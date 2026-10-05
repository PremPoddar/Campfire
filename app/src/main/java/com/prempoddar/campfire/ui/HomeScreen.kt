package com.prempoddar.campfire.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.prempoddar.campfire.AppGraph
import com.prempoddar.campfire.core.ChatState
import com.prempoddar.campfire.core.MemberInfo
import com.prempoddar.campfire.core.PUBLIC_ROOM
import com.prempoddar.campfire.core.RoomInfo
import com.prempoddar.campfire.host.CampfireService
import com.prempoddar.campfire.host.Host
import com.prempoddar.campfire.host.HostState
import com.prempoddar.campfire.host.LanAddress
import com.prempoddar.campfire.host.NetworkInfo
import kotlinx.coroutines.launch

private val swatches = listOf(0xFFE5484D, 0xFFF76B15, 0xFFC99400, 0xFF3E9B4F, 0xFF12A594, 0xFF0090FF, 0xFF3E63DD, 0xFF8E4EC6, 0xFFD6409F, 0xFF7C7C74)

private sealed interface Confirm {
    data object End : Confirm
    data class RemovePerson(val member: MemberInfo) : Confirm
    data class CloseRoom(val room: RoomInfo) : Confirm
}

@Composable
fun HomeScreen(onOpenChat: () -> Unit, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val state by Host.state.collectAsStateWithLifecycle()
    val settings by AppGraph.settings.settings.collectAsStateWithLifecycle(null)
    var confirm by remember { mutableStateOf<Confirm?>(null) }
    val scope = rememberCoroutineScope()

    // The notification is how people find the running server again; ask for it before starting.
    val notify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { CampfireService.start(context) }
    val start = {
        val needs = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needs) notify.launch(Manifest.permission.POST_NOTIFICATIONS) else CampfireService.start(context)
    }

    val running = state as? HostState.Running
    val snapshot = running?.hub?.snapshot?.collectAsStateWithLifecycle()?.value
    val insets = WindowInsets.safeDrawing.asPaddingValues()

    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = insets.calculateTopPadding() + 8.dp, bottom = insets.calculateBottomPadding() + 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Campfire", style = MaterialTheme.typography.displaySmall, modifier = Modifier.weight(1f))
                IconButton(onClick = onOpenSettings) { Icon(Icons.Rounded.Settings, contentDescription = "Settings") }
            }
        }
        item {
            when (val s = state) {
                is HostState.Running -> RunningCard(s, onOpenChat, onEnd = { confirm = Confirm.End })
                HostState.Starting -> StoppedCard(settings?.serverName.orEmpty(), starting = true, error = null, onStart = {})
                is HostState.Failed -> StoppedCard(settings?.serverName.orEmpty(), starting = false, error = s.message, onStart = start)
                HostState.Stopped -> StoppedCard(settings?.serverName.orEmpty(), starting = false, error = null, onStart = start)
            }
        }
        if (snapshot != null) {
            val people = snapshot.members.filter { !it.isHost || it.online }.sortedWith(compareByDescending<MemberInfo> { it.online }.thenBy { it.name })
            item { Section("People", "${snapshot.members.count { it.online }} here now") }
            if (people.isEmpty()) {
                item { Hint("Nobody has joined yet. Share the link or let people scan the code.") }
            }
            items(people, key = { "m" + it.id }) { m ->
                PersonRow(m, onRemove = { confirm = Confirm.RemovePerson(m) })
            }
            item { Section("Rooms", "${snapshot.rooms.size}") }
            items(snapshot.rooms, key = { "r" + it.id }) { r ->
                RoomRow(r, snapshot, onClose = { confirm = Confirm.CloseRoom(r) })
            }
        }
    }

    confirm?.let { c ->
        val (title, body, action) = when (c) {
            Confirm.End -> Triple("End this Campfire?", "Everyone is disconnected and every message, room and file is deleted.", "End Campfire")
            is Confirm.RemovePerson -> Triple("Remove ${c.member.name}?", "They're disconnected and their device can't rejoin until you start a new Campfire.", "Remove")
            is Confirm.CloseRoom -> Triple("Close ${c.room.name}?", "Everyone in it is moved out and its messages are deleted.", "Close room")
        }
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(title) },
            text = { Text(body) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    when (c) {
                        Confirm.End -> CampfireService.stop(context)
                        is Confirm.RemovePerson -> running?.hub?.let { hub -> scope.launch { hub.removePerson(c.member.id) } }
                        is Confirm.CloseRoom -> running?.hub?.let { hub -> scope.launch { hub.closeRoom(c.room.id) } }
                    }
                }) { Text(action, color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun StoppedCard(serverName: String, starting: Boolean, error: String?, onStart: () -> Unit) {
    val addresses = remember(starting, error) { NetworkInfo.addresses() }
    Column(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surface).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Start a chat for everyone nearby", style = MaterialTheme.typography.headlineSmall)
        Text(
            "People on the same Wi-Fi, or on your hotspot, open a link in their browser and chat. Nothing goes over the internet, and it all disappears when you end it.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        NetworkLine(addresses)
        if (error != null) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(4.dp))
        Button(onClick = onStart, enabled = !starting, modifier = Modifier.fillMaxWidth().height(54.dp)) {
            Text(if (starting) "Starting…" else "Start $serverName", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun NetworkLine(addresses: List<LanAddress>) {
    val first = addresses.firstOrNull()
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (first == null) {
            Icon(Icons.Rounded.WifiOff, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Connect to Wi-Fi or turn on your hotspot so people can join.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        } else {
            Box(Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
            Spacer(Modifier.width(8.dp))
            Text(
                "${kindLabel(first.kind)} · ${first.ip}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun kindLabel(kind: LanAddress.Kind) = when (kind) {
    LanAddress.Kind.HOTSPOT -> "On your hotspot"
    LanAddress.Kind.WIFI -> "On Wi-Fi"
    LanAddress.Kind.OTHER -> "On local network"
}

@Composable
private fun RunningCard(s: HostState.Running, onOpenChat: () -> Unit, onEnd: () -> Unit) {
    val context = LocalContext.current
    val url = s.primaryUrl
    Column(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.primaryContainer).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondary))
            Spacer(Modifier.width(8.dp))
            Text("${s.serverName} is on", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (url == null) {
            Text("No network to share yet", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            NetworkLine(emptyList())
        } else {
            Text("Open this on another device", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f))
            Text(url.removePrefix("http://"), style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight(750)), color = MaterialTheme.colorScheme.onPrimaryContainer)
            val qr = remember(url) { qrCode(url) }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(Modifier.clip(MaterialTheme.shapes.medium).background(Color.White).padding(16.dp)) {
                    Image(qr, contentDescription = "QR code for $url", modifier = Modifier.size(200.dp))
                }
            }
            Text(
                "${kindLabel(s.addresses.first().kind)}. Anyone joining must be on the same network.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = {
                    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Campfire link", url))
                }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.ContentCopy, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Copy")
                }
                FilledTonalButton(onClick = {
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(Intent.EXTRA_TEXT, "Join ${s.serverName} on Campfire: $url (same Wi-Fi only)")
                    context.startActivity(Intent.createChooser(send, "Share link"))
                }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Share, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Share")
                }
            }
            if (s.addresses.size > 1) {
                Text(
                    "Also reachable at " + s.addresses.drop(1).joinToString { it.url(s.port).removePrefix("http://") },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                )
            }
        }
        Button(onClick = onOpenChat, modifier = Modifier.fillMaxWidth().height(54.dp)) {
            Icon(Icons.Rounded.ChatBubble, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Open chat")
        }
        TextButton(onClick = onEnd, modifier = Modifier.align(Alignment.CenterHorizontally), colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
            Text("End Campfire")
        }
    }
}

@Composable
private fun Section(title: String, detail: String) {
    Row(Modifier.fillMaxWidth().padding(start = 6.dp, top = 12.dp), verticalAlignment = Alignment.Bottom) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 6.dp))
}

@Composable
private fun PersonRow(m: MemberInfo, onRemove: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surface).padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(Color(swatches[m.color % swatches.size])), contentAlignment = Alignment.Center) {
            Text(initials(m.name), color = Color.White, style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(m.name + if (m.isHost) " (you)" else "", style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (m.online) "Online" else "Away", style = MaterialTheme.typography.bodySmall, color = if (m.online) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!m.isHost) IconButton(onClick = onRemove) { Icon(Icons.Rounded.PersonRemove, contentDescription = "Remove ${m.name}") }
        else Spacer(Modifier.height(48.dp))
    }
}

@Composable
private fun RoomRow(r: RoomInfo, snapshot: ChatState.Snapshot, onClose: () -> Unit) {
    val online = r.members.count { id -> snapshot.members.any { it.id == id && it.online } }
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surface).padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Icon(if (r.locked) Icons.Rounded.Lock else Icons.Rounded.Tag, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(r.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${r.members.size} members · $online online", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (r.id != PUBLIC_ROOM) IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, contentDescription = "Close ${r.name}") }
        else Spacer(Modifier.height(48.dp))
    }
}

private fun initials(name: String): String {
    val words = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return (if (words.size > 1) "${words[0][0]}${words[1][0]}" else words.firstOrNull()?.take(2) ?: "?").uppercase()
}
