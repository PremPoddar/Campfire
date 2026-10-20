package com.prempoddar.campfire

import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import com.prempoddar.campfire.ui.theme.CAMPFIRE_BRAND
import com.prempoddar.campfire.ui.design.LayeredIcon
import com.prempoddar.campfire.ui.design.Credit
import com.prempoddar.campfire.ui.design.AppearanceScreen
import com.prempoddar.campfire.ui.design.AboutScreen
import com.prempoddar.campfire.ui.chat.ChatRoot
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.prempoddar.campfire.host.HostSettings
import com.prempoddar.campfire.ui.HomeScreen
import com.prempoddar.campfire.ui.SettingsScreen
import com.prempoddar.campfire.ui.theme.CampfireTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.prempoddar.campfire.ui.design.Haptics.attach(window.decorView)
        enableEdgeToEdge()
        setContent {
            val settings: HostSettings? by AppGraph.settings.settings.collectAsStateWithLifecycle(null)
            val loaded = settings ?: return@setContent
            CampfireTheme(loaded.appearance) {
                val nav = rememberNavController()
                NavHost(
                    nav, startDestination = "home",
                    enterTransition = { slideInHorizontally { it / 5 } + fadeIn() },
                    exitTransition = { fadeOut() },
                    popEnterTransition = { fadeIn() },
                    popExitTransition = { slideOutHorizontally { it / 5 } + fadeOut() },
                ) {
                    composable("home") { HomeScreen(onOpenChat = { nav.navigate("chat") }, onOpenSettings = { nav.navigate("settings") }) }
                    composable("chat") { ChatRoot(onBack = { nav.popBackStack() }) }
                    composable("settings") { SettingsScreen(onBack = { nav.popBackStack() }, onAppearance = { nav.navigate("appearance") }, onAbout = { nav.navigate("about") }) }
                    composable("appearance") {
                        val scope = rememberCoroutineScope()
                        AppearanceScreen(loaded.appearance, CAMPFIRE_BRAND, onChange = { a -> scope.launch { AppGraph.settings.update { it.copy(appearance = a) } } }, onBack = { nav.popBackStack() })
                    }
                    composable("about") {
                        AboutScreen(
                            "Campfire", "A private chat for everyone around you. Nothing leaves your network, and it's gone when you end it.",
                            icon = { LayeredIcon(R.drawable.ic_launcher_background, R.drawable.ic_launcher_foreground) },
                            credits = listOf(
                                Credit("Ktor", "Apache 2.0"), Credit("Preact and htm", "MIT"), Credit("ZXing", "Apache 2.0"),
                                Credit("Figtree", "SIL Open Font License"), Credit("kotlinx.serialization", "Apache 2.0"),
                            ),
                            onBack = { nav.popBackStack() },
                        )
                    }
                }
            }
        }
    }
}
