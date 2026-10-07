package com.prempoddar.campfire.core

/**
 * Every chat rule lives here. Each call returns the events to send and who gets them; it never
 * does I/O, so the server can run it from one coroutine and tests can call it directly.
 */
class ChatState(
    private val ids: IdSource = RandomIds(),
    private val historyCap: Int = 5_000,
) {
    data class Delivery(val to: Set<String>, val event: ServerEvent)

    data class Snapshot(val rooms: List<RoomInfo>, val members: List<MemberInfo>)

    private val members = LinkedHashMap<String, Member>()
    private val removed = mutableSetOf<String>()
    private val online = mutableSetOf<String>()
    private val rooms = LinkedHashMap<String, Room>()
    private val messages = HashMap<String, ArrayDeque<Message>>()
    private val convOf = HashMap<Long, String>()
    private val reads = HashMap<String, MutableMap<String, Long>>()
    private val dms = mutableSetOf<String>()
    private var nextMessageId = 1L

    init {
        rooms[PUBLIC_ROOM] = Room(PUBLIC_ROOM, "Public", creatorId = null, passwordHash = null, members = emptySet())
    }

    // Reading

    fun room(id: String): Room? = rooms[id]
    fun rooms(): List<Room> = rooms.values.toList()
    fun message(id: Long): Message? = convOf[id]?.let { conv -> messages[conv]?.firstOrNull { it.id == id } }
    fun member(id: String): Member? = members[id]?.takeIf { id !in removed }
    fun snapshot() = Snapshot(roomInfos(), memberInfos())

    fun welcome(memberId: String, serverName: String): ServerEvent.Welcome {
        val me = members.getValue(memberId)
        val convs = rooms.values.filter { memberId in it.members }.map { Conv.room(it.id) } +
            dms.filter { conv -> Conv.dmMembers(conv)?.toList()?.contains(memberId) == true }
        return ServerEvent.Welcome(
            you = info(me),
            token = me.token,
            serverName = serverName,
            rooms = roomInfos(),
            members = memberInfos(),
            convs = convs.map { summary(memberId, it) },
        )
    }

    // People

    /** Adds someone. Null when the name is unusable. */
    fun join(name: String, color: Int, isHost: Boolean): Pair<Member, List<Delivery>>? {
        val clean = Names.clean(name, 32) ?: return null
        val taken = members.values.filter { it.id !in removed }.map { it.name }.toSet()
        val member = Member(
            id = ids.memberId(),
            name = Names.dedupe(clean, taken),
            color = color.coerceIn(0, COLOR_COUNT - 1),
            isHost = isHost,
            token = ids.token(),
        )
        members[member.id] = member
        updateRoom(PUBLIC_ROOM) { it.copy(members = it.members + member.id) }
        return member to listOf(toAll(ServerEvent.Members(memberInfos())), toAll(ServerEvent.Rooms(roomInfos())))
    }

    /**
     * Gives an offline person their seat back when they return without their token (they left,
     * or opened a fresh tab): same id, history and rooms; the name is updated if they typed a new one.
     */
    fun reclaim(memberId: String, name: String): Pair<Member, List<Delivery>>? {
        val m = member(memberId) ?: return null
        if (m.isHost || memberId in online) return null
        val clean = Names.clean(name, 32) ?: return null
        if (clean == m.name) return m to emptyList()
        val taken = members.values.filter { it.id !in removed && it.id != memberId }.map { it.name }.toSet()
        val renamed = m.copy(name = Names.dedupe(clean, taken))
        members[memberId] = renamed
        return renamed to listOf(toAll(ServerEvent.Members(memberInfos())))
    }

    fun resume(token: String): Member? = members.values.firstOrNull { it.token == token && it.id !in removed }

    fun setOnline(memberId: String, isOnline: Boolean): List<Delivery> {
        if (member(memberId) == null) return emptyList()
        val changed = if (isOnline) online.add(memberId) else online.remove(memberId)
        return if (changed) listOf(toAll(ServerEvent.Members(memberInfos()))) else emptyList()
    }

    fun removeFromServer(memberId: String, by: String? = null): List<Delivery> {
        if (member(memberId) == null) return emptyList()
        removed += memberId
        online -= memberId
        rooms.keys.toList().forEach { id -> updateRoom(id) { it.copy(members = it.members - memberId) } }
        return listOf(
            Delivery(setOf(memberId), ServerEvent.Removed(roomId = null, by = by)),
            toAll(ServerEvent.Members(memberInfos())),
            toAll(ServerEvent.Rooms(roomInfos())),
        )
    }

    // Commands

    fun handle(memberId: String, cmd: ClientCommand, now: Long): List<Delivery> {
        val me = member(memberId) ?: return emptyList()
        return when (cmd) {
            is ClientCommand.Hello -> emptyList()
            is ClientCommand.JoinRoom -> joinRoom(me, cmd)
            is ClientCommand.LeaveRoom -> leaveRoom(me, cmd.roomId)
            is ClientCommand.CreateRoom -> createRoom(me, cmd, now)
            is ClientCommand.UpdateRoom -> updateRoomSettings(me, cmd)
            is ClientCommand.CloseRoom -> closeRoom(me, cmd.roomId)
            is ClientCommand.RemoveMember -> removeMember(me, cmd)
            is ClientCommand.Ban -> ban(me, cmd.memberId)
            is ClientCommand.OpenDm -> openDm(me, cmd.memberId)
            is ClientCommand.History -> history(me, cmd.conv)
            is ClientCommand.Send -> send(me, cmd, now)
            is ClientCommand.Edit -> edit(me, cmd, now)
            is ClientCommand.Delete -> delete(me, cmd.msgId)
            is ClientCommand.React -> react(me, cmd)
            is ClientCommand.Read -> read(me, cmd)
            is ClientCommand.Typing -> typing(me, cmd.conv)
        }
    }

    fun applyPreview(msgId: Long, preview: LinkPreview): List<Delivery> {
        val msg = message(msgId) ?: return emptyList()
        if (msg.deleted || Links.firstUrl(msg.text) != preview.url) return emptyList()
        return listOf(publish(msg.copy(preview = preview)))
    }

    // Rooms

    private fun joinRoom(me: Member, cmd: ClientCommand.JoinRoom): List<Delivery> {
        val room = rooms[cmd.roomId] ?: return error(me, "not_found", "That room no longer exists.")
        val conv = Conv.room(room.id)
        if (me.id in room.members) return listOf(Delivery(setOf(me.id), ServerEvent.Opened(conv)), historyFor(me, conv))
        if (me.id in room.kicked) return error(me, "removed", "You were removed from ${room.name}.")
        val hash = room.passwordHash
        if (hash != null && !isAdmin(me, room) && !Passwords.verify(cmd.password.orEmpty(), hash)) {
            return error(me, "wrong_password", "That password isn't right.")
        }
        updateRoom(room.id) { it.copy(members = it.members + me.id) }
        return listOf(
            toAll(ServerEvent.Rooms(roomInfos())),
            Delivery(setOf(me.id), ServerEvent.Opened(conv)),
            historyFor(me, conv),
        )
    }

    private fun leaveRoom(me: Member, roomId: String): List<Delivery> {
        if (roomId == PUBLIC_ROOM) return error(me, "not_allowed", "Everyone stays in Public.")
        val room = rooms[roomId] ?: return error(me, "not_found", "That room no longer exists.")
        if (me.id !in room.members) return emptyList()
        updateRoom(roomId) { it.copy(members = it.members - me.id) }
        return listOf(toAll(ServerEvent.Rooms(roomInfos())))
    }

    private fun createRoom(me: Member, cmd: ClientCommand.CreateRoom, now: Long): List<Delivery> {
        val name = Names.clean(cmd.name, 40) ?: return error(me, "bad_name", "Give the room a name of up to 40 characters.")
        val password = cmd.password?.takeIf { it.isNotBlank() }
        val room = Room(
            id = ids.roomId(),
            name = Names.dedupe(name, rooms.values.map { it.name }.toSet()),
            creatorId = me.id,
            passwordHash = password?.let(Passwords::hash),
            members = setOf(me.id),
            createdAt = now,
        )
        rooms[room.id] = room
        val conv = Conv.room(room.id)
        return listOf(
            toAll(ServerEvent.Rooms(roomInfos())),
            Delivery(setOf(me.id), ServerEvent.Opened(conv)),
            historyFor(me, conv),
        )
    }

    private fun updateRoomSettings(me: Member, cmd: ClientCommand.UpdateRoom): List<Delivery> {
        val room = rooms[cmd.roomId] ?: return error(me, "not_found", "That room no longer exists.")
        if (room.id == PUBLIC_ROOM || !isAdmin(me, room)) return notAllowed(me)
        val name = cmd.name?.let {
            val clean = Names.clean(it, 40) ?: return error(me, "bad_name", "Give the room a name of up to 40 characters.")
            if (clean == room.name) clean else Names.dedupe(clean, rooms.values.filter { r -> r.id != room.id }.map { r -> r.name }.toSet())
        } ?: room.name
        val hash = when {
            cmd.clearPassword -> null
            !cmd.password.isNullOrBlank() -> Passwords.hash(cmd.password)
            else -> room.passwordHash
        }
        updateRoom(room.id) { it.copy(name = name, passwordHash = hash) }
        return listOf(toAll(ServerEvent.Rooms(roomInfos())))
    }

    private fun closeRoom(me: Member, roomId: String): List<Delivery> {
        val room = rooms[roomId] ?: return error(me, "not_found", "That room no longer exists.")
        if (room.id == PUBLIC_ROOM || !isAdmin(me, room)) return notAllowed(me)
        return close(room)
    }

    /** Closes a room on behalf of the host app, which may not have joined the chat itself. */
    fun adminCloseRoom(roomId: String): List<Delivery> {
        val room = rooms[roomId]?.takeIf { it.id != PUBLIC_ROOM } ?: return emptyList()
        return close(room)
    }

    private fun close(room: Room): List<Delivery> {
        rooms.remove(room.id)
        val conv = Conv.room(room.id)
        messages.remove(conv)?.forEach { convOf.remove(it.id) }
        reads.remove(conv)
        return listOf(
            Delivery(room.members, ServerEvent.RoomClosed(room.id, room.name)),
            toAll(ServerEvent.Rooms(roomInfos())),
        )
    }

    private fun removeMember(me: Member, cmd: ClientCommand.RemoveMember): List<Delivery> {
        val room = rooms[cmd.roomId] ?: return error(me, "not_found", "That room no longer exists.")
        val target = member(cmd.memberId) ?: return error(me, "not_found", "That person has left.")
        if (room.id == PUBLIC_ROOM || !isAdmin(me, room) || target.id == me.id || target.isHost) return notAllowed(me)
        updateRoom(room.id) { it.copy(members = it.members - target.id, kicked = it.kicked + target.id) }
        return listOf(
            Delivery(setOf(target.id), ServerEvent.Removed(room.id, me.id)),
            toAll(ServerEvent.Rooms(roomInfos())),
        )
    }

    private fun ban(me: Member, targetId: String): List<Delivery> {
        val target = member(targetId) ?: return error(me, "not_found", "That person has left.")
        if (!me.isHost || target.isHost) return notAllowed(me)
        return removeFromServer(target.id, by = me.id)
    }

    // Conversations

    private fun openDm(me: Member, otherId: String): List<Delivery> {
        val other = member(otherId)
        if (other == null || other.id == me.id) return error(me, "not_found", "That person has left.")
        val conv = Conv.dm(me.id, other.id)
        dms += conv
        return listOf(Delivery(setOf(me.id), ServerEvent.Opened(conv)), historyFor(me, conv))
    }

    private fun history(me: Member, conv: String): List<Delivery> {
        if (me.id !in participants(conv).orEmpty()) return notMember(me)
        return listOf(historyFor(me, conv))
    }

    private fun send(me: Member, cmd: ClientCommand.Send, now: Long): List<Delivery> {
        val people = participants(cmd.conv)
        if (people == null || me.id !in people) return notMember(me)
        if (cmd.text.isBlank() && cmd.attachment == null) return error(me, "empty", "Type a message first.")
        if (cmd.text.length > MAX_TEXT) return error(me, "too_long", "Messages can be up to $MAX_TEXT characters.")
        val replyTo = cmd.replyTo?.takeIf { convOf[it] == cmd.conv }
        val msg = Message(
            id = nextMessageId++,
            conv = cmd.conv,
            from = me.id,
            text = cmd.text.trimEnd(),
            at = now,
            replyTo = replyTo,
            attachment = cmd.attachment,
        )
        val list = messages.getOrPut(cmd.conv) { ArrayDeque() }
        list.addLast(msg)
        convOf[msg.id] = cmd.conv
        while (list.size > historyCap) convOf.remove(list.removeFirst().id)
        if (cmd.conv.startsWith("dm:")) dms += cmd.conv
        reads.getOrPut(cmd.conv) { mutableMapOf() }[me.id] = msg.id
        return listOf(Delivery(people, ServerEvent.NewMessage(msg, cmd.clientId)))
    }

    private fun edit(me: Member, cmd: ClientCommand.Edit, now: Long): List<Delivery> {
        val msg = message(cmd.msgId)?.takeIf { !it.deleted } ?: return error(me, "not_found", "That message is gone.")
        if (msg.from != me.id) return notAllowed(me)
        if (cmd.text.isBlank() && msg.attachment == null) return error(me, "empty", "Type a message first.")
        if (cmd.text.length > MAX_TEXT) return error(me, "too_long", "Messages can be up to $MAX_TEXT characters.")
        val text = cmd.text.trimEnd()
        val preview = msg.preview?.takeIf { Links.firstUrl(text) == it.url }
        return listOf(publish(msg.copy(text = text, editedAt = now, preview = preview)))
    }

    private fun delete(me: Member, msgId: Long): List<Delivery> {
        val msg = message(msgId)?.takeIf { !it.deleted } ?: return error(me, "not_found", "That message is gone.")
        val room = Conv.roomId(msg.conv)?.let(rooms::get)
        val allowed = msg.from == me.id || me.isHost || (room != null && isAdmin(me, room))
        if (!allowed) return notAllowed(me)
        return listOf(
            publish(msg.copy(text = "", deleted = true, attachment = null, reactions = emptyMap(), preview = null)),
        )
    }

    private fun react(me: Member, cmd: ClientCommand.React): List<Delivery> {
        val msg = message(cmd.msgId)?.takeIf { !it.deleted } ?: return error(me, "not_found", "That message is gone.")
        if (me.id !in participants(msg.conv).orEmpty()) return notMember(me)
        val emoji = cmd.emoji.trim()
        if (emoji.isEmpty() || emoji.length > 16) return error(me, "bad_emoji", "That reaction can't be used.")
        val current = msg.reactions[emoji].orEmpty()
        val next = if (me.id in current) current - me.id else current + me.id
        if (next.isNotEmpty() && emoji !in msg.reactions && msg.reactions.size >= MAX_REACTIONS) {
            return error(me, "too_many", "This message has all the reactions it can hold.")
        }
        val reactions = (msg.reactions + (emoji to next)).filterValues { it.isNotEmpty() }
        return listOf(publish(msg.copy(reactions = reactions)))
    }

    private fun read(me: Member, cmd: ClientCommand.Read): List<Delivery> {
        val people = participants(cmd.conv)
        if (people == null || me.id !in people) return emptyList()
        val latest = messages[cmd.conv]?.lastOrNull()?.id ?: return emptyList()
        val upTo = cmd.upTo.coerceAtMost(latest)
        val convReads = reads.getOrPut(cmd.conv) { mutableMapOf() }
        if (upTo <= (convReads[me.id] ?: 0)) return emptyList()
        convReads[me.id] = upTo
        return listOf(Delivery(people, ServerEvent.ReceiptUpdate(cmd.conv, me.id, upTo)))
    }

    private fun typing(me: Member, conv: String): List<Delivery> {
        val people = participants(conv)
        if (people == null || me.id !in people) return emptyList()
        return listOf(Delivery(people - me.id, ServerEvent.Typing(conv, me.id)))
    }

    // Helpers

    /** Who is in a conversation, or null if it doesn't exist. */
    private fun participants(conv: String): Set<String>? {
        Conv.roomId(conv)?.let { return rooms[it]?.members }
        val (a, b) = Conv.dmMembers(conv) ?: return null
        if (member(a) == null || member(b) == null) return null
        return setOf(a, b)
    }

    private fun publish(msg: Message): Delivery {
        val list = messages.getValue(msg.conv)
        val i = list.indexOfFirst { it.id == msg.id }
        list[i] = msg
        return Delivery(participants(msg.conv).orEmpty(), ServerEvent.MessageUpdated(msg))
    }

    private fun historyFor(me: Member, conv: String) = Delivery(
        setOf(me.id),
        ServerEvent.History(
            conv,
            messages[conv]?.toList().orEmpty(),
            reads[conv].orEmpty().map { (id, upTo) -> Receipt(id, upTo) },
        ),
    )

    private fun summary(memberId: String, conv: String): ConvSummary {
        val list = messages[conv].orEmpty()
        val myRead = reads[conv]?.get(memberId) ?: 0
        return ConvSummary(
            conv = conv,
            last = list.lastOrNull(),
            unread = list.count { it.id > myRead && it.from != memberId && !it.deleted },
        )
    }

    private fun isAdmin(me: Member, room: Room) = me.isHost || room.creatorId == me.id

    private fun updateRoom(id: String, change: (Room) -> Room) {
        rooms[id]?.let { rooms[id] = change(it) }
    }

    private fun info(m: Member) = MemberInfo(m.id, m.name, m.color, m.isHost, m.id in online)
    private fun memberInfos() = members.values.filter { it.id !in removed }.map(::info)
    private fun roomInfos() = rooms.values.map { RoomInfo(it.id, it.name, it.passwordHash != null, it.creatorId, it.members.toList()) }
    private fun toAll(event: ServerEvent) = Delivery(members.keys - removed, event)
    private fun error(me: Member, code: String, message: String) = listOf(Delivery(setOf(me.id), ServerEvent.Error(code, message)))
    private fun notAllowed(me: Member) = error(me, "not_allowed", "Only the room's creator or the host can do that.")
    private fun notMember(me: Member) = error(me, "not_member", "Join the conversation first.")

    companion object {
        const val MAX_TEXT = 4_000
        const val MAX_REACTIONS = 20
    }
}
