package com.prempoddar.campfire.client

import com.prempoddar.campfire.core.Attachment
import com.prempoddar.campfire.core.ClientCommand
import com.prempoddar.campfire.core.Conv
import com.prempoddar.campfire.core.MemberInfo
import com.prempoddar.campfire.core.Message
import com.prempoddar.campfire.core.RoomInfo
import com.prempoddar.campfire.core.ServerEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

const val PUBLIC_CONV = "room:public"

/** One conversation as this client knows it. */
data class ConvState(
    val messages: List<Message> = emptyList(),
    /** Full history has arrived (not just the last message from the welcome). */
    val loaded: Boolean = false,
    /** Member id → the last message id they've seen. */
    val receipts: Map<String, Long> = emptyMap(),
    val unread: Int = 0,
)

data class ChatUi(
    val status: Status = Status.CONNECTING,
    val serverName: String = "Campfire",
    val me: MemberInfo? = null,
    val token: String? = null,
    /** People here now. */
    val members: Map<String, MemberInfo> = emptyMap(),
    /** Anyone ever seen, so old messages keep their names. */
    val known: Map<String, MemberInfo> = emptyMap(),
    val rooms: List<RoomInfo> = emptyList(),
    val convs: Map<String, ConvState> = emptyMap(),
    val active: String = PUBLIC_CONV,
    /** Conversation → member id → when their "typing" expires. */
    val typing: Map<String, Map<String, Long>> = emptyMap(),
    /** Client ids of messages sent but not yet confirmed. */
    val pending: Set<String> = emptySet(),
    /** One-off messages for the person ("You were removed from Games"). */
    val notice: String? = null,
    /** Set when a room's password was wrong, for the password dialog. */
    val passwordError: String? = null,
) {
    enum class Status { CONNECTING, READY, RECONNECTING, ENDED, REMOVED }

    fun person(id: String): MemberInfo = members[id] ?: known[id] ?: MemberInfo(id, "Someone", 9, isHost = false, online = false)
    fun room(conv: String): RoomInfo? = Conv.roomId(conv)?.let { id -> rooms.firstOrNull { it.id == id } }
    fun dmOther(conv: String): String? = Conv.dmMembers(conv)?.let { (a, b) -> if (a == me?.id) b else a }

    fun title(conv: String): String = room(conv)?.name ?: dmOther(conv)?.let { person(it).name } ?: ""

    /** Room creators and the host manage rooms; nobody manages Public. */
    fun isAdmin(room: RoomInfo?): Boolean = room != null && room.id != "public" && (me?.isHost == true || room.creatorId == me?.id)

    /** Who is typing in [conv] right now, by name. */
    fun typingIn(conv: String, now: Long): List<String> =
        typing[conv].orEmpty().filter { (id, until) -> until > now && id != me?.id }.keys.map { person(it).name }

    val totalUnread: Int get() = convs.values.sumOf { it.unread }
}

/**
 * The host's chat, as plain state: server events go in, commands come out. It mirrors the browser
 * client exactly, so everyone at the fire sees the same thing. [send] returns false while offline;
 * commands are then queued (except typing and read marks) and sent after reconnecting.
 */
class ChatClient(private val now: () -> Long = System::currentTimeMillis) {
    private val _state = MutableStateFlow(ChatUi())
    val state: StateFlow<ChatUi> = _state.asStateFlow()

    var send: (ClientCommand) -> Boolean = { false }
    /** Whether the chat is on screen; unread counts and read marks depend on it. */
    var visible: Boolean = false
        set(v) { field = v; if (v) markRead() }

    private val queue = ArrayList<ClientCommand>()
    private val lastRead = HashMap<String, Long>()

    private fun command(c: ClientCommand) {
        if (_state.value.status == ChatUi.Status.READY && send(c)) return
        if (c !is ClientCommand.Typing && c !is ClientCommand.Read) synchronized(queue) { queue += c }
    }

    private fun withConv(s: ChatUi, conv: String, change: (ConvState) -> ConvState) =
        s.copy(convs = s.convs + (conv to change(s.convs[conv] ?: ConvState())))

    private fun dropConv(s: ChatUi, conv: String) = s.copy(convs = s.convs - conv, active = if (s.active == conv) PUBLIC_CONV else s.active)

    fun disconnected() {
        _state.update { if (it.status == ChatUi.Status.ENDED || it.status == ChatUi.Status.REMOVED) it else it.copy(status = ChatUi.Status.RECONNECTING) }
    }

    fun onEvent(ev: ServerEvent) {
        when (ev) {
            is ServerEvent.Welcome -> {
                _state.update { s ->
                    val convs = ev.convs.associate { c ->
                        val old = s.convs[c.conv]
                        c.conv to ConvState(old?.messages ?: listOfNotNull(c.last), loaded = false, receipts = old?.receipts.orEmpty(), unread = c.unread)
                    }
                    val members = ev.members.associateBy { it.id }
                    s.copy(
                        status = ChatUi.Status.READY, me = ev.you, token = ev.token, serverName = ev.serverName, rooms = ev.rooms,
                        members = members, known = s.known + members, convs = convs,
                        active = if (s.active in convs) s.active else PUBLIC_CONV,
                    )
                }
                send(ClientCommand.History(_state.value.active))
                val pending = synchronized(queue) { queue.toList().also { queue.clear() } }
                pending.forEach { send(it) }
            }
            is ServerEvent.Rooms -> _state.update { it.copy(rooms = ev.rooms) }
            is ServerEvent.Members -> _state.update { s ->
                val members = ev.members.associateBy { it.id }
                s.copy(members = members, known = s.known + members, me = s.me?.let { members[it.id] ?: it })
            }
            is ServerEvent.History -> {
                _state.update { s -> withConv(s, ev.conv) { ConvState(ev.messages, loaded = true, receipts = ev.receipts.associate { it.memberId to it.upTo }, unread = it.unread) } }
                if (ev.conv == _state.value.active) markRead()
            }
            is ServerEvent.NewMessage -> {
                val m = ev.message
                _state.update { s ->
                    val onScreen = s.active == m.conv && visible
                    val counts = m.from != s.me?.id && !onScreen
                    val typing = s.typing + (m.conv to (s.typing[m.conv].orEmpty() - m.from))
                    withConv(s.copy(pending = s.pending - (ev.clientId ?: ""), typing = typing), m.conv) { c ->
                        c.copy(
                            messages = if (c.messages.any { it.id == m.id }) c.messages else c.messages + m,
                            unread = if (counts) c.unread + 1 else c.unread,
                            receipts = if (m.from == s.me?.id) c.receipts + (m.from to m.id) else c.receipts,
                        )
                    }
                }
                if (m.conv == _state.value.active) markRead()
            }
            is ServerEvent.MessageUpdated -> _state.update { s ->
                withConv(s, ev.message.conv) { c -> c.copy(messages = c.messages.map { if (it.id == ev.message.id) ev.message else it }) }
            }
            is ServerEvent.ReceiptUpdate -> _state.update { s -> withConv(s, ev.conv) { c -> c.copy(receipts = c.receipts + (ev.memberId to ev.upTo)) } }
            is ServerEvent.Typing -> _state.update { s ->
                s.copy(typing = s.typing + (ev.conv to (s.typing[ev.conv].orEmpty() + (ev.memberId to now() + TYPING_MS))))
            }
            is ServerEvent.Opened -> {
                _state.update { s -> withConv(s, ev.conv) { it.copy(unread = 0) }.copy(active = ev.conv, passwordError = null) }
                send(ClientCommand.History(ev.conv))
            }
            is ServerEvent.RoomClosed -> _state.update { dropConv(it, Conv.room(ev.roomId)).copy(notice = "${ev.name} was closed") }
            is ServerEvent.Removed -> _state.update { s ->
                if (ev.roomId == null) s.copy(status = ChatUi.Status.REMOVED)
                else dropConv(s, Conv.room(ev.roomId)).copy(notice = "You were removed from ${s.rooms.firstOrNull { it.id == ev.roomId }?.name ?: "the room"}")
            }
            is ServerEvent.Error -> _state.update { s ->
                when (ev.code) {
                    "wrong_password" -> s.copy(passwordError = ev.message)
                    "banned" -> s.copy(status = ChatUi.Status.REMOVED)
                    else -> s.copy(notice = ev.message)
                }
            }
            is ServerEvent.Ended -> _state.update { it.copy(status = ChatUi.Status.ENDED) }
        }
    }

    fun noticeShown() = _state.update { it.copy(notice = null) }

    /** Tells the server the open conversation has been read, when it's actually on screen. */
    fun markRead() {
        val s = _state.value
        val c = s.convs[s.active] ?: return
        if (!visible) return
        val last = c.messages.lastOrNull() ?: return
        if (c.unread > 0) _state.update { withConv(it, it.active) { cs -> cs.copy(unread = 0) } }
        if ((c.receipts[s.me?.id] ?: 0) >= last.id || (lastRead[s.active] ?: 0) >= last.id) return
        lastRead[s.active] = last.id
        command(ClientCommand.Read(s.active, last.id))
    }

    fun open(conv: String) {
        _state.update { s -> withConv(s, conv) { it.copy(unread = 0) }.copy(active = conv) }
        command(ClientCommand.History(conv))
    }

    /** Sends a message; it shows as pending until the server confirms it. */
    fun sendMessage(text: String, replyTo: Long? = null, attachment: Attachment? = null, conv: String = _state.value.active) {
        if (text.isBlank() && attachment == null) return
        val id = UUID.randomUUID().toString()
        _state.update { it.copy(pending = it.pending + id) }
        command(ClientCommand.Send(conv, text.trim(), replyTo, attachment, id))
    }

    fun edit(msgId: Long, text: String) = command(ClientCommand.Edit(msgId, text.trim()))
    fun delete(msgId: Long) = command(ClientCommand.Delete(msgId))
    fun react(msgId: Long, emoji: String) = command(ClientCommand.React(msgId, emoji))
    fun typing() = command(ClientCommand.Typing(_state.value.active))
    fun joinRoom(roomId: String, password: String? = null) = command(ClientCommand.JoinRoom(roomId, password))
    fun createRoom(name: String, password: String?) = command(ClientCommand.CreateRoom(name.trim(), password?.takeIf { it.isNotEmpty() }))
    fun leaveRoom(roomId: String) = command(ClientCommand.LeaveRoom(roomId))
    fun closeRoom(roomId: String) = command(ClientCommand.CloseRoom(roomId))
    fun removeMember(roomId: String, memberId: String) = command(ClientCommand.RemoveMember(roomId, memberId))
    fun ban(memberId: String) = command(ClientCommand.Ban(memberId))
    fun openDm(memberId: String) = command(ClientCommand.OpenDm(memberId))

    companion object {
        const val TYPING_MS = 3_500L
        /** Member colours, the same as the browser's. */
        val COLORS = listOf(0xE5484D, 0xF76B15, 0xC99400, 0x3E9B4F, 0x12A594, 0x0090FF, 0x3E63DD, 0x8E4EC6, 0xD6409F, 0x7C7C74)
    }
}
