package com.prempoddar.campfire.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A person as other people see them. */
@Serializable
data class MemberInfo(
    val id: String,
    val name: String,
    val color: Int,
    val isHost: Boolean,
    val online: Boolean,
    /** Muted by the host until this time; they can read but not send. */
    val mutedUntil: Long? = null,
)

@Serializable
data class RoomInfo(
    val id: String,
    val name: String,
    val locked: Boolean,
    val creatorId: String?,
    val members: List<String>,
)

@Serializable
data class Receipt(val memberId: String, val upTo: Long)

/** A poll as its author writes it, before anyone has voted. */
@Serializable
data class PollDraft(val question: String, val options: List<String>)

/** A conversation the member is part of, for the sidebar after (re)connecting. */
@Serializable
data class ConvSummary(
    val conv: String,
    val last: Message?,
    val unread: Int,
)

/** Commands a browser sends. */
@Serializable
sealed interface ClientCommand {
    @Serializable @SerialName("hello")
    data class Hello(
        val token: String? = null,
        val name: String? = null,
        val color: Int = 0,
        val hostToken: String? = null,
    ) : ClientCommand

    @Serializable @SerialName("join_room")
    data class JoinRoom(val roomId: String, val password: String? = null) : ClientCommand

    @Serializable @SerialName("leave_room")
    data class LeaveRoom(val roomId: String) : ClientCommand

    @Serializable @SerialName("create_room")
    data class CreateRoom(val name: String, val password: String? = null) : ClientCommand

    @Serializable @SerialName("update_room")
    data class UpdateRoom(
        val roomId: String,
        val name: String? = null,
        val password: String? = null,
        val clearPassword: Boolean = false,
    ) : ClientCommand

    @Serializable @SerialName("close_room")
    data class CloseRoom(val roomId: String) : ClientCommand

    @Serializable @SerialName("remove_member")
    data class RemoveMember(val roomId: String, val memberId: String) : ClientCommand

    /** Host only: remove someone from the whole Campfire. */
    @Serializable @SerialName("ban")
    data class Ban(val memberId: String) : ClientCommand

    @Serializable @SerialName("open_dm")
    data class OpenDm(val memberId: String) : ClientCommand

    @Serializable @SerialName("history")
    data class History(val conv: String) : ClientCommand

    @Serializable @SerialName("send")
    data class Send(
        val conv: String,
        val text: String = "",
        val replyTo: Long? = null,
        val attachment: Attachment? = null,
        /** Makes this message a poll: [text] is ignored and the question is used. */
        val poll: PollDraft? = null,
        /** Echoed back so the sender can match its pending message. */
        val clientId: String? = null,
    ) : ClientCommand

    @Serializable @SerialName("pin")
    data class Pin(val msgId: Long, val pinned: Boolean) : ClientCommand

    @Serializable @SerialName("vote")
    data class Vote(val msgId: Long, val option: Int) : ClientCommand

    /** Host only: [minutes] 0 lifts it. */
    @Serializable @SerialName("mute")
    data class Mute(val memberId: String, val minutes: Int) : ClientCommand

    @Serializable @SerialName("edit")
    data class Edit(val msgId: Long, val text: String) : ClientCommand

    @Serializable @SerialName("delete")
    data class Delete(val msgId: Long) : ClientCommand

    @Serializable @SerialName("react")
    data class React(val msgId: Long, val emoji: String) : ClientCommand

    @Serializable @SerialName("read")
    data class Read(val conv: String, val upTo: Long) : ClientCommand

    @Serializable @SerialName("typing")
    data class Typing(val conv: String) : ClientCommand
}

/** Events the server sends. */
@Serializable
sealed interface ServerEvent {
    @Serializable @SerialName("welcome")
    data class Welcome(
        val you: MemberInfo,
        val token: String,
        val serverName: String,
        val rooms: List<RoomInfo>,
        val members: List<MemberInfo>,
        val convs: List<ConvSummary>,
    ) : ServerEvent

    @Serializable @SerialName("rooms")
    data class Rooms(val rooms: List<RoomInfo>) : ServerEvent

    @Serializable @SerialName("members")
    data class Members(val members: List<MemberInfo>) : ServerEvent

    @Serializable @SerialName("history")
    data class History(val conv: String, val messages: List<Message>, val receipts: List<Receipt>) : ServerEvent

    @Serializable @SerialName("message")
    data class NewMessage(val message: Message, val clientId: String? = null) : ServerEvent

    @Serializable @SerialName("message_updated")
    data class MessageUpdated(val message: Message) : ServerEvent

    @Serializable @SerialName("receipt")
    data class ReceiptUpdate(val conv: String, val memberId: String, val upTo: Long) : ServerEvent

    @Serializable @SerialName("typing")
    data class Typing(val conv: String, val memberId: String) : ServerEvent

    /** Sent to the member who joined or created a room, or opened a DM: switch to it. */
    @Serializable @SerialName("opened")
    data class Opened(val conv: String) : ServerEvent

    @Serializable @SerialName("room_closed")
    data class RoomClosed(val roomId: String, val name: String) : ServerEvent

    /** [roomId] null means removed from the whole Campfire. */
    @Serializable @SerialName("removed")
    data class Removed(val roomId: String?, val by: String?) : ServerEvent

    @Serializable @SerialName("error")
    data class Error(val code: String, val message: String) : ServerEvent

    @Serializable @SerialName("ended")
    data class Ended(val serverName: String) : ServerEvent
}

object Protocol {
    val json = Json {
        classDiscriminator = "type"
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    fun encode(event: ServerEvent): String = json.encodeToString(ServerEvent.serializer(), event)

    fun decodeCommand(text: String): ClientCommand? =
        runCatching { json.decodeFromString(ClientCommand.serializer(), text) }.getOrNull()

    // The other direction, for the host's own chat in the app.
    fun encode(command: ClientCommand): String = json.encodeToString(ClientCommand.serializer(), command)

    fun decodeEvent(text: String): ServerEvent? =
        runCatching { json.decodeFromString(ServerEvent.serializer(), text) }.getOrNull()
}
