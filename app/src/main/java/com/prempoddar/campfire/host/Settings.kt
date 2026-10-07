package com.prempoddar.campfire.host

import android.content.Context
import android.provider.Settings as SystemSettings
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import com.prempoddar.campfire.ui.design.Appearance
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class HostSettings(
    val serverName: String,
    val port: Int = DEFAULT_PORT,
    val appearance: Appearance = Appearance(),
    /** What the host is called in the chat. */
    val hostName: String = "",
    /** True while the name follows the host's ("Prem's Campfire") rather than one they typed. */
    val serverNameAuto: Boolean = true,
) {
    companion object {
        /** "Prem's Campfire", or just "Campfire" before a name is set. */
        fun autoName(hostName: String): String = hostName.trim().takeIf { it.isNotEmpty() }?.let { "$it's Campfire".take(40) } ?: "Campfire"

        const val DEFAULT_PORT = 8080
        val PORTS = 1024..65535
    }
}

private val Context.dataStore by preferencesDataStore("settings")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val name = stringPreferencesKey("name")
        val port = intPreferencesKey("port")
        val theme = stringPreferencesKey("theme")
        val dynamic = booleanPreferencesKey("dynamic")
        val accent = intPreferencesKey("accent")
        val black = booleanPreferencesKey("pure_black")
        val hostName = stringPreferencesKey("host_name")
    }

    /** What older versions saved by default ("Pixel 8's Campfire"); treated as not chosen. */
    private val deviceName: String by lazy {
        val device = SystemSettings.Global.getString(context.contentResolver, SystemSettings.Global.DEVICE_NAME)
            ?.takeIf { it.isNotBlank() } ?: android.os.Build.MODEL
        "$device's Campfire".take(40)
    }

    val settings: Flow<HostSettings> = context.dataStore.data.map { it.toSettings() }

    suspend fun update(transform: (HostSettings) -> HostSettings) {
        context.dataStore.edit { p ->
            val next = transform(p.toSettings())
            // Only a name someone chose is kept; otherwise it follows their name.
            if (next.serverNameAuto || next.serverName.isBlank() || next.serverName == HostSettings.autoName(next.hostName)) p.remove(Keys.name) else p[Keys.name] = next.serverName
            p[Keys.port] = next.port
            p[Keys.theme] = next.appearance.mode.name
            p[Keys.dynamic] = next.appearance.dynamic
            p[Keys.black] = next.appearance.pureBlack
            next.appearance.accent?.let { p[Keys.accent] = it } ?: p.remove(Keys.accent)
            p[Keys.hostName] = next.hostName
        }
    }

    private fun Preferences.toSettings(): HostSettings {
        val chosen = this[Keys.name]?.takeIf { it.isNotBlank() && it != deviceName }
        val host = this[Keys.hostName].orEmpty()
        return HostSettings(
        serverName = chosen ?: HostSettings.autoName(host),
        serverNameAuto = chosen == null,
        port = this[Keys.port]?.takeIf { it in HostSettings.PORTS } ?: HostSettings.DEFAULT_PORT,
        appearance = Appearance(
            mode = this[Keys.theme]?.let { runCatching { Appearance.Mode.valueOf(it) }.getOrNull() } ?: Appearance.Mode.SYSTEM,
            dynamic = this[Keys.dynamic] ?: false,
            accent = this[Keys.accent],
            pureBlack = this[Keys.black] ?: false,
        ),
        hostName = host,
    )
    }
}
