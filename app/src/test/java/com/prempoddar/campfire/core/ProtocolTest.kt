package com.prempoddar.campfire.core

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ProtocolTest {
    @Test fun decodesHandWrittenClientJson() {
        val cmd = Protocol.decodeCommand("""{"type":"send","conv":"room:public","text":"hi","replyTo":4,"extra":1}""")
        assertThat(cmd).isEqualTo(ClientCommand.Send(conv = "room:public", text = "hi", replyTo = 4))
    }

    @Test fun everyCommandRoundTrips() {
        val all = listOf(
            ClientCommand.Hello(token = "t", name = "Asha", color = 3, hostToken = "h"),
            ClientCommand.JoinRoom("r1", "pw"),
            ClientCommand.LeaveRoom("r1"),
            ClientCommand.CreateRoom("Snacks", null),
            ClientCommand.UpdateRoom("r1", name = "Snacks 2", password = "x", clearPassword = false),
            ClientCommand.CloseRoom("r1"),
            ClientCommand.RemoveMember("r1", "m2"),
            ClientCommand.Ban("m2"),
            ClientCommand.OpenDm("m2"),
            ClientCommand.History("room:r1"),
            ClientCommand.Send("room:r1", "yo", null, Attachment("f", "a.png", 3, "image/png"), clientId = "c1"),
            ClientCommand.Send("room:r1", poll = PollDraft("Go?", listOf("Yes", "No"))),
            ClientCommand.Pin(5, true), ClientCommand.Vote(5, 1), ClientCommand.Mute("m2", 60),
            ClientCommand.Edit(5, "fixed"),
            ClientCommand.Delete(5),
            ClientCommand.React(5, "🔥"),
            ClientCommand.Read("room:r1", 9),
            ClientCommand.Typing("room:r1"),
        )
        all.forEach { cmd ->
            val json = Protocol.json.encodeToString(ClientCommand.serializer(), cmd)
            assertThat(Protocol.decodeCommand(json)).isEqualTo(cmd)
        }
    }

    @Test fun eventsCarryTypeDiscriminator() {
        val text = Protocol.encode(ServerEvent.Typing("room:public", "m1"))
        assertThat(text).contains("\"type\":\"typing\"")
        assertThat(Protocol.encode(ServerEvent.Error("bad", "Nope"))).contains("\"type\":\"error\"")
    }

    @Test fun garbageDecodesToNull() {
        assertThat(Protocol.decodeCommand("not json")).isNull()
        assertThat(Protocol.decodeCommand("""{"type":"launch_rockets"}""")).isNull()
    }
}
