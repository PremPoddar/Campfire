package com.prempoddar.campfire.host

import com.prempoddar.campfire.server.Hub
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface HostState {
    data object Stopped : HostState
    data object Starting : HostState
    data class Running(
        val serverName: String,
        val port: Int,
        val addresses: List<LanAddress>,
        val hub: Hub,
        val startedAt: Long = System.currentTimeMillis(),
    ) : HostState {
        val primaryUrl: String? get() = addresses.firstOrNull()?.url(port)
        val localChatUrl: String get() = "http://127.0.0.1:$port/#host=${hub.hostToken}"
    }
    data class Failed(val message: String) : HostState
}

/** What the service is doing, for the screens. */
object Host {
    internal val mutable = MutableStateFlow<HostState>(HostState.Stopped)
    val state: StateFlow<HostState> = mutable.asStateFlow()
}
