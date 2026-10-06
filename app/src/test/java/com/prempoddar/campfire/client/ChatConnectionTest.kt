package com.prempoddar.campfire.client

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.prempoddar.campfire.core.ClientCommand
import com.prempoddar.campfire.core.Conv
import com.prempoddar.campfire.core.PUBLIC_ROOM
import com.prempoddar.campfire.core.Protocol
import com.prempoddar.campfire.core.ServerEvent
import com.prempoddar.campfire.server.FileStore
import com.prempoddar.campfire.server.Hub
import com.prempoddar.campfire.server.campfire
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.server.testing.testApplication
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The app's own chat against the real server, next to someone in a browser. */
class ChatConnectionTest {
    @get:Rule val tmp = TemporaryFolder()

    private suspend fun waitFor(what: String, check: () -> Boolean) = withTimeout(5_000) {
        while (!check()) delay(20)
    }.also { assertWithMessage(what).that(check()).isTrue() }

    private suspend inline fun <reified T : ServerEvent> DefaultClientWebSocketSession.await(crossinline match: (T) -> Boolean = { true }): T = withTimeout(5_000) {
        while (true) {
            val frame = incoming.receive() as? Frame.Text ?: continue
            val event = Protocol.decodeEvent(frame.readText())
            if (event is T && match(event)) return@withTimeout event
        }
        @Suppress("UNREACHABLE_CODE") error("unreachable")
    }

    @Test fun hostChatsWithABrowser() = testApplication {
        val files = FileStore(tmp.newFolder("files")).also { it.wipe() }
        val hub = Hub("Test Camp", previewer = null, scope = CoroutineScope(SupervisorJob() + Dispatchers.Default))
        application { campfire(hub, files, "Test Camp") }
        val http = createClient { install(WebSockets) }

        val chat = ChatClient()
        chat.visible = true
        val connection = ChatConnection(chat, http, "http://localhost", hub.hostToken, name = { "Prem" })
        connection.start(CoroutineScope(SupervisorJob() + Dispatchers.Default))
        waitFor("host joined") { chat.state.value.status == ChatUi.Status.READY }
        assertThat(chat.state.value.me!!.isHost).isTrue()
        assertThat(chat.state.value.me!!.name).isEqualTo("Prem")

        http.webSocket("/ws") {
            send(Frame.Text(Protocol.encode(ClientCommand.Hello(name = "Ben", color = 2))))
            await<ServerEvent.Welcome>()

            // Browser → app.
            send(Frame.Text(Protocol.encode(ClientCommand.Send(Conv.room(PUBLIC_ROOM), "hello host"))))
            waitFor("host got it") { chat.state.value.convs[PUBLIC_CONV]?.messages?.any { it.text == "hello host" } == true }

            // App → browser.
            chat.sendMessage("hi ben")
            assertThat(await<ServerEvent.NewMessage> { it.message.text == "hi ben" }.message.from).isEqualTo(chat.state.value.me!!.id)
            waitFor("confirmed") { chat.state.value.pending.isEmpty() }

            // A file goes over HTTP, then into a message.
            val bytes = "photo bytes".toByteArray()
            val attachment = connection.upload(bytes.inputStream(), bytes.size.toLong(), "pic.jpg", "image/jpeg")
            assertThat(attachment.size).isEqualTo(bytes.size.toLong())
            chat.sendMessage("", attachment = attachment)
            assertThat(await<ServerEvent.NewMessage> { it.message.attachment != null }.message.attachment!!.name).isEqualTo("pic.jpg")

            // The host makes a locked room; the browser sees it.
            chat.createRoom("Secret", "pw")
            waitFor("room opened") { chat.state.value.active.startsWith("room:") && chat.state.value.room(chat.state.value.active)?.name == "Secret" }
            assertThat(await<ServerEvent.Rooms> { r -> r.rooms.any { it.name == "Secret" && it.locked } }.rooms).isNotEmpty()
        }
        connection.stop()
    }
}
