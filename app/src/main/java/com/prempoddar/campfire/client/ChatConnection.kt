package com.prempoddar.campfire.client

import com.prempoddar.campfire.core.Attachment
import com.prempoddar.campfire.core.ClientCommand
import com.prempoddar.campfire.core.Protocol
import com.prempoddar.campfire.core.ServerEvent
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.writeFully
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.InputStream
import java.net.URLEncoder

/**
 * Connects [client] to a Campfire server and keeps it connected: reconnects with backoff, resumes
 * with its token, and joins as host with [hostToken] the first time.
 */
class ChatConnection(
    private val client: ChatClient,
    private val http: HttpClient,
    /** For example `http://127.0.0.1:8080`. */
    private val base: String,
    private val hostToken: String,
    private val name: () -> String,
    private val color: Int = 0,
) {
    private var job: Job? = null
    private var token: String? = null

    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        job = scope.launch {
            var attempt = 0
            while (isActive) {
                try {
                    http.webSocket(base.replaceFirst("http", "ws") + "/ws") {
                        attempt = 0
                        client.send = { c -> outgoing.trySend(Frame.Text(Protocol.encode(c))).isSuccess }
                        hello()
                        for (frame in incoming) {
                            if (frame !is Frame.Text) continue
                            val ev = Protocol.decodeEvent(frame.readText()) ?: continue
                            // The server forgot us (it restarted): join again as the host.
                            if (ev is ServerEvent.Error && ev.code == "session_ended") { token = null; hello(); continue }
                            if (ev is ServerEvent.Welcome) token = ev.token
                            client.onEvent(ev)
                            if (ev is ServerEvent.Ended) return@webSocket
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Connection refused or dropped: retry below.
                }
                client.send = { false }
                client.disconnected()
                val status = client.state.value.status
                if (status == ChatUi.Status.ENDED || status == ChatUi.Status.REMOVED) break
                delay(minOf(8_000L, 500L shl minOf(attempt++, 4)))
            }
        }
    }

    private fun hello() {
        val t = token
        client.send(if (t != null) ClientCommand.Hello(token = t) else ClientCommand.Hello(name = name(), color = color, hostToken = hostToken))
    }

    fun stop() { job?.cancel(); job = null; client.send = { false } }

    class UploadFailed(message: String) : Exception(message)

    /** Uploads a file over HTTP (so a big file never blocks the chat) and returns what to attach. */
    suspend fun upload(input: InputStream, length: Long?, name: String, mime: String, onProgress: (Float) -> Unit = {}): Attachment {
        val t = token ?: throw UploadFailed("Not connected yet")
        val url = "$base/api/upload?token=${enc(t)}&name=${enc(name)}&mime=${enc(mime)}"
        val response = http.put(url) {
            setBody(object : OutgoingContent.WriteChannelContent() {
                override val contentLength: Long? = length
                override val contentType: ContentType = runCatching { ContentType.parse(mime) }.getOrDefault(ContentType.Application.OctetStream)
                override suspend fun writeTo(channel: ByteWriteChannel) {
                    val buf = ByteArray(64 * 1024)
                    var sent = 0L
                    input.use { s ->
                        while (true) {
                            val n = s.read(buf)
                            if (n < 0) break
                            channel.writeFully(buf, 0, n)
                            sent += n
                            if (length != null && length > 0) onProgress((sent.toFloat() / length).coerceIn(0f, 1f))
                        }
                    }
                }
            })
        }
        val body = response.bodyAsText()
        if (response.status != HttpStatusCode.OK) throw UploadFailed(body.ifBlank { "The file couldn't be sent" })
        return Protocol.json.decodeFromString(Attachment.serializer(), body)
    }

    fun fileUrl(a: Attachment) = "$base/files/${a.id}/${enc(a.name)}"
    fun fileUrl(id: String) = "$base/files/$id"

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
}
