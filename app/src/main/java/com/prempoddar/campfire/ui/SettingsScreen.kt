package com.prempoddar.campfire.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.prempoddar.campfire.AppGraph
import com.prempoddar.campfire.host.Host
import com.prempoddar.campfire.host.HostSettings
import com.prempoddar.campfire.host.HostState
import com.prempoddar.campfire.ui.design.ExpressiveScaffold
import com.prempoddar.campfire.ui.design.PillTextField
import com.prempoddar.campfire.ui.design.SettingsGroup
import com.prempoddar.campfire.ui.design.SettingsRow

@Composable
fun SettingsScreen(onBack: () -> Unit, onAppearance: () -> Unit, onAbout: () -> Unit) {
    val settings by AppGraph.settings.settings.collectAsStateWithLifecycle(null)
    val state by Host.state.collectAsStateWithLifecycle()
    val loaded = settings ?: return
    var name by rememberSaveable { mutableStateOf(loaded.serverName) }
    var host by rememberSaveable { mutableStateOf(loaded.hostName) }
    var port by rememberSaveable { mutableStateOf(loaded.port.toString()) }
    val portValue = port.toIntOrNull()
    val portOk = portValue != null && portValue in HostSettings.PORTS

    // Saved as you type; values that aren't valid simply aren't saved.
    LaunchedEffect(name) { if (name.isNotBlank()) AppGraph.settings.update { it.copy(serverName = name.trim().take(40)) } }
    LaunchedEffect(host) { AppGraph.settings.update { it.copy(hostName = host.trim().take(32)) } }
    LaunchedEffect(port) { if (portOk) AppGraph.settings.update { it.copy(port = portValue!!) } }

    ExpressiveScaffold("Settings", onBack = onBack) { pad ->
        LazyColumn(contentPadding = PaddingValues(top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding() + 32.dp)) {
            if (state is HostState.Running) item {
                Text(
                    "Changes to the names and port apply the next time you start a Campfire.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PillTextField(host, { host = it.take(32) }, "Your name", supporting = "What people see next to your messages", keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
                    PillTextField(name, { name = it.take(40) }, "Campfire name", supporting = "Shown to people when they join", keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
                    PillTextField(
                        port, { port = it.filter(Char::isDigit).take(5) }, "Port", isError = !portOk, clearable = false,
                        supporting = if (portOk) "Part of the link people open. 8080 suits most networks." else "Use a number from 1024 to 65535",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
            }
            item {
                SettingsGroup("General") {
                    SettingsRow("Appearance", "Theme, colours and black backgrounds", Icons.Rounded.Palette, onClick = onAppearance) { Chevron() }
                }
            }
            item {
                SettingsGroup("Privacy") {
                    SettingsRow(
                        "Stays on your network",
                        "Campfire runs only on this phone and your local network; it uses the internet only to fetch link previews. Ending a Campfire deletes every message, room and file.",
                        Icons.Rounded.Shield,
                    )
                    SettingsRow(
                        "Not encrypted",
                        "Anyone watching the same Wi-Fi could read messages, and direct messages pass through this phone. Use it for casual chat, not secrets.",
                        Icons.Rounded.Lock,
                    )
                }
            }
            item {
                SettingsGroup("About") {
                    SettingsRow("About Campfire", "Version, licences", Icons.Rounded.Info, onClick = onAbout) { Chevron() }
                }
            }
        }
    }
}

@Composable
private fun Chevron() = Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
