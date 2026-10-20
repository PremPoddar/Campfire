package com.prempoddar.campfire.core

import kotlinx.serialization.Serializable

/** Someone connected to this Campfire. [token] is their secret and is never sent to others. */
data class Member(
    val id: String,
    val name: String,
    val color: Int,
    val isHost: Boolean,
    val token: String,
)

/** A chat room. Public is a room like any other, except that nobody can close or lock it. */
data class Room(
    val id: String,
    val name: String,
    val creatorId: String?,
    val passwordHash: String?,
    val members: Set<String>,
    /** Removed from this room for the rest of the session. */
    val kicked: Set<String> = emptySet(),
    val createdAt: Long = 0,
)

@Serializable
data class Attachment(
    val id: String,
    val name: String,
    val size: Long,
    val mime: String,
)

@Serializable
data class LinkPreview(
    val url: String,
    val title: String? = null,
    val description: String? = null,
    /** A file id served by this Campfire, so clients without internet still see the image. */
    val imageId: String? = null,
    val site: String? = null,
)

@Serializable
data class Message(
    val id: Long,
    val conv: String,
    val from: String,
    val text: String,
    val at: Long,
    val editedAt: Long? = null,
    val deleted: Boolean = false,
    val replyTo: Long? = null,
    val attachment: Attachment? = null,
    /** Emoji → ids of the members who reacted with it. */
    val reactions: Map<String, Set<String>> = emptyMap(),
    val preview: LinkPreview? = null,
    /** Kept at the top of the conversation by its admins. */
    val pinned: Boolean = false,
    val poll: Poll? = null,
)

/** A question with choices; each person has one vote, which they can change or take back. */
@Serializable
data class Poll(val question: String, val options: List<String>, val votes: Map<Int, Set<String>> = emptyMap()) {
    fun voteOf(memberId: String): Int? = votes.entries.firstOrNull { memberId in it.value }?.key
}

const val PUBLIC_ROOM = "public"
const val COLOR_COUNT = 10

/** Conversation ids: `room:<roomId>` or `dm:<a>:<b>` with the two member ids sorted. */
object Conv {
    fun room(roomId: String) = "room:$roomId"
    fun dm(a: String, b: String): String = if (a < b) "dm:$a:$b" else "dm:$b:$a"
    fun roomId(conv: String): String? = conv.removePrefix("room:").takeIf { conv.startsWith("room:") }
    fun dmMembers(conv: String): Pair<String, String>? {
        if (!conv.startsWith("dm:")) return null
        val parts = conv.split(':')
        return if (parts.size == 3) parts[1] to parts[2] else null
    }
}
