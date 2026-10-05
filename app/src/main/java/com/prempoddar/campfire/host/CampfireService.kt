package com.prempoddar.campfire.host

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.prempoddar.campfire.AppGraph
import com.prempoddar.campfire.MainActivity
import com.prempoddar.campfire.R
import com.prempoddar.campfire.server.CampfireServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.BindException

/** Keeps the chat server alive while the host uses other apps, with a notification to stop it. */
class CampfireService : LifecycleService() {

    private var server: CampfireServer? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var watcher: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_STOP -> stop()
            else -> start()
        }
        return START_NOT_STICKY
    }

    private fun start() {
        if (server != null) return
        Host.mutable.value = HostState.Starting
        promote(getString(R.string.notification_starting))
        lifecycleScope.launch {
            val settings = AppGraph.settings.settings.first()
            val s = CampfireServer(settings.port, settings.serverName, File(cacheDir, "campfire-files"))
            val failure = withContext(Dispatchers.IO) {
                runCatching { s.start() }.exceptionOrNull()
            }
            if (failure != null) {
                Host.mutable.value = HostState.Failed(
                    if (failure is BindException || failure.cause is BindException) {
                        "Port ${settings.port} is already in use. Pick another port in Settings."
                    } else {
                        "Campfire couldn't start: ${failure.message ?: failure.javaClass.simpleName}"
                    },
                )
                shutdown()
                return@launch
            }
            server = s
            acquireLocks()
            val running = HostState.Running(settings.serverName, settings.port, NetworkInfo.addresses(), s.hub)
            Host.mutable.value = running
            promote(running.primaryUrl ?: getString(R.string.notification_no_network))
            // Wi-Fi can change while the server runs; keep the shown link current.
            watcher = lifecycleScope.launch {
                while (isActive) {
                    delay(3_000)
                    val current = Host.state.value as? HostState.Running ?: break
                    val now = NetworkInfo.addresses()
                    if (now != current.addresses) {
                        val next = current.copy(addresses = now)
                        Host.mutable.value = next
                        promote(next.primaryUrl ?: getString(R.string.notification_no_network))
                    }
                }
            }
        }
    }

    private fun stop() {
        val s = server
        server = null
        watcher?.cancel()
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { s?.stop() }
            Host.mutable.value = HostState.Stopped
            shutdown()
        }
    }

    private fun shutdown() {
        releaseLocks()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        server?.let { s -> runCatching { s.stop() } }
        server = null
        if (Host.state.value !is HostState.Failed) Host.mutable.value = HostState.Stopped
        releaseLocks()
        super.onDestroy()
    }

    private fun promote(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW).apply {
                description = getString(R.string.channel_description)
                setShowBadge(false)
            },
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, CampfireService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE,
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, getString(R.string.notification_stop), stop)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    private fun acquireLocks() {
        val wifi = applicationContext.getSystemService(WifiManager::class.java)
        wifiLock = wifi.createWifiLock(WifiManager.WIFI_MODE_FULL_LOW_LATENCY, "Campfire:wifi").apply {
            setReferenceCounted(false)
            acquire()
        }
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Campfire:server").apply {
                setReferenceCounted(false)
                acquire()
            }
    }

    private fun releaseLocks() {
        wifiLock?.takeIf { it.isHeld }?.release()
        wakeLock?.takeIf { it.isHeld }?.release()
        wifiLock = null
        wakeLock = null
    }

    companion object {
        private const val CHANNEL = "campfire"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_START = "com.prempoddar.campfire.START"
        private const val ACTION_STOP = "com.prempoddar.campfire.STOP"

        fun start(context: Context) {
            context.startForegroundService(Intent(context, CampfireService::class.java).setAction(ACTION_START))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, CampfireService::class.java).setAction(ACTION_STOP))
        }
    }
}
