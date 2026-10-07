package com.prempoddar.campfire.ui.chat

import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.AddCircle
import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.prempoddar.campfire.AppGraph
import com.prempoddar.campfire.client.ChatClient
import com.prempoddar.campfire.client.ChatConnection
import com.prempoddar.campfire.client.ChatUi
import com.prempoddar.campfire.client.PUBLIC_CONV
import com.prempoddar.campfire.core.Attachment
import com.prempoddar.campfire.core.Conv
import com.prempoddar.campfire.core.Message
import com.prempoddar.campfire.core.RoomInfo
import com.prempoddar.campfire.ui.design.EmptyState
import com.prempoddar.campfire.ui.design.ExpressiveScaffold
import com.prempoddar.campfire.ui.design.IconTile
import com.prempoddar.campfire.ui.design.PillTextField
import com.prempoddar.campfire.ui.design.SectionLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val QUICK = listOf("❤️", "😂", "👍", "😮", "😢", "🔥")
private val URL = Regex("""https?://[^\s<>"]+""")

/** The host's chat: the list of conversations and the open one, side by side on wide screens. */
@Composable
fun ChatRoot(onBack: () -> Unit) {
    val session by AppGraph.chat.session.collectAsStateWithLifecycle()
    val s = session
    LaunchedEffect(s == null) { if (s == null) onBack() }
    if (s == null) return
    val client = s.client
    val ui by client.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LifecycleResumeEffect(client) {
        client.visible = true
        onPauseOrDispose { client.visible = false }
    }
    LaunchedEffect(ui.notice) { ui.notice?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show(); client.noticeShown() } }

    var showing by rememberSaveable { mutableStateOf(false) }
    // After asking to join a room or start a DM, follow the server's "opened" to it.
    var awaitingOpen by remember { mutableStateOf(false) }
    LaunchedEffect(ui.active) { if (awaitingOpen) { showing = true; awaitingOpen = false } }
    var newRoom by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf<RoomInfo?>(null) }

    fun openConv(conv: String) { client.open(conv); showing = true }
    fun joinOrOpen(room: RoomInfo) {
        val conv = Conv.room(room.id)
        when {
            ui.me?.id in room.members -> openConv(conv)
            room.locked -> password = room
            else -> { awaitingOpen = true; client.joinRoom(room.id) }
        }
    }

    if (ui.status == ChatUi.Status.ENDED || ui.status == ChatUi.Status.REMOVED) {
        ExpressiveScaffold("Chat", onBack = onBack) { pad ->
            EmptyState(Icons.Rounded.LocalFireDepartment, if (ui.status == ChatUi.Status.ENDED) "This Campfire has ended" else "You were removed", modifier = Modifier.padding(pad))
        }
        return
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        val wide = maxWidth >= 720.dp
        if (wide) {
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.width(360.dp).fillMaxHeight()) {
                    ConversationList(ui, onBack, onOpen = ::openConv, onRoom = ::joinOrOpen, onDm = { awaitingOpen = true; client.openDm(it) }, onNewRoom = { newRoom = true }, selected = ui.active)
                }
                VerticalDivider()
                Box(Modifier.weight(1f).fillMaxHeight()) { Conversation(ui, client, s.connection, ui.active, onBack = null) }
            }
        } else {
            if (showing) {
                BackHandler { showing = false }
                Conversation(ui, client, s.connection, ui.active, onBack = { showing = false })
            } else {
                ConversationList(ui, onBack, onOpen = ::openConv, onRoom = ::joinOrOpen, onDm = { awaitingOpen = true; client.openDm(it) }, onNewRoom = { newRoom = true }, selected = null)
            }
        }
    }

    if (newRoom) NewRoomDialog(onDismiss = { newRoom = false }) { name, pw -> newRoom = false; awaitingOpen = true; client.createRoom(name, pw) }
    password?.let { room ->
        PasswordDialog(room.name, ui.passwordError, onDismiss = { password = null }) { pw -> awaitingOpen = true; client.joinRoom(room.id, pw) }
        // Close the dialog once the server lets us in.
        LaunchedEffect(ui.active) { if (ui.active == Conv.room(room.id)) password = null }
    }
}

@Composable
internal fun ConversationList(
    ui: ChatUi, onBack: () -> Unit, onOpen: (String) -> Unit, onRoom: (RoomInfo) -> Unit, onDm: (String) -> Unit, onNewRoom: () -> Unit, selected: String?,
) {
    val me = ui.me?.id
    val joined = ui.rooms.filter { me in it.members }
    val others = ui.rooms.filter { me !in it.members }
    val dms = ui.convs.keys.filter { it.startsWith("dm:") }.sortedByDescending { ui.convs[it]?.messages?.lastOrNull()?.at ?: 0 }
    val people = ui.members.values.filter { it.id != me && it.online }.sortedBy { it.name }
    ExpressiveScaffold(
        ui.serverName, onBack = onBack,
        floatingActionButton = { ExtendedFloatingActionButton(onClick = onNewRoom, icon = { Icon(Icons.Rounded.Add, null) }, text = { Text("New room") }) },
    ) { pad ->
        LazyColumn(contentPadding = PaddingValues(top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding() + 96.dp)) {
            if (ui.status != ChatUi.Status.READY) item { ConnectingBanner() }
            item { SectionLabel("Rooms") }
            items(joined, key = { "j" + it.id }) { r ->
                val conv = Conv.room(r.id)
                ConvRow(
                    title = r.name, preview = previewOf(ui, conv), unread = ui.convs[conv]?.unread ?: 0, selected = selected == conv,
                    leading = { IconTile(if (r.locked) Icons.Rounded.Lock else Icons.Rounded.Tag) },
                ) { onOpen(conv) }
            }
            if (others.isNotEmpty()) {
                item { SectionLabel("Other rooms") }
                items(others, key = { "o" + it.id }) { r ->
                    ConvRow(
                        title = r.name, preview = if (r.locked) "Needs a password · ${r.members.size} in it" else "Tap to join · ${r.members.size} in it", unread = 0, selected = false,
                        leading = { IconTile(if (r.locked) Icons.Rounded.Lock else Icons.Rounded.Tag, container = MaterialTheme.colorScheme.secondaryContainer, tint = MaterialTheme.colorScheme.onSecondaryContainer) },
                    ) { onRoom(r) }
                }
            }
            if (dms.isNotEmpty()) {
                item { SectionLabel("Direct messages") }
                items(dms, key = { it }) { conv ->
                    val other = ui.dmOther(conv)?.let { ui.person(it) }
                    ConvRow(
                        title = other?.name ?: "Someone", preview = previewOf(ui, conv), unread = ui.convs[conv]?.unread ?: 0, selected = selected == conv,
                        leading = { if (other != null) MemberAvatar(other, 40.dp) },
                    ) { onOpen(conv) }
                }
            }
            if (people.isNotEmpty()) {
                item { SectionLabel("Here now") }
                items(people, key = { "p" + it.id }) { m ->
                    ConvRow(title = m.name, preview = "Send a direct message", unread = 0, selected = false, leading = { MemberAvatar(m, 40.dp) }) { onDm(m.id) }
                }
            }
        }
    }
}

private fun previewOf(ui: ChatUi, conv: String): String {
    val m = ui.convs[conv]?.messages?.lastOrNull() ?: return "No messages yet"
    val who = if (m.from == ui.me?.id) "You" else ui.person(m.from).name
    val what = when {
        m.deleted -> "message deleted"
        m.text.isNotBlank() -> m.text
        m.attachment != null -> "sent ${m.attachment.name}"
        else -> ""
    }
    return "$who: $what"
}

@Composable
private fun ConvRow(title: String, preview: String, unread: Int, selected: Boolean, leading: @Composable () -> Unit, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp).clip(MaterialTheme.shapes.large)
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                preview, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (unread > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (unread > 0) Badge(Modifier.padding(start = 8.dp)) { Text(if (unread > 99) "99+" else "$unread") }
    }
}

@Composable
private fun ConnectingBanner() {
    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.large, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Connecting to your Campfire…", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
        }
    }
}

/**
 * "Asha is typing…" in a slot that's always there, so the messages above never jump when it
 * comes and goes. Its once-a-second clock lives here so nothing else redraws with it.
 */
@Composable
private fun TypingLine(ui: ChatUi, conv: String) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(1_000); now = System.currentTimeMillis() } }
    val typing = ui.typingIn(conv, now)
    Text(
        if (typing.isEmpty()) "" else typing.joinToString(" and ") + if (typing.size == 1) " is typing…" else " are typing…",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth().height(20.dp).padding(horizontal = 20.dp),
    )
}

private class Upload(val id: Long, val name: String) { var progress by mutableStateOf(0f) }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun Conversation(ui: ChatUi, client: ChatClient, connection: ChatConnection, conv: String, onBack: (() -> Unit)?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = ui.convs[conv]
    val messages = state?.messages.orEmpty()
    val room = ui.room(conv)
    val title = ui.title(conv).ifEmpty { "Chat" }
    var text by rememberSaveable(conv) { mutableStateOf("") }
    var replyTo by remember(conv) { mutableStateOf<Message?>(null) }
    var editing by remember(conv) { mutableStateOf<Message?>(null) }
    var actionsFor by remember { mutableStateOf<Message?>(null) }
    var info by remember { mutableStateOf(false) }
    var viewing by remember { mutableStateOf<String?>(null) }
    val uploads = remember { mutableStateListOf<Upload>() }
    // Tell others we're typing, at most every few seconds.
    var lastTyping by remember { mutableLongStateOf(0L) }
    fun onText(v: String) {
        text = v
        if (v.isNotBlank() && System.currentTimeMillis() - lastTyping > 2_500) { lastTyping = System.currentTimeMillis(); client.typing() }
    }

    fun sendUris(uris: List<Uri>) {
        uris.forEach { uri ->
            val (name, size) = describe(context, uri)
            val up = Upload(System.nanoTime(), name)
            uploads += up
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
                        connection.upload(context.contentResolver.openInputStream(uri)!!, size, name, mime) { up.progress = it }
                    }
                }
                uploads.remove(up)
                result.onSuccess { client.sendMessage("", attachment = it, conv = conv) }
                    .onFailure { Toast.makeText(context, it.message ?: "$name couldn't be sent", Toast.LENGTH_SHORT).show() }
            }
        }
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris -> sendUris(uris) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(20)) { uris -> sendUris(uris) }
    // The camera app writes straight into a file we share with it.
    var shot by rememberSaveable { mutableStateOf<Uri?>(null) }
    fun newShot(ext: String): Uri {
        val dir = java.io.File(context.cacheDir, "camera").apply { mkdirs() }
        val stamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())
        return androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".camera", java.io.File(dir, "${if (ext == "mp4") "VID" else "IMG"}_$stamp.$ext")).also { shot = it }
    }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> shot?.let { if (ok) sendUris(listOf(it)) }; shot = null }
    val takeVideo = rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo()) { ok -> shot?.let { if (ok) sendUris(listOf(it)) }; shot = null }
    var attachMenu by remember { mutableStateOf(false) }

    fun submit() {
        val e = editing
        if (e != null) { client.edit(e.id, text); editing = null } else { client.sendMessage(text, replyTo?.id, conv = conv); replyTo = null }
        text = ""
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).imePadding()) {
        // Top bar
        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                else Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f).clickable(enabled = room != null) { info = true }.padding(vertical = 4.dp)) {
                    Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val sub = when {
                        room != null -> "${room.members.size} in this room · ${room.members.count { ui.members[it]?.online == true }} here"
                        else -> ui.dmOther(conv)?.let { if (ui.members[it]?.online == true) "Here now" else "Away" } ?: ""
                    }
                    if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (room != null) IconButton(onClick = { info = true }) { Icon(Icons.Rounded.Info, "About this room") }
            }
        }
        if (ui.status != ChatUi.Status.READY) LinearProgressIndicator(Modifier.fillMaxWidth())

        // Messages, newest at the bottom
        val list = rememberLazyListState()
        // Follow new messages when already at the bottom (or when it's your own), never yank someone reading back.
        val newestId = messages.lastOrNull()?.id
        LaunchedEffect(newestId) {
            if (newestId != null && (list.firstVisibleItemIndex <= 1 || messages.last().from == ui.me?.id)) list.animateScrollToItem(0)
        }
        val lastMineId = remember(messages, ui.me?.id) { messages.lastOrNull { it.from == ui.me?.id }?.id }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (messages.isEmpty() && state?.loaded == true) {
                EmptyState(Icons.Rounded.LocalFireDepartment, "No messages yet", "Say hello to everyone around the fire.", Modifier.align(Alignment.Center))
            }
            LazyColumn(state = list, reverseLayout = true, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)) {
                val reversed = messages.asReversed()
                items(reversed.size, key = { reversed[it].id }) { i ->
                    val m = reversed[i]
                    val older = reversed.getOrNull(i + 1)
                    val firstOfGroup = older == null || older.from != m.from || m.at - older.at > 5 * 60_000
                    MessageItem(
                        ui, m, conv, firstOfGroup, isLastMine = m.id == lastMineId,
                        messages = messages, connection = connection,
                        onLongPress = { actionsFor = m }, onReact = { e -> client.react(m.id, e) }, onImage = { viewing = it },
                    )
                }
            }
        }

        // Typing and uploads
        TypingLine(ui, conv)
        uploads.forEach { u ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(u.name, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                LinearProgressIndicator({ u.progress }, Modifier.width(120.dp).padding(start = 12.dp))
            }
        }

        // Reply or edit banner
        val banner = editing?.let { "Editing" to it } ?: replyTo?.let { "Replying to ${if (it.from == ui.me?.id) "yourself" else ui.person(it.from).name}" to it }
        if (banner != null) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp), modifier = Modifier.padding(horizontal = 12.dp)) {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(banner.first, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Text(banner.second.text.ifBlank { banner.second.attachment?.name ?: "" }, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { if (editing != null) { editing = null; text = "" } else replyTo = null }) { Icon(Icons.Rounded.Close, "Cancel") }
                }
            }
        }

        // Composer
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 8.dp).navigationBarsPadding(),
            verticalAlignment = Alignment.Bottom,
        ) {
            Box(Modifier.padding(bottom = 4.dp)) {
                IconButton(onClick = { attachMenu = true }) { Icon(Icons.Rounded.AddCircle, "Send a photo, video or file") }
                DropdownMenu(attachMenu, onDismissRequest = { attachMenu = false }) {
                    DropdownMenuItem(text = { Text("Gallery") }, leadingIcon = { Icon(Icons.Rounded.PhotoLibrary, null) }, onClick = {
                        attachMenu = false; gallery.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                    })
                    DropdownMenuItem(text = { Text("Take a photo") }, leadingIcon = { Icon(Icons.Rounded.PhotoCamera, null) }, onClick = {
                        attachMenu = false; runCatching { takePhoto.launch(newShot("jpg")) }.onFailure { Toast.makeText(context, "No camera app found", Toast.LENGTH_SHORT).show() }
                    })
                    DropdownMenuItem(text = { Text("Record a video") }, leadingIcon = { Icon(Icons.Rounded.Videocam, null) }, onClick = {
                        attachMenu = false; runCatching { takeVideo.launch(newShot("mp4")) }.onFailure { Toast.makeText(context, "No camera app found", Toast.LENGTH_SHORT).show() }
                    })
                    DropdownMenuItem(text = { Text("Any file") }, leadingIcon = { Icon(Icons.Rounded.AttachFile, null) }, onClick = { attachMenu = false; pick.launch("*/*") })
                }
            }
            PillTextField(
                text, ::onText, "Message", modifier = Modifier.weight(1f), singleLine = false, clearable = false,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
            FilledIconButton(onClick = ::submit, enabled = text.isNotBlank(), modifier = Modifier.padding(start = 6.dp, bottom = 4.dp).size(52.dp)) {
                Icon(if (editing != null) Icons.Rounded.Edit else Icons.AutoMirrored.Rounded.Send, if (editing != null) "Save edit" else "Send")
            }
        }
    }

    actionsFor?.let { m ->
        MessageActions(
            ui, m, room,
            onDismiss = { actionsFor = null },
            onReact = { e -> client.react(m.id, e); actionsFor = null },
            onReply = { replyTo = m; editing = null; actionsFor = null },
            onCopy = {
                context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Message", m.text))
                Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show(); actionsFor = null
            },
            onEdit = { editing = m; replyTo = null; text = m.text; actionsFor = null },
            onDelete = { client.delete(m.id); actionsFor = null },
            onSave = { m.attachment?.let { download(context, connection.fileUrl(it), it) }; actionsFor = null },
        )
    }
    if (info && room != null) RoomSheet(ui, room, client, onDismiss = { info = false })
    viewing?.let { url ->
        Dialog(onDismissRequest = { viewing = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().background(Color.Black).clickable { viewing = null }, contentAlignment = Alignment.Center) {
                RemoteImage(url, null, Modifier.fillMaxSize(), ContentScale.Fit)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun MessageItem(
    ui: ChatUi, m: Message, conv: String, firstOfGroup: Boolean, isLastMine: Boolean, messages: List<Message>, connection: ChatConnection,
    onLongPress: () -> Unit, onReact: (String) -> Unit, onImage: (String) -> Unit,
) {
    val context = LocalContext.current
    val mine = m.from == ui.me?.id
    val author = ui.person(m.from)
    val c = MaterialTheme.colorScheme
    val bubble = if (mine) c.primaryContainer else c.surfaceContainerHigh
    val onBubble = if (mine) c.onPrimaryContainer else c.onSurface
    Column(Modifier.fillMaxWidth().padding(top = if (firstOfGroup) 10.dp else 2.dp), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
        Row(verticalAlignment = Alignment.Bottom) {
            if (!mine) {
                if (firstOfGroup) MemberAvatar(author, 32.dp) else Spacer(Modifier.width(32.dp))
                Spacer(Modifier.width(8.dp))
            }
            Column(horizontalAlignment = if (mine) Alignment.End else Alignment.Start, modifier = Modifier.widthIn(max = 320.dp)) {
                if (firstOfGroup && !mine && conv.startsWith("room:")) {
                    Text(author.name, style = MaterialTheme.typography.labelMedium, color = memberColor(author.color).let { if (c.surface.luminanceIsDark()) it else it.darker() }, modifier = Modifier.padding(start = 12.dp, bottom = 2.dp))
                }
                Surface(
                    color = bubble, contentColor = onBubble,
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = if (mine) 20.dp else 6.dp, bottomEnd = if (mine) 6.dp else 20.dp),
                    modifier = Modifier.combinedClickable(onClick = {}, onLongClick = onLongPress),
                ) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 9.dp)) {
                        m.replyTo?.let { id ->
                            val quoted = messages.firstOrNull { it.id == id }
                            Row(Modifier.padding(bottom = 6.dp).clip(RoundedCornerShape(10.dp)).background(onBubble.copy(alpha = 0.08f)).padding(8.dp)) {
                                Box(Modifier.width(3.dp).height(32.dp).clip(CircleShape).background(onBubble.copy(alpha = 0.6f)))
                                Column(Modifier.padding(start = 8.dp)) {
                                    Text(quoted?.let { ui.person(it.from).name } ?: "Message", style = MaterialTheme.typography.labelMedium, color = onBubble)
                                    Text(quoted?.let { if (it.deleted) "Deleted" else it.text.ifBlank { it.attachment?.name ?: "" } } ?: "Not loaded", style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                        if (m.deleted) {
                            Text("This message was deleted", style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic), color = onBubble.copy(alpha = 0.7f))
                        } else {
                            m.attachment?.let { a -> AttachmentView(a, connection, onImage) { download(context, connection.fileUrl(a), a) } }
                            if (m.text.isNotBlank()) Text(remember(m.text, c.primary) { linkify(m.text, c.primary) }, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = if (m.attachment != null) 6.dp else 0.dp))
                            m.preview?.let { p ->
                                Column(Modifier.padding(top = 8.dp).clip(RoundedCornerShape(12.dp)).background(onBubble.copy(alpha = 0.06f)).clickable { open(context, p.url, null) }.padding(10.dp)) {
                                    p.imageId?.let { RemoteImage(connection.fileUrl(it), null, Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(8.dp))) }
                                    p.site?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = onBubble.copy(alpha = 0.7f), modifier = Modifier.padding(top = 6.dp)) }
                                    p.title?.let { Text(it, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                                    p.description?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis, color = onBubble.copy(alpha = 0.8f)) }
                                }
                            }
                        }
                        Text(
                            DateFormat.getTimeFormat(context).format(m.at) + if (m.editedAt != null && !m.deleted) " · edited" else "",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = onBubble.copy(alpha = 0.6f),
                            modifier = Modifier.align(Alignment.End).padding(top = 3.dp),
                        )
                    }
                }
                if (m.reactions.isNotEmpty()) {
                    FlowRow(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        m.reactions.forEach { (emoji, who) ->
                            val minePick = ui.me?.id in who
                            Surface(
                                onClick = { onReact(emoji) }, shape = CircleShape,
                                color = if (minePick) c.secondaryContainer else c.surfaceContainerHighest,
                                contentColor = if (minePick) c.onSecondaryContainer else c.onSurface,
                            ) { Text("$emoji ${who.size}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) }
                        }
                    }
                }
                if (isLastMine && !m.deleted) {
                    val receipts = ui.convs[conv]?.receipts.orEmpty().filterKeys { it != ui.me?.id }
                    val seen = receipts.count { it.value >= m.id }
                    val label = when {
                        conv.startsWith("dm:") -> if (seen > 0) "Seen" else "Sent"
                        seen > 0 -> "Seen by $seen"
                        else -> "Sent"
                    }
                    Text(label, style = MaterialTheme.typography.labelSmall, color = c.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp, end = 4.dp))
                }
            }
        }
    }
}

private fun Color.luminanceIsDark() = (0.2126f * red + 0.7152f * green + 0.0722f * blue) < 0.5f
private fun Color.darker() = Color(red * 0.7f, green * 0.7f, blue * 0.7f, alpha)

/** Links in a message, tappable, opening outside Campfire. */
private fun linkify(text: String, color: Color): AnnotatedString = buildAnnotatedString {
    var at = 0
    URL.findAll(text).forEach { m ->
        append(text.substring(at, m.range.first))
        val start = length
        append(m.value)
        addLink(LinkAnnotation.Url(m.value, TextLinkStyles(SpanStyle(color = color, textDecoration = TextDecoration.Underline))), start, length)
        at = m.range.last + 1
    }
    append(text.substring(at))
}

@Composable
private fun AttachmentView(a: Attachment, connection: ChatConnection, onImage: (String) -> Unit, onSave: () -> Unit) {
    val context = LocalContext.current
    val url = connection.fileUrl(a)
    when {
        a.mime.startsWith("image/") && !a.mime.contains("svg") ->
            RemoteImage(url, a.name, Modifier.widthIn(max = 280.dp).fillMaxWidth().heightIn(max = 280.dp).height(200.dp).clip(RoundedCornerShape(14.dp)).clickable { onImage(url) })
        else -> Row(
            Modifier.clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                .clickable { open(context, url, a.mime) }.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(if (a.mime.startsWith("video/") || a.mime.startsWith("audio/")) Icons.Rounded.PlayCircle else Icons.Rounded.InsertDriveFile, null, Modifier.size(32.dp))
            Column(Modifier.padding(horizontal = 10.dp).widthIn(max = 180.dp)) {
                Text(a.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(size(a.size), style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onSave) { Icon(Icons.Rounded.Download, "Save ${a.name}") }
        }
    }
}

private fun size(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.0f KB".format(bytes / 1024.0)
    bytes < 1024L * 1024 * 1024 -> "%.1f MB".format(bytes / 1048576.0)
    else -> "%.1f GB".format(bytes / 1073741824.0)
}

private fun open(context: Context, url: String, mime: String?) {
    val intent = Intent(Intent.ACTION_VIEW).apply { if (mime != null) setDataAndType(Uri.parse(url), mime) else data = Uri.parse(url) }
    runCatching { context.startActivity(intent) }.onFailure {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }
}

private fun download(context: Context, url: String, a: Attachment) {
    val request = DownloadManager.Request(Uri.parse(url)).setTitle(a.name).setMimeType(a.mime)
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, a.name)
    context.getSystemService(DownloadManager::class.java).enqueue(request)
    Toast.makeText(context, "Saving ${a.name} to Downloads", Toast.LENGTH_SHORT).show()
}

private fun describe(context: Context, uri: Uri): Pair<String, Long?> {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
        if (c.moveToFirst()) {
            val name = c.getString(0) ?: "file"
            val size = if (c.isNull(1)) null else c.getLong(1)
            return name to size
        }
    }
    return (uri.lastPathSegment ?: "file") to null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MessageActions(
    ui: ChatUi, m: Message, room: RoomInfo?, onDismiss: () -> Unit, onReact: (String) -> Unit, onReply: () -> Unit,
    onCopy: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit, onSave: () -> Unit,
) {
    val mine = m.from == ui.me?.id
    val canDelete = mine || ui.me?.isHost == true || ui.isAdmin(room)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 24.dp)) {
            if (!m.deleted) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    QUICK.forEach { e ->
                        Text(e, fontSize = 28.sp, modifier = Modifier.clip(CircleShape).clickable { onReact(e) }.padding(10.dp))
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                SheetAction(Icons.AutoMirrored.Rounded.Reply, "Reply", onReply)
                if (m.text.isNotBlank()) SheetAction(Icons.Rounded.ContentCopy, "Copy text", onCopy)
                if (m.attachment != null) SheetAction(Icons.Rounded.Download, "Save ${m.attachment.name}", onSave)
                if (mine) SheetAction(Icons.Rounded.Edit, "Edit", onEdit)
            }
            if (canDelete && !m.deleted) SheetAction(Icons.Rounded.Delete, "Delete for everyone", onDelete, danger = true)
        }
    }
}

@Composable
private fun SheetAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit, danger: Boolean = false) {
    val color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = color)
        Text(label, style = MaterialTheme.typography.titleMedium, color = color, modifier = Modifier.padding(start = 20.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoomSheet(ui: ChatUi, room: RoomInfo, client: ChatClient, onDismiss: () -> Unit) {
    val admin = ui.isAdmin(room)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(Modifier.padding(bottom = 24.dp)) {
            item {
                Row(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconTile(if (room.locked) Icons.Rounded.Lock else Icons.Rounded.Tag)
                    Column(Modifier.padding(start = 16.dp)) {
                        Text(room.name, style = MaterialTheme.typography.headlineSmall)
                        Text(if (room.locked) "Locked with a password" else "Open to everyone here", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item { SectionLabel("${room.members.size} in this room") }
            items(room.members, key = { it }) { id ->
                val m = ui.person(id)
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    MemberAvatar(m, 36.dp)
                    Text(m.name + if (id == ui.me?.id) " (you)" else "", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).padding(start = 14.dp))
                    if (admin && id != ui.me?.id) IconButton(onClick = { client.removeMember(room.id, id) }) { Icon(Icons.Rounded.PersonRemove, "Remove ${m.name}") }
                }
            }
            if (room.id != "public") {
                item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
                item { SheetAction(Icons.Rounded.Logout, "Leave room", { client.leaveRoom(room.id); client.open(PUBLIC_CONV); onDismiss() }) }
                if (admin) item { SheetAction(Icons.Rounded.Delete, "Close room for everyone", { client.closeRoom(room.id); onDismiss() }, danger = true) }
            }
        }
    }
}

@Composable
private fun NewRoomDialog(onDismiss: () -> Unit, onCreate: (String, String?) -> Unit) {
    var name by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New room") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PillTextField(name, { name = it.take(40) }, "Room name", keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
                PillTextField(password, { password = it.take(64) }, "Password (optional)", supporting = "Only people with it can join")
            }
        },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onCreate(name, password.ifEmpty { null }) }) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun PasswordDialog(roomName: String, error: String?, onDismiss: () -> Unit, onJoin: (String) -> Unit) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Lock, null) },
        title = { Text("Join $roomName") },
        text = {
            Column {
                Text("This room is locked. Ask whoever made it for the password.", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                androidx.compose.material3.TextField(
                    password, { password = it }, label = { Text("Password") }, singleLine = true, isError = error != null,
                    supportingText = error?.let { { Text(it) } }, visualTransformation = PasswordVisualTransformation(), shape = RoundedCornerShape(20.dp),
                    colors = androidx.compose.material3.TextFieldDefaults.colors(focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, errorIndicatorColor = Color.Transparent),
                )
            }
        },
        confirmButton = { TextButton(enabled = password.isNotEmpty(), onClick = { onJoin(password) }) { Text("Join") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
