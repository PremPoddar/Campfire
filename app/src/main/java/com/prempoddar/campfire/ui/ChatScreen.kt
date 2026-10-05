package com.prempoddar.campfire.ui

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.net.Uri
import android.os.Environment
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.prempoddar.campfire.host.Host
import com.prempoddar.campfire.host.HostState

/** The host's own seat at the fire: the same web chat everyone else uses, signed in as host. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ChatScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val state by Host.state.collectAsStateWithLifecycle()
    val running = state as? HostState.Running
    LaunchedEffect(running == null) { if (running == null) onBack() }
    if (running == null) return

    val pending = remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val chooser = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        pending.value?.onReceiveValue(uris.toTypedArray())
        pending.value = null
    }
    BackHandler(onBack = onBack)

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().imePadding()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = true
                    settings.allowFileAccess = false
                    webViewClient = object : WebViewClient() {
                        // Links to other sites open in the browser, not inside Campfire.
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            val host = request.url.host
                            if (host == "127.0.0.1" || host == "localhost") return false
                            runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, request.url)) }
                            return true
                        }
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onShowFileChooser(
                            view: WebView,
                            callback: ValueCallback<Array<Uri>>,
                            params: FileChooserParams,
                        ): Boolean {
                            pending.value?.onReceiveValue(null)
                            pending.value = callback
                            chooser.launch("*/*")
                            return true
                        }
                    }
                    setDownloadListener { url, _, disposition, mime, _ ->
                        val name = URLUtil.guessFileName(url, disposition, mime)
                        val request = DownloadManager.Request(Uri.parse(url))
                            .setTitle(name)
                            .setMimeType(mime)
                            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
                        context.getSystemService(DownloadManager::class.java).enqueue(request)
                        Toast.makeText(context, "Saving $name to Downloads", Toast.LENGTH_SHORT).show()
                    }
                    loadUrl(running.localChatUrl)
                }
            },
            onRelease = { it.destroy() },
        )
    }
}
