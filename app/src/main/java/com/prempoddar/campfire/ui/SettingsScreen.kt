package com.prempoddar.campfire.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.prempoddar.campfire.AppGraph
import com.prempoddar.campfire.host.Host
import com.prempoddar.campfire.host.HostSettings
import com.prempoddar.campfire.host.HostState
import com.prempoddar.campfire.host.ThemeMode
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val settings by AppGraph.settings.settings.collectAsStateWithLifecycle(null)
    val state by Host.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val loaded = settings ?: return
    var name by rememberSaveable { mutableStateOf(loaded.serverName) }
    var port by rememberSaveable { mutableStateOf(loaded.port.toString()) }
    val portValue = port.toIntOrNull()
    val portOk = portValue != null && portValue in HostSettings.PORTS

    // Save as the user types; invalid values are simply not saved.
    LaunchedEffect(name) {
        if (name.isNotBlank()) AppGraph.settings.update { it.copy(serverName = name.trim().take(40)) }
    }
    LaunchedEffect(port) {
        if (portOk) AppGraph.settings.update { it.copy(port = portValue!!) }
    }

    val insets = WindowInsets.safeDrawing.asPaddingValues()
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState())
            .padding(top = insets.calculateTopPadding(), bottom = insets.calculateBottomPadding() + 24.dp).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
            Text("Settings", style = MaterialTheme.typography.headlineMedium)
        }
        if (state is HostState.Running) {
            Text(
                "Changes to the name and port apply the next time you start a Campfire.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        Card {
            OutlinedTextField(
                value = name, onValueChange = { name = it.take(40) }, singleLine = true,
                label = { Text("Campfire name") },
                supportingText = { Text("Shown to people when they join") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = port, onValueChange = { port = it.filter(Char::isDigit).take(5) }, singleLine = true,
                label = { Text("Port") },
                isError = !portOk,
                supportingText = { Text(if (portOk) "Part of the link people open. 8080 suits most networks." else "Use a number from 1024 to 65535") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Card {
            Text("Appearance", style = MaterialTheme.typography.titleSmall)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                val options = listOf(ThemeMode.SYSTEM to "System", ThemeMode.LIGHT to "Light", ThemeMode.DARK to "Dark")
                options.forEachIndexed { i, (mode, label) ->
                    SegmentedButton(
                        selected = loaded.theme == mode,
                        onClick = { scope.launch { AppGraph.settings.update { it.copy(theme = mode) } } },
                        shape = SegmentedButtonDefaults.itemShape(i, options.size),
                    ) { Text(label) }
                }
            }
            Text("People in the browser chat pick their own theme from its menu.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Card {
            Text("Privacy", style = MaterialTheme.typography.titleSmall)
            Text(
                "Campfire runs only on this phone and your local network; it never uses the internet except to fetch link previews. " +
                    "Ending a Campfire deletes every message, room and file.\n\n" +
                    "It is not encrypted. Anyone watching the same Wi-Fi could read messages, and direct messages pass through this phone. " +
                    "Use it for casual chat, not secrets.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surface).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) { content() }
}
