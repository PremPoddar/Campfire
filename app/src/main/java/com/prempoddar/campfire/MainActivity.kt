package com.prempoddar.campfire

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.prempoddar.campfire.host.HostSettings
import com.prempoddar.campfire.ui.ChatScreen
import com.prempoddar.campfire.ui.HomeScreen
import com.prempoddar.campfire.ui.SettingsScreen
import com.prempoddar.campfire.ui.theme.CampfireTheme
import com.prempoddar.campfire.ui.theme.isDark

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings: HostSettings? by AppGraph.settings.settings.collectAsStateWithLifecycle(null)
            val loaded = settings ?: return@setContent
            val dark = isDark(loaded.theme)
            DisposableEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose { }
            }
            CampfireTheme(loaded.theme) {
                val nav = rememberNavController()
                NavHost(
                    nav, startDestination = "home",
                    enterTransition = { slideInHorizontally { it / 5 } + fadeIn() },
                    exitTransition = { fadeOut() },
                    popEnterTransition = { fadeIn() },
                    popExitTransition = { slideOutHorizontally { it / 5 } + fadeOut() },
                ) {
                    composable("home") { HomeScreen(onOpenChat = { nav.navigate("chat") }, onOpenSettings = { nav.navigate("settings") }) }
                    composable("chat") { ChatScreen(onBack = { nav.popBackStack() }) }
                    composable("settings") { SettingsScreen(onBack = { nav.popBackStack() }) }
                }
            }
        }
    }
}
