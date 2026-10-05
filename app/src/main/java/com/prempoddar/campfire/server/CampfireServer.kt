package com.prempoddar.campfire.server

import com.prempoddar.campfire.core.Attachment
import com.prempoddar.campfire.core.Protocol
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.http.content.LocalFileContent
import io.ktor.server.http.content.staticFiles
import io.ktor.server.http.content.staticResources
import io.ktor.server.plugins.autohead.AutoHeadResponse
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.partialcontent.PartialContent
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import io.ktor.server.websocket.webSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import kotlin.time.Duration.Companion.seconds

/** One running Campfire: the chat hub plus the web server around it. */
class CampfireServer(
    private val port: Int,
    private val serverName: String,
    filesDir: File,
    private val webDir: File? = null,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val files = FileStore(filesDir)
    val hub = Hub(serverName, LinkPreviewer(files), scope)
    private var engine: EmbeddedServer<*, *>? = null

    /** Starts listening on all interfaces. Throws if the port is taken. */
    fun start() {
        files.wipe()
        engine = embeddedServer(CIO, port = port, host = "0.0.0.0") {
            campfire(hub, files, serverName, webDir)
        }.start(wait = false)
    }

    fun stop() {
        runBlocking { runCatching { hub.end() } }
        engine?.stop(gracePeriodMillis = 300, timeoutMillis = 1_500)
        engine = null
        scope.cancel()
        files.wipe()
    }
}

/** [webDir] serves the client from disk instead of the APK; only used while developing. */
fun Application.campfire(hub: Hub, files: FileStore, serverName: String, webDir: File? = null) {
    install(WebSockets) {
        pingPeriod = 15.seconds
        timeout = 30.seconds
        maxFrameSize = 256 * 1024
    }
    install(PartialContent)
    install(AutoHeadResponse)

    routing {
        if (webDir != null) staticFiles("/", webDir) else staticResources("/", "web")

        get("/api/info") {
            call.respondText(buildJsonObject { put("name", serverName) }.toString(), ContentType.Application.Json)
        }

        webSocket("/ws") {
            hub.session(this, call.request.origin.remoteHost)
        }

        put("/api/upload") {
            val token = call.request.queryParameters["token"].orEmpty()
            if (hub.memberByToken(token) == null || hub.isBanned(call.request.origin.remoteHost)) {
                call.respondText("Join the chat first.", status = HttpStatusCode.Unauthorized)
                return@put
            }
            val name = call.request.queryParameters["name"].orEmpty()
            val mime = call.request.queryParameters["mime"].orEmpty()
            val size = call.request.headers[HttpHeaders.ContentLength]?.toLongOrNull()
            val attachment: Attachment = try {
                files.save(call.receiveChannel(), name, mime, size)
            } catch (e: FileStore.NoSpaceException) {
                call.respondText("The host phone is out of space.", status = HttpStatusCode.InsufficientStorage)
                return@put
            }
            call.respondText(Protocol.json.encodeToString(Attachment.serializer(), attachment), ContentType.Application.Json)
        }

        get("/files/{id}/{name?}") {
            val (file, meta) = files.file(call.parameters["id"].orEmpty()) ?: run {
                call.respondText("This file is gone.", status = HttpStatusCode.NotFound)
                return@get
            }
            val inline = meta.mime.startsWith("image/") || meta.mime.startsWith("video/") || meta.mime.startsWith("audio/")
            val disposition = if (inline) ContentDisposition.Inline else ContentDisposition.Attachment
            call.response.header(
                HttpHeaders.ContentDisposition,
                disposition.withParameter(ContentDisposition.Parameters.FileNameAsterisk, "utf-8''" + encode(meta.name)).toString(),
            )
            call.response.header("X-Content-Type-Options", "nosniff")
            call.respond(LocalFileContent(file, ContentType.parse(safeType(meta.mime))))
        }
    }
}

/** Never let an upload be served as a page or script on Campfire's own origin. */
private fun safeType(mime: String): String {
    val risky = listOf("html", "javascript", "ecmascript", "xml", "svg")
    return if (risky.any { mime.contains(it, ignoreCase = true) }) "application/octet-stream" else mime
}

private fun encode(name: String) = java.net.URLEncoder.encode(name, "UTF-8").replace("+", "%20")
