@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.prempoddar.campfire.ui

import com.prempoddar.campfire.ui.design.tap
import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.prempoddar.campfire.AppGraph
import com.prempoddar.campfire.R
import com.prempoddar.campfire.core.ChatState
import com.prempoddar.campfire.core.MemberInfo
import com.prempoddar.campfire.core.PUBLIC_ROOM
import com.prempoddar.campfire.core.RoomInfo
import com.prempoddar.campfire.host.CampfireService
import com.prempoddar.campfire.host.Host
import com.prempoddar.campfire.host.HostSettings
import com.prempoddar.campfire.host.HostState
import com.prempoddar.campfire.host.LanAddress
import com.prempoddar.campfire.host.NetworkInfo
import com.prempoddar.campfire.ui.chat.MemberAvatar
import com.prempoddar.campfire.ui.design.BigButton
import com.prempoddar.campfire.ui.design.IconTile
import com.prempoddar.campfire.ui.design.PillTextField
import com.prempoddar.campfire.ui.design.SettingsGroup
import com.prempoddar.campfire.ui.design.SettingsRow
import com.prempoddar.campfire.ui.design.TonalBigButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val s = settings ?: return

    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        val wide = maxWidth > maxHeight && maxWidth >= 600.dp
        val scroll = Modifier.verticalScroll(rememberScrollState())
        val pad = Modifier.padding(top = insets.calculateTopPadding(), bottom = insets.calculateBottomPadding() + 24.dp).padding(horizontal = 20.dp)
        if (running == null) {
            val stopped: @Composable ColumnScope.() -> Unit = {
                StoppedForm(s, starting = state == HostState.Starting, error = (state as? HostState.Failed)?.message, onStart = start)
            }
            if (wide) Row(Modifier.fillMaxSize().then(pad), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                Column(Modifier.weight(1f).then(scroll)) { Header(onOpenSettings); Spacer(Modifier.height(16.dp)); Hero(Modifier.fillMaxWidth()) }
                Column(Modifier.weight(1f).then(scroll).imePadding().padding(top = 24.dp), content = stopped)
            } else Column(Modifier.fillMaxSize().then(scroll).imePadding().then(pad).widthIn(max = 640.dp)) {
                Header(onOpenSettings)
                Spacer(Modifier.height(16.dp))
                Hero(Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                stopped()
            }
        } else {
            val snapshot by running.hub.snapshot.collectAsStateWithLifecycle()
            val chat by AppGraph.chat.session.collectAsStateWithLifecycle()
            val unread = chat?.client?.state?.collectAsStateWithLifecycle()?.value?.totalUnread ?: 0
            val manage: @Composable ColumnScope.() -> Unit = {
                People(snapshot) { confirm = Confirm.RemovePerson(it) }
                Rooms(snapshot) { confirm = Confirm.CloseRoom(it) }
                Spacer(Modifier.height(20.dp))
                OutlinedButton(
                    onClick = tap { confirm = Confirm.End }, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("End Campfire", style = MaterialTheme.typography.labelLarge) }
            }
            if (wide) Row(Modifier.fillMaxSize().then(pad), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                Column(Modifier.weight(1f).then(scroll)) { LiveHeader(running, onOpenSettings); Invite(running) }
                Column(Modifier.weight(1f).then(scroll).padding(top = 16.dp)) { Actions(running, snapshot, unread, onOpenChat); manage() }
            } else Column(Modifier.fillMaxSize().then(scroll).then(pad).widthIn(max = 640.dp)) {
                LiveHeader(running, onOpenSettings)
                Invite(running)
                Actions(running, snapshot, unread, onOpenChat)
                manage()
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
                TextButton(onClick = tap {
                    confirm = null
                    when (c) {
                        Confirm.End -> CampfireService.stop(context)
                        is Confirm.RemovePerson -> running?.hub?.let { hub -> scope.launch { hub.removePerson(c.member.id) } }
                        is Confirm.CloseRoom -> running?.hub?.let { hub -> scope.launch { hub.closeRoom(c.room.id) } }
                    }
                }) { Text(action, color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = tap { confirm = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Header(onOpenSettings: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Campfire", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        FilledTonalIconButton(onClick = tap(onOpenSettings), modifier = Modifier.size(48.dp)) { Icon(Icons.Rounded.Settings, "Settings") }
    }
}

/** The warm card that explains what Campfire is, with the fire from the icon. */
@Composable
private fun Hero(modifier: Modifier) {
    val c = MaterialTheme.colorScheme
    Box(
        modifier.clip(MaterialTheme.shapes.extraLarge)
            .background(Brush.linearGradient(listOf(c.primaryContainer, c.secondaryContainer)))
            .padding(24.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(end = 96.dp)) {
            Text("Light a campfire", style = MaterialTheme.typography.headlineMedium, color = c.onPrimaryContainer)
            Spacer(Modifier.height(6.dp))
            Text(
                "A private chat for everyone on your Wi-Fi or hotspot. They join from any browser. Nothing goes over the internet, and it's gone when you end it.",
                style = MaterialTheme.typography.bodyMedium, color = c.onPrimaryContainer.copy(alpha = 0.85f),
            )
        }
        Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.size(120.dp).align(Alignment.BottomEnd).offset(x = 20.dp, y = 22.dp))
    }
}

@Composable
private fun StoppedForm(s: HostSettings, starting: Boolean, error: String?, onStart: () -> Unit) {
    val scope = rememberCoroutineScope()
    var hostName by rememberSaveable { mutableStateOf(s.hostName) }
    var serverName by rememberSaveable { mutableStateOf(s.serverName) }
    // Until a name is typed here, the Campfire is named after its host.
    var custom by rememberSaveable { mutableStateOf(!s.serverNameAuto) }
    val addresses = remember(starting, error) { NetworkInfo.addresses() }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PillTextField(
            hostName, { v ->
                hostName = v.take(32)
                if (!custom) serverName = HostSettings.autoName(hostName)
                scope.launch { AppGraph.settings.update { it.copy(hostName = v.trim().take(32)) } }
            },
            "Your name", supporting = "What people see next to your messages", leading = Icons.Rounded.Person,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
        )
        PillTextField(
            serverName, { v ->
                serverName = v.take(40)
                custom = v.isNotBlank() && v.trim() != HostSettings.autoName(hostName)
                scope.launch { AppGraph.settings.update { it.copy(serverName = v.trim().take(40), serverNameAuto = !custom) } }
            },
            "Campfire name", supporting = "Shown to people when they join", leading = Icons.Rounded.LocalFireDepartment,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )
        NetworkCard(addresses)
        if (error != null) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 8.dp))
        BigButton(
            if (starting) "Starting…" else "Start", onClick = onStart, enabled = !starting,
            icon = Icons.Rounded.LocalFireDepartment, modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** Which network people can join on, or what to do when there isn't one. */
@Composable
private fun NetworkCard(addresses: List<LanAddress>) {
    val first = addresses.firstOrNull()
    val c = MaterialTheme.colorScheme
    Surface(shape = MaterialTheme.shapes.large, color = if (first == null) c.errorContainer else c.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (first == null) IconTile(Icons.Rounded.WifiOff, container = c.error, tint = c.onError)
            else IconTile(if (first.kind == LanAddress.Kind.HOTSPOT) Icons.Rounded.WifiTethering else Icons.Rounded.Wifi)
            Column(Modifier.padding(start = 16.dp)) {
                if (first == null) {
                    Text("No network yet", style = MaterialTheme.typography.titleMedium, color = c.onErrorContainer)
                    Text("Join a Wi-Fi network or turn on your hotspot. Anyone on the same network can join.", style = MaterialTheme.typography.bodyMedium, color = c.onErrorContainer)
                } else {
                    Text(kindLabel(first.kind), style = MaterialTheme.typography.titleMedium, color = c.onSurface)
                    Text(
                        "People on the same network can join · ${first.ip}" + if (addresses.size > 1) " and ${addresses.size - 1} more" else "",
                        style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun kindLabel(kind: LanAddress.Kind) = when (kind) {
    LanAddress.Kind.HOTSPOT -> "On your hotspot"
    LanAddress.Kind.WIFI -> "On Wi-Fi"
    LanAddress.Kind.OTHER -> "On the local network"
}

@Composable
private fun LiveHeader(running: HostState.Running, onOpenSettings: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(20_000); now = System.currentTimeMillis() } }
    val minutes = ((now - running.startedAt) / 60_000).coerceAtLeast(0)
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(running.serverName, style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF3BB273)))
                Spacer(Modifier.width(6.dp))
                Text(if (minutes < 1) "Live · just started" else "Live · $minutes min", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        FilledTonalIconButton(onClick = tap(onOpenSettings), modifier = Modifier.size(48.dp)) { Icon(Icons.Rounded.Settings, "Settings") }
    }
}

/** The QR code and link people open; with several networks, a chip per network. */
@Composable
private fun Invite(running: HostState.Running) {
    val context = LocalContext.current
    var pick by remember(running.addresses) { mutableStateOf(0) }
    val address = running.addresses.getOrNull(pick)
    Spacer(Modifier.height(16.dp))
    if (address == null) {
        NetworkCard(emptyList())
        return
    }
    val url = address.url(running.port)
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (running.addresses.size > 1) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    running.addresses.forEachIndexed { i, a -> FilterChip(i == pick, { pick = i }, label = { Text(kindLabel(a.kind)) }) }
                }
                Spacer(Modifier.height(8.dp))
            }
            val qr = remember(url) { qrCode(url) }
            // QR codes need dark-on-light to scan, so the code always sits on white.
            Box(Modifier.widthIn(max = 260.dp).fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(24.dp)).background(Color.White).padding(18.dp)) {
                Image(qr, contentDescription = "QR code for $url", modifier = Modifier.fillMaxSize())
            }
            Spacer(Modifier.height(14.dp))
            Text(url.removePrefix("http://"), style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight(750)), color = MaterialTheme.colorScheme.onSurface)
            Text(
                "Scan, or open the link in any browser on the same network", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
            )
            IconButton(onClick = tap {
                context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Campfire link", url))
                Toast.makeText(context, "Link copied", Toast.LENGTH_SHORT).show()
            }) { Icon(Icons.Rounded.ContentCopy, "Copy link") }
        }
    }
}

@Composable
private fun Actions(running: HostState.Running, snapshot: ChatState.Snapshot, unread: Int, onOpenChat: () -> Unit) {
    val context = LocalContext.current
    val here = snapshot.members.filter { it.online }
    Spacer(Modifier.height(16.dp))
    if (here.isNotEmpty()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy((-10).dp)) {
                here.take(5).forEach { m -> MemberAvatar(m, 34.dp, Modifier.border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)) }
            }
            Spacer(Modifier.width(12.dp))
            Text("${here.size} by the fire", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        }
    }
    Box {
        BigButton("Open chat", onClick = onOpenChat, icon = Icons.AutoMirrored.Rounded.Chat)
        if (unread > 0) Badge(Modifier.align(Alignment.TopEnd).padding(top = 4.dp, end = 12.dp)) { Text("$unread") }
    }
    Spacer(Modifier.height(10.dp))
    TonalBigButton("Share link", icon = Icons.Rounded.Share, onClick = {
        val url = running.primaryUrl ?: return@TonalBigButton
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "Join ${running.serverName} on Campfire: $url (same Wi-Fi only)")
        context.startActivity(Intent.createChooser(send, "Share link"))
    })
}

@Composable
private fun People(snapshot: ChatState.Snapshot, onRemove: (MemberInfo) -> Unit) {
    val people = snapshot.members.filter { !it.isHost || it.online }.sortedWith(compareByDescending<MemberInfo> { it.online }.thenBy { it.name })
    SettingsGroup("People · ${snapshot.members.count { it.online }} here", Modifier.padding(top = 8.dp)) {
        if (people.isEmpty()) SettingsRow("Nobody has joined yet", "Share the link or let people scan the code")
        people.forEach { m ->
            Row(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MemberAvatar(m, 40.dp)
                Column(Modifier.weight(1f).padding(start = 16.dp)) {
                    Text(m.name + if (m.isHost) " (you)" else "", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (m.online) "Here now" else "Away", style = MaterialTheme.typography.bodyMedium, color = if (m.online) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (!m.isHost) IconButton(onClick = tap { onRemove(m) }) { Icon(Icons.Rounded.PersonRemove, "Remove ${m.name}") }
            }
        }
    }
}

@Composable
private fun Rooms(snapshot: ChatState.Snapshot, onClose: (RoomInfo) -> Unit) {
    SettingsGroup("Rooms · ${snapshot.rooms.size}") {
        snapshot.rooms.forEach { r ->
            val online = r.members.count { id -> snapshot.members.any { it.id == id && it.online } }
            SettingsRow(r.name, "${r.members.size} members · $online here", if (r.locked) Icons.Rounded.Lock else Icons.Rounded.Tag) {
                if (r.id != PUBLIC_ROOM) IconButton(onClick = tap { onClose(r) }) { Icon(Icons.Rounded.Close, "Close ${r.name}") }
            }
        }
    }
}
