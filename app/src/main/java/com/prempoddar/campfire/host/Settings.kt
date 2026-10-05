package com.prempoddar.campfire.host

import android.content.Context
import android.provider.Settings as SystemSettings
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class HostSettings(
    val serverName: String,
    val port: Int = DEFAULT_PORT,
    val theme: ThemeMode = ThemeMode.SYSTEM,
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
            p[Keys.theme] = next.theme.name
        }
    }

    private fun Preferences.toSettings() = HostSettings(
        serverName = this[Keys.name]?.takeIf { it.isNotBlank() } ?: defaultName,
        port = this[Keys.port]?.takeIf { it in HostSettings.PORTS } ?: HostSettings.DEFAULT_PORT,
        theme = this[Keys.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
    )
}
