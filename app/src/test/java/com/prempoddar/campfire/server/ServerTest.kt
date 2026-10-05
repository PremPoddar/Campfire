package com.prempoddar.campfire.server

import com.google.common.truth.Truth.assertThat
import com.prempoddar.campfire.core.Attachment
import com.prempoddar.campfire.core.ClientCommand
import com.prempoddar.campfire.core.Conv
import com.prempoddar.campfire.core.PUBLIC_ROOM
import com.prempoddar.campfire.core.Protocol
import com.prempoddar.campfire.core.ServerEvent
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withTimeout
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ServerTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun ApplicationTestBuilder.setUp(): Pair<Hub, FileStore> {
        val files = FileStore(tmp.newFolder("files")).also { it.wipe() }
        val hub = Hub("Test Camp", previewer = null, scope = CoroutineScope(SupervisorJob() + Dispatchers.Default))
        application { campfire(hub, files, "Test Camp") }
        return hub to files
    }

    private suspend fun DefaultClientWebSocketSession.sendCmd(cmd: ClientCommand) =
        send(Frame.Text(Protocol.json.encodeToString(ClientCommand.serializer(), cmd)))

    private suspend inline fun <reified T : ServerEvent> DefaultClientWebSocketSession.await(): T = withTimeout(5_000) {
        while (true) {
            val frame = incoming.receive() as? Frame.Text ?: continue
            val event = Protocol.json.decodeFromString(ServerEvent.serializer(), frame.readText())
            if (event is T) return@withTimeout event
        }
        @Suppress("UNREACHABLE_CODE") error("unreachable")
    }

    @Test fun servesClientAndInfo() = testApplication {
        setUp()
        val index = client.get("/")
        assertThat(index.status).isEqualTo(HttpStatusCode.OK)
        assertThat(index.bodyAsText()).contains("<html")
        assertThat(client.get("/api/info").bodyAsText()).contains("Test Camp")
    }

    @Test fun twoPeopleChatOverWebSockets() = testApplication {
        setUp()
        val ws = createClient { install(WebSockets) }
        ws.webSocket("/ws") {
            sendCmd(ClientCommand.Hello(name = "Asha", color = 1))
            val asha = await<ServerEvent.Welcome>()
            assertThat(asha.serverName).isEqualTo("Test Camp")

            ws.webSocket("/ws") {
                sendCmd(ClientCommand.Hello(name = "Ben", color = 2))
                await<ServerEvent.Welcome>()
                sendCmd(ClientCommand.Send(Conv.room(PUBLIC_ROOM), "hello asha", clientId = "c1"))
                assertThat(await<ServerEvent.NewMessage>().clientId).isEqualTo("c1")
            }
            val got = await<ServerEvent.NewMessage>()
            assertThat(got.message.text).isEqualTo("hello asha")
        }
    }

    @Test fun resumingWithTokenKeepsIdentity() = testApplication {
        setUp()
        val ws = createClient { install(WebSockets) }
        var token = ""
        var id = ""
        ws.webSocket("/ws") {
            sendCmd(ClientCommand.Hello(name = "Asha"))
            await<ServerEvent.Welcome>().let { token = it.token; id = it.you.id }
        }
        ws.webSocket("/ws") {
            sendCmd(ClientCommand.Hello(token = token))
            assertThat(await<ServerEvent.Welcome>().you.id).isEqualTo(id)
        }
        ws.webSocket("/ws") {
            sendCmd(ClientCommand.Hello(token = "made-up"))
            assertThat(await<ServerEvent.Error>().code).isEqualTo("session_ended")
        }
    }

    @Test fun hostTokenMakesHost() = testApplication {
        val (hub, _) = setUp()
        val ws = createClient { install(WebSockets) }
        ws.webSocket("/ws") {
            sendCmd(ClientCommand.Hello(name = "Prem", hostToken = hub.hostToken))
            assertThat(await<ServerEvent.Welcome>().you.isHost).isTrue()
        }
        ws.webSocket("/ws") {
            sendCmd(ClientCommand.Hello(name = "Faker", hostToken = "guess"))
            assertThat(await<ServerEvent.Welcome>().you.isHost).isFalse()
        }
    }

    @Test fun uploadThenDownloadWithRange() = testApplication {
        setUp()
        val ws = createClient { install(WebSockets) }
        var token = ""
        ws.webSocket("/ws") {
            sendCmd(ClientCommand.Hello(name = "Asha"))
            token = await<ServerEvent.Welcome>().token
        }
        assertThat(client.put("/api/upload?token=nope&name=a.txt") { setBody("x") }.status)
            .isEqualTo(HttpStatusCode.Unauthorized)

        val body = "0123456789".repeat(100)
        val res = client.put("/api/upload?token=$token&name=../../notes.txt&mime=text/plain") { setBody(body) }
        val att = Protocol.json.decodeFromString(Attachment.serializer(), res.bodyAsText())
        assertThat(att.name).isEqualTo("notes.txt")
        assertThat(att.size).isEqualTo(1000)

        val full = client.get("/files/${att.id}/notes.txt")
        assertThat(full.readRawBytes().size).isEqualTo(1000)
        assertThat(full.headers[HttpHeaders.ContentDisposition]).startsWith("attachment")

        val part = client.get("/files/${att.id}/notes.txt") { header(HttpHeaders.Range, "bytes=10-19") }
        assertThat(part.status).isEqualTo(HttpStatusCode.PartialContent)
        assertThat(part.bodyAsText()).isEqualTo("0123456789")
    }

    @Test fun uploadedHtmlIsNeverServedAsHtml() = testApplication {
        setUp()
        val ws = createClient { install(WebSockets) }
        var token = ""
        ws.webSocket("/ws") {
            sendCmd(ClientCommand.Hello(name = "Asha"))
            token = await<ServerEvent.Welcome>().token
        }
        val res = client.put("/api/upload?token=$token&name=x.html&mime=text/html") { setBody("<script>alert(1)</script>") }
        val att = Protocol.json.decodeFromString(Attachment.serializer(), res.bodyAsText())
        val got = client.get("/files/${att.id}/x.html")
        assertThat(got.headers[HttpHeaders.ContentType]).startsWith("application/octet-stream")
    }

    @Test fun unknownFileIs404() = testApplication {
        setUp()
        assertThat(client.get("/files/aaaaaaaaaaaaaaaaaaaa/x").status).isEqualTo(HttpStatusCode.NotFound)
        assertThat(client.get("/files/../../etc/passwd").status.value).isIn(400..404)
    }
}
