package com.prempoddar.campfire.core

import com.google.common.truth.Truth.assertThat
import com.prempoddar.campfire.core.ChatState.Delivery
import org.junit.Before
import org.junit.Test

class ChatStateTest {
    private class FakeIds : IdSource {
        var m = 0; var t = 0; var r = 0
        override fun memberId() = "m${++m}"
        override fun token() = "t${++t}"
        override fun roomId() = "r${++r}"
    }

    private lateinit var chat: ChatState
    private lateinit var host: Member
    private lateinit var asha: Member
    private lateinit var ben: Member
    private val pub = Conv.room(PUBLIC_ROOM)

    @Before fun setUp() {
        chat = ChatState(FakeIds(), historyCap = 5)
        host = chat.join("Host", 0, isHost = true)!!.first
        asha = chat.join("Asha", 1, isHost = false)!!.first
        ben = chat.join("Ben", 2, isHost = false)!!.first
    }

    private fun List<Delivery>.events() = map { it.event }
    private inline fun <reified T : ServerEvent> List<Delivery>.only(): T = events().filterIsInstance<T>().single()
    private fun List<Delivery>.errorCode() = (events().single() as ServerEvent.Error).code
    private fun send(m: Member, conv: String, text: String, replyTo: Long? = null) =
        chat.handle(m.id, ClientCommand.Send(conv, text, replyTo), now = 1000).only<ServerEvent.NewMessage>().message

    // Joining

    @Test fun joinDedupesNamesAndAddsToPublic() {
        val (asha2, out) = chat.join("asha", 4, false)!!
        assertThat(asha2.name).isEqualTo("asha 2")
        assertThat(chat.room(PUBLIC_ROOM)!!.members).contains(asha2.id)
        assertThat(out.events().filterIsInstance<ServerEvent.Members>()).isNotEmpty()
    }

    @Test fun joinRejectsBlankNameAndClampsColor() {
        assertThat(chat.join("   ", 1, false)).isNull()
        assertThat(chat.join("Cy", 99, false)!!.first.color).isEqualTo(COLOR_COUNT - 1)
    }

    @Test fun resumeFindsMemberByToken() {
        assertThat(chat.resume(asha.token)).isEqualTo(asha)
        assertThat(chat.resume("nope")).isNull()
    }

    @Test fun welcomeListsJoinedConversationsWithUnread() {
        send(ben, pub, "one"); send(ben, pub, "two")
        val w = chat.welcome(asha.id, "Camp")
        val summary = w.convs.single { it.conv == pub }
        assertThat(summary.unread).isEqualTo(2)
        assertThat(summary.last!!.text).isEqualTo("two")
        assertThat(w.token).isEqualTo(asha.token)
    }

    // Rooms

    @Test fun createRoomJoinsCreatorAndOpensIt() {
        val out = chat.handle(asha.id, ClientCommand.CreateRoom("  Snacks ", null), 0)
        val room = chat.rooms().single { it.name == "Snacks" }
        assertThat(room.members).containsExactly(asha.id)
        assertThat(out.only<ServerEvent.Opened>().conv).isEqualTo(Conv.room(room.id))
        assertThat(out.single { it.event is ServerEvent.Rooms }.to).containsAtLeast(host.id, asha.id, ben.id)
    }

    @Test fun lockedRoomNeedsPasswordExceptForHost() {
        chat.handle(asha.id, ClientCommand.CreateRoom("Secret", "pw"), 0)
        val id = chat.rooms().single { it.name == "Secret" }.id
        assertThat(chat.handle(ben.id, ClientCommand.JoinRoom(id, "bad"), 0).errorCode()).isEqualTo("wrong_password")
        chat.handle(ben.id, ClientCommand.JoinRoom(id, "pw"), 0)
        chat.handle(host.id, ClientCommand.JoinRoom(id, null), 0)
        assertThat(chat.room(id)!!.members).containsExactly(asha.id, ben.id, host.id)
    }

    @Test fun onlyAdminsUpdateOrCloseRooms() {
        chat.handle(asha.id, ClientCommand.CreateRoom("Snacks", null), 0)
        val id = chat.rooms().single { it.name == "Snacks" }.id
        chat.handle(ben.id, ClientCommand.JoinRoom(id), 0)
        assertThat(chat.handle(ben.id, ClientCommand.UpdateRoom(id, name = "Mine"), 0).errorCode()).isEqualTo("not_allowed")
        chat.handle(asha.id, ClientCommand.UpdateRoom(id, name = "Treats", password = "x"), 0)
        assertThat(chat.room(id)!!.name).isEqualTo("Treats")
        assertThat(chat.room(id)!!.passwordHash).isNotNull()

        assertThat(chat.handle(ben.id, ClientCommand.CloseRoom(id), 0).errorCode()).isEqualTo("not_allowed")
        val out = chat.handle(host.id, ClientCommand.CloseRoom(id), 0)
        assertThat(chat.room(id)).isNull()
        assertThat(out.single { it.event is ServerEvent.RoomClosed }.to).containsExactly(asha.id, ben.id)
    }

    @Test fun publicRoomCannotBeClosedLockedOrLeft() {
        assertThat(chat.handle(host.id, ClientCommand.CloseRoom(PUBLIC_ROOM), 0).errorCode()).isEqualTo("not_allowed")
        assertThat(chat.handle(host.id, ClientCommand.UpdateRoom(PUBLIC_ROOM, password = "x"), 0).errorCode()).isEqualTo("not_allowed")
        assertThat(chat.handle(asha.id, ClientCommand.LeaveRoom(PUBLIC_ROOM), 0).errorCode()).isEqualTo("not_allowed")
    }

    @Test fun removedMemberCannotRejoinRoom() {
        chat.handle(asha.id, ClientCommand.CreateRoom("Snacks", null), 0)
        val id = chat.rooms().single { it.name == "Snacks" }.id
        chat.handle(ben.id, ClientCommand.JoinRoom(id), 0)
        val out = chat.handle(asha.id, ClientCommand.RemoveMember(id, ben.id), 0)
        assertThat(out.single { it.event is ServerEvent.Removed }.to).containsExactly(ben.id)
        assertThat(chat.room(id)!!.members).doesNotContain(ben.id)
        assertThat(chat.handle(ben.id, ClientCommand.JoinRoom(id), 0).errorCode()).isEqualTo("removed")
    }

    @Test fun banIsHostOnlyAndRevokesToken() {
        assertThat(chat.handle(asha.id, ClientCommand.Ban(ben.id), 0).errorCode()).isEqualTo("not_allowed")
        val out = chat.handle(host.id, ClientCommand.Ban(ben.id), 0)
        assertThat(out.single { it.event is ServerEvent.Removed }.to).containsExactly(ben.id)
        assertThat(chat.resume(ben.token)).isNull()
        assertThat(chat.room(PUBLIC_ROOM)!!.members).doesNotContain(ben.id)
    }

    // Messages

    @Test fun messagesGoOnlyToParticipants() {
        chat.handle(asha.id, ClientCommand.CreateRoom("Snacks", null), 0)
        val conv = Conv.room(chat.rooms().single { it.name == "Snacks" }.id)
        val out = chat.handle(asha.id, ClientCommand.Send(conv, "hi", clientId = "c1"), 5)
        val d = out.single()
        assertThat(d.to).containsExactly(asha.id)
        assertThat((d.event as ServerEvent.NewMessage).clientId).isEqualTo("c1")
        assertThat(chat.handle(ben.id, ClientCommand.Send(conv, "let me in"), 5).errorCode()).isEqualTo("not_member")
    }

    @Test fun emptyOrTooLongMessagesAreRejected() {
        assertThat(chat.handle(asha.id, ClientCommand.Send(pub, "   "), 0).errorCode()).isEqualTo("empty")
        assertThat(chat.handle(asha.id, ClientCommand.Send(pub, "x".repeat(4001)), 0).errorCode()).isEqualTo("too_long")
    }

    @Test fun attachmentOnlyMessageIsFine() {
        val out = chat.handle(asha.id, ClientCommand.Send(pub, "", attachment = Attachment("f1", "a.png", 10, "image/png")), 0)
        assertThat(out.only<ServerEvent.NewMessage>().message.attachment!!.id).isEqualTo("f1")
    }

    @Test fun replyMustPointIntoSameConversation() {
        val first = send(asha, pub, "first")
        assertThat(send(ben, pub, "re", replyTo = first.id).replyTo).isEqualTo(first.id)
        assertThat(send(ben, pub, "re", replyTo = 999).replyTo).isNull()
    }

    @Test fun editOwnOnly() {
        val m = send(asha, pub, "helo")
        assertThat(chat.handle(ben.id, ClientCommand.Edit(m.id, "hacked"), 9).errorCode()).isEqualTo("not_allowed")
        val updated = chat.handle(asha.id, ClientCommand.Edit(m.id, "hello"), 9).only<ServerEvent.MessageUpdated>().message
        assertThat(updated.text).isEqualTo("hello")
        assertThat(updated.editedAt).isEqualTo(9)
    }

    @Test fun deleteOwnOrAsAdmin() {
        val m = send(asha, pub, "oops")
        assertThat(chat.handle(ben.id, ClientCommand.Delete(m.id), 0).errorCode()).isEqualTo("not_allowed")
        val gone = chat.handle(host.id, ClientCommand.Delete(m.id), 0).only<ServerEvent.MessageUpdated>().message
        assertThat(gone.deleted).isTrue()
        assertThat(gone.text).isEmpty()
        assertThat(chat.handle(asha.id, ClientCommand.Edit(m.id, "back"), 0).errorCode()).isEqualTo("not_found")
    }

    @Test fun reactionsToggle() {
        val m = send(asha, pub, "fire")
        chat.handle(ben.id, ClientCommand.React(m.id, "🔥"), 0)
        val two = chat.handle(asha.id, ClientCommand.React(m.id, "🔥"), 0).only<ServerEvent.MessageUpdated>().message
        assertThat(two.reactions["🔥"]).containsExactly(ben.id, asha.id)
        val back = chat.handle(ben.id, ClientCommand.React(m.id, "🔥"), 0).only<ServerEvent.MessageUpdated>().message
        assertThat(back.reactions["🔥"]).containsExactly(asha.id)
        chat.handle(asha.id, ClientCommand.React(m.id, "🔥"), 0)
        assertThat(chat.message(m.id)!!.reactions).isEmpty()
    }

    @Test fun historyIsCapped() {
        repeat(7) { send(asha, pub, "n$it") }
        val h = chat.handle(ben.id, ClientCommand.History(pub), 0).only<ServerEvent.History>()
        assertThat(h.messages.map { it.text }).containsExactly("n2", "n3", "n4", "n5", "n6").inOrder()
    }

    // Receipts & typing

    @Test fun receiptsOnlyMoveForward() {
        val a = send(asha, pub, "a"); val b = send(asha, pub, "b")
        val out = chat.handle(ben.id, ClientCommand.Read(pub, b.id), 0)
        assertThat(out.only<ServerEvent.ReceiptUpdate>().upTo).isEqualTo(b.id)
        assertThat(chat.handle(ben.id, ClientCommand.Read(pub, a.id), 0)).isEmpty()
        assertThat(chat.handle(ben.id, ClientCommand.Read(pub, 10_000), 0)).isEmpty()
    }

    @Test fun sendingMarksOwnMessageRead() {
        val m = send(asha, pub, "mine")
        val h = chat.handle(asha.id, ClientCommand.History(pub), 0).only<ServerEvent.History>()
        assertThat(h.receipts).contains(Receipt(asha.id, m.id))
    }

    @Test fun typingGoesToOthersOnly() {
        val d = chat.handle(asha.id, ClientCommand.Typing(pub), 0).single()
        assertThat(d.to).containsExactly(host.id, ben.id)
    }

    // DMs

    @Test fun directMessagesStayBetweenTwo() {
        val opened = chat.handle(asha.id, ClientCommand.OpenDm(ben.id), 0).only<ServerEvent.Opened>()
        assertThat(opened.conv).isEqualTo(Conv.dm(asha.id, ben.id))
        val out = chat.handle(asha.id, ClientCommand.Send(opened.conv, "psst"), 0)
        assertThat(out.single().to).containsExactly(asha.id, ben.id)
        assertThat(chat.handle(host.id, ClientCommand.History(opened.conv), 0).errorCode()).isEqualTo("not_member")
        assertThat(chat.welcome(ben.id, "x").convs.map { it.conv }).contains(opened.conv)
    }

    @Test fun cannotDmYourself() {
        assertThat(chat.handle(asha.id, ClientCommand.OpenDm(asha.id), 0).errorCode()).isEqualTo("not_found")
    }

    // Presence & previews

    @Test fun onlineChangesBroadcastMembers() {
        val out = chat.setOnline(asha.id, true)
        assertThat(out.only<ServerEvent.Members>().members.single { it.id == asha.id }.online).isTrue()
        assertThat(chat.setOnline(asha.id, true)).isEmpty()
    }

    @Test fun previewAppliesOnlyWhileUrlStillInText() {
        val m = send(asha, pub, "look https://example.com/a")
        val out = chat.applyPreview(m.id, LinkPreview("https://example.com/a", "Example"))
        assertThat(out.only<ServerEvent.MessageUpdated>().message.preview!!.title).isEqualTo("Example")
        chat.handle(asha.id, ClientCommand.Edit(m.id, "never mind"), 0)
        assertThat(chat.message(m.id)!!.preview).isNull()
        assertThat(chat.applyPreview(m.id, LinkPreview("https://example.com/a", "Late"))).isEmpty()
    }
}
