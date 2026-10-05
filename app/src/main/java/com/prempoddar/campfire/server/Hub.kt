package com.prempoddar.campfire.server

import com.prempoddar.campfire.core.ChatState
import com.prempoddar.campfire.core.ClientCommand
import com.prempoddar.campfire.core.Links
import com.prempoddar.campfire.core.Member
import com.prempoddar.campfire.core.Protocol
import com.prempoddar.campfire.core.ServerEvent
import io.ktor.websocket.CloseReason
import io.ktor.websocket.DefaultWebSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.SecureRandom

/**
 * Connects sockets to [ChatState]. Every change runs under one lock, so the chat rules never see
 * two commands at once; events are queued per socket and written by that socket's own coroutine.
 */
class Hub(
    private val serverName: String,
    private val previewer: LinkPreviewer?,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private class Connection(val memberId: String, val ip: String, val outbox: Channel<String>, val socket: DefaultWebSocketSession)

    private val state = ChatState()
    private val lock = Mutex()
    private val connections = mutableMapOf<String, MutableSet<Connection>>()
    private val lastIp = mutableMapOf<String, String>()
    private val bannedIps = mutableSetOf<String>()

    /** Lets the host app's own chat join as host. */
    val hostToken: String = SecureRandom().let { r -> (1..32).map { "abcdefghjkmnpqrstuvwxyz23456789"[r.nextInt(31)] }.joinToString("") }

    private val _snapshot = MutableStateFlow(state.snapshot())
    /** Rooms and people, for the host app's screen. */
    val snapshot: StateFlow<ChatState.Snapshot> = _snapshot.asStateFlow()

    suspend fun isBanned(ip: String) = lock.withLock { ip in bannedIps }

    suspend fun memberByToken(token: String): Member? = lock.withLock { state.resume(token) }

    /** Runs one WebSocket until it closes. The first message must be a `hello`. */
    suspend fun session(socket: DefaultWebSocketSession, ip: String) {
        if (isBanned(ip)) {
            socket.send(Frame.Text(Protocol.encode(ServerEvent.Error("banned", "The host removed you from this Campfire."))))
            socket.close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "banned"))
            return
        }
        val outbox = Channel<String>(OUTBOX_SIZE)
        val writer = socket.launch {
            for (text in outbox) socket.send(Frame.Text(text))
        }
        var conn: Connection? = null
        try {
            for (frame in socket.incoming) {
                if (frame !is Frame.Text) continue
                val cmd = Protocol.decodeCommand(frame.readText()) ?: continue
                val current = conn
                if (current == null) {
                    if (cmd is ClientCommand.Hello) conn = hello(cmd, ip, outbox, socket)
                } else {
                    process(current.memberId, cmd)
                }
            }
        } finally {
            conn?.let { disconnect(it) }
            outbox.close()
            writer.join()
        }
    }

    private suspend fun hello(cmd: ClientCommand.Hello, ip: String, outbox: Channel<String>, socket: DefaultWebSocketSession): Connection? =
        lock.withLock {
            val member = cmd.token?.let(state::resume) ?: cmd.name?.let { name ->
                val isHost = cmd.hostToken != null && cmd.hostToken == hostToken
                state.join(name, cmd.color, isHost)?.also { (_, out) -> dispatch(out) }?.first
            }
            if (member == null) {
                val error = if (cmd.token != null && cmd.name == null) {
                    ServerEvent.Error("session_ended", "Your session has ended. Join again.")
                } else {
                    ServerEvent.Error("bad_name", "Pick a name of up to 32 characters.")
                }
                outbox.trySend(Protocol.encode(error))
                return@withLock null
            }
            val conn = Connection(member.id, ip, outbox, socket)
            connections.getOrPut(member.id) { mutableSetOf() } += conn
            lastIp[member.id] = ip
            outbox.trySend(Protocol.encode(state.welcome(member.id, serverName)))
            dispatch(state.setOnline(member.id, true))
            publishSnapshot()
            conn
        }

    private suspend fun process(memberId: String, cmd: ClientCommand) {
        val deliveries = lock.withLock {
            state.handle(memberId, cmd, clock()).also {
                dispatch(it)
                publishSnapshot()
            }
        }
        // Fetch a link preview for new and edited messages that have a link and no preview yet.
        deliveries.mapNotNull {
            when (val e = it.event) {
                is ServerEvent.NewMessage -> e.message
                is ServerEvent.MessageUpdated -> e.message.takeIf { cmd is ClientCommand.Edit }
                else -> null
            }
        }.distinctBy { it.id }.forEach { msg ->
            val url = Links.firstUrl(msg.text) ?: return@forEach
            if (msg.preview?.url == url || previewer == null) return@forEach
            scope.launch {
                val preview = previewer.preview(url) ?: return@launch
                lock.withLock { dispatch(state.applyPreview(msg.id, preview)) }
            }
        }
        // Someone removed from the whole Campfire loses their sockets too.
        deliveries.filter { val e = it.event; e is ServerEvent.Removed && e.roomId == null }
            .flatMap { it.to }
            .forEach { kick(it) }
    }

    private suspend fun disconnect(conn: Connection) = lock.withLock {
        val set = connections[conn.memberId] ?: return@withLock
        set -= conn
        if (set.isEmpty()) {
            connections.remove(conn.memberId)
            dispatch(state.setOnline(conn.memberId, false))
            publishSnapshot()
        }
    }

    // Host app actions

    suspend fun removePerson(memberId: String) {
        lock.withLock {
            dispatch(state.removeFromServer(memberId))
            publishSnapshot()
        }
        kick(memberId)
    }

    suspend fun closeRoom(roomId: String) = lock.withLock {
        dispatch(state.adminCloseRoom(roomId))
        publishSnapshot()
    }

    /** Tells everyone the Campfire is over; the server closes the sockets afterwards. */
    suspend fun end() = lock.withLock {
        val text = Protocol.encode(ServerEvent.Ended(serverName))
        connections.values.flatten().forEach { it.outbox.trySend(text) }
    }

    /** Bans the member's address and closes their sockets once the "removed" event is out. */
    private suspend fun kick(memberId: String) {
        val conns = lock.withLock {
            lastIp[memberId]?.let { bannedIps += it }
            connections.remove(memberId).orEmpty()
        }
        conns.forEach { c ->
            c.outbox.close()
            scope.launch {
                delay(300) // let the "removed" event reach the browser first
                runCatching { c.socket.close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "removed")) }
            }
        }
    }

    private fun dispatch(deliveries: List<ChatState.Delivery>) {
        deliveries.forEach { d ->
            val text = Protocol.encode(d.event)
            d.to.forEach { id ->
                connections[id]?.forEach { c ->
                    // A socket that can't keep up is dropped; the browser reconnects and resyncs.
                    if (c.outbox.trySend(text).isFailure) c.outbox.close()
                }
            }
        }
    }

    private fun publishSnapshot() {
        _snapshot.value = state.snapshot()
    }

    private companion object {
        const val OUTBOX_SIZE = 2048
    }
}
