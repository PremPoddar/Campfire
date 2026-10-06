package com.prempoddar.campfire.client

import com.google.common.truth.Truth.assertThat
import com.prempoddar.campfire.core.ClientCommand
import com.prempoddar.campfire.core.ConvSummary
import com.prempoddar.campfire.core.MemberInfo
import com.prempoddar.campfire.core.Message
import com.prempoddar.campfire.core.RoomInfo
import com.prempoddar.campfire.core.ServerEvent
import org.junit.Test

class ChatClientTest {
    private val me = MemberInfo("me", "Prem", 0, isHost = true, online = true)
    private val ben = MemberInfo("ben", "Ben", 3, isHost = false, online = true)
    private val sent = ArrayList<ClientCommand>()
    private var online = true
    private var clock = 1_000L
    private val client = ChatClient { clock }.also { it.send = { c -> if (online) sent += c; online } }

    private fun welcome() = client.onEvent(
        ServerEvent.Welcome(
            me, "tok", "Camp", listOf(RoomInfo("public", "Public", false, null, listOf("me", "ben")), RoomInfo("games", "Games", true, "ben", listOf("me", "ben"))),
            listOf(me, ben), listOf(ConvSummary(PUBLIC_CONV, null, 0)),
        ),
    )

    private fun msg(id: Long, from: String, conv: String = PUBLIC_CONV, text: String = "hi") = Message(id, conv, from, text, at = id)

    @Test fun welcomeAsksForTheOpenConversation() {
        welcome()
        assertThat(client.state.value.status).isEqualTo(ChatUi.Status.READY)
        assertThat(sent).contains(ClientCommand.History(PUBLIC_CONV))
        assertThat(client.state.value.title("room:games")).isEqualTo("Games")
    }

    @Test fun unreadCountsOnlyWhenNotLooking() {
        welcome()
        client.onEvent(ServerEvent.NewMessage(msg(1, "ben")))
        assertThat(client.state.value.convs.getValue(PUBLIC_CONV).unread).isEqualTo(1)
        client.visible = true
        assertThat(client.state.value.convs.getValue(PUBLIC_CONV).unread).isEqualTo(0)
        assertThat(sent.last()).isEqualTo(ClientCommand.Read(PUBLIC_CONV, 1))
        client.onEvent(ServerEvent.NewMessage(msg(2, "ben")))
        assertThat(client.state.value.convs.getValue(PUBLIC_CONV).unread).isEqualTo(0)
        // Messages elsewhere still count.
        client.onEvent(ServerEvent.NewMessage(msg(3, "ben", conv = "dm:ben:me")))
        assertThat(client.state.value.convs.getValue("dm:ben:me").unread).isEqualTo(1)
        assertThat(client.state.value.title("dm:ben:me")).isEqualTo("Ben")
    }

    @Test fun pendingClearsWhenConfirmed() {
        welcome()
        client.sendMessage("hello")
        val send = sent.last() as ClientCommand.Send
        assertThat(client.state.value.pending).containsExactly(send.clientId)
        client.onEvent(ServerEvent.NewMessage(msg(5, "me", text = "hello"), clientId = send.clientId))
        assertThat(client.state.value.pending).isEmpty()
        assertThat(client.state.value.convs.getValue(PUBLIC_CONV).receipts["me"]).isEqualTo(5)
    }

    @Test fun commandsWaitWhileOfflineButTypingDoesNot() {
        welcome()
        online = false
        client.disconnected()
        assertThat(client.state.value.status).isEqualTo(ChatUi.Status.RECONNECTING)
        client.sendMessage("queued")
        client.typing()
        online = true
        sent.clear()
        welcome()
        assertThat(sent.filterIsInstance<ClientCommand.Send>().map { it.text }).containsExactly("queued")
        assertThat(sent.filterIsInstance<ClientCommand.Typing>()).isEmpty()
    }

    @Test fun typingExpires() {
        welcome()
        client.onEvent(ServerEvent.Typing(PUBLIC_CONV, "ben"))
        assertThat(client.state.value.typingIn(PUBLIC_CONV, clock)).containsExactly("Ben")
        assertThat(client.state.value.typingIn(PUBLIC_CONV, clock + 4_000)).isEmpty()
        // A message from them ends it at once.
        client.onEvent(ServerEvent.NewMessage(msg(9, "ben")))
        assertThat(client.state.value.typingIn(PUBLIC_CONV, clock)).isEmpty()
    }

    @Test fun removalAndClosingLeaveTheRoom() {
        welcome()
        client.onEvent(ServerEvent.Opened("room:games"))
        assertThat(client.state.value.active).isEqualTo("room:games")
        client.onEvent(ServerEvent.Removed("games", "ben"))
        assertThat(client.state.value.active).isEqualTo(PUBLIC_CONV)
        assertThat(client.state.value.notice).isEqualTo("You were removed from Games")
        client.onEvent(ServerEvent.Error("wrong_password", "That password isn't right"))
        assertThat(client.state.value.passwordError).isEqualTo("That password isn't right")
        client.onEvent(ServerEvent.Ended("Camp"))
        client.disconnected()
        assertThat(client.state.value.status).isEqualTo(ChatUi.Status.ENDED)
    }

    @Test fun adminsAreRoomCreatorsAndTheHost() {
        welcome()
        val s = client.state.value
        assertThat(s.isAdmin(s.room("room:games"))).isTrue() // the host
        assertThat(s.isAdmin(s.room(PUBLIC_CONV))).isFalse()
    }
}
