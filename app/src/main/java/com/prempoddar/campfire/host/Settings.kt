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
) {
    companion object {
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

    /** "Pixel 8's Campfire", from the phone's own name. */
    private val defaultName: String by lazy {
        val device = SystemSettings.Global.getString(context.contentResolver, SystemSettings.Global.DEVICE_NAME)
            ?.takeIf { it.isNotBlank() } ?: android.os.Build.MODEL
        "$device's Campfire".take(40)
    }

    val settings: Flow<HostSettings> = context.dataStore.data.map { it.toSettings() }

    suspend fun update(transform: (HostSettings) -> HostSettings) {
        context.dataStore.edit { p ->
            val next = transform(p.toSettings())
            p[Keys.name] = next.serverName
            p[Keys.port] = next.port
            p[Keys.theme] = next.appearance.mode.name
            p[Keys.dynamic] = next.appearance.dynamic
            p[Keys.black] = next.appearance.pureBlack
            next.appearance.accent?.let { p[Keys.accent] = it } ?: p.remove(Keys.accent)
            p[Keys.hostName] = next.hostName
        }
    }

    private fun Preferences.toSettings() = HostSettings(
        serverName = this[Keys.name]?.takeIf { it.isNotBlank() } ?: defaultName,
        port = this[Keys.port]?.takeIf { it in HostSettings.PORTS } ?: HostSettings.DEFAULT_PORT,
        appearance = Appearance(
            mode = this[Keys.theme]?.let { runCatching { Appearance.Mode.valueOf(it) }.getOrNull() } ?: Appearance.Mode.SYSTEM,
            dynamic = this[Keys.dynamic] ?: false,
            accent = this[Keys.accent],
            pureBlack = this[Keys.black] ?: false,
        ),
        hostName = this[Keys.hostName].orEmpty(),
    )
}
