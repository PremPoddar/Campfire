package com.prempoddar.campfire.client

import com.prempoddar.campfire.host.Host
import com.prempoddar.campfire.host.HostState
import com.prempoddar.campfire.host.SettingsRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * The host's seat at the fire. While a Campfire runs, the app stays connected to its own server
 * like any browser, so messages arrive even when the chat isn't on screen.
 */
class HostChat(private val settings: SettingsRepository) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val http = HttpClient(CIO) { install(WebSockets) }

    /** The current chat, or null when no Campfire is running. */
    private val _session = MutableStateFlow<Session?>(null)
    val session: StateFlow<Session?> = _session.asStateFlow()

    class Session(val client: ChatClient, val connection: ChatConnection, val hubKey: Any)

    fun start() {
        scope.launch {
            Host.state.collect { state ->
                val running = state as? HostState.Running
                val current = _session.value
                when {
                    running == null && current != null -> { current.connection.stop(); _session.value = null }
                    running != null && current?.hubKey !== running.hub -> {
                        current?.connection?.stop()
                        val client = ChatClient()
                        val connection = ChatConnection(
                            client, http, "http://127.0.0.1:${running.port}", running.hub.hostToken,
                            name = { runBlocking { settings.settings.first().hostName.ifBlank { "Host" } } },
                        )
                        connection.start(scope)
                        _session.value = Session(client, connection, running.hub)
                    }
                }
            }
        }
    }
}
