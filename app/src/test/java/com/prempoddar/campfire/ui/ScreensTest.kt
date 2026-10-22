package com.prempoddar.campfire.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.prempoddar.campfire.AppGraph
import com.prempoddar.campfire.client.ChatClient
import com.prempoddar.campfire.client.ChatConnection
import com.prempoddar.campfire.client.PUBLIC_CONV
import com.prempoddar.campfire.core.Attachment
import com.prempoddar.campfire.core.ConvSummary
import com.prempoddar.campfire.core.MemberInfo
import com.prempoddar.campfire.core.Message
import com.prempoddar.campfire.core.Receipt
import com.prempoddar.campfire.core.RoomInfo
import com.prempoddar.campfire.core.ServerEvent
import com.prempoddar.campfire.ui.chat.Conversation
import com.prempoddar.campfire.ui.chat.ConversationList
import com.prempoddar.campfire.ui.design.Appearance
import com.prempoddar.campfire.ui.theme.CampfireTheme
import io.ktor.client.HttpClient
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Draws real screens to build/screens to check light, dark and landscape without a phone. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
open class ScreensTest {
    /** Where the pictures go; the tablet runs below draw the same screens into their own folders. */
    protected open val out = "build/screens"

    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Before fun setUp() = AppGraph.init(ApplicationProvider.getApplicationContext())

    private fun shoot(name: String, appearance: Appearance, content: @Composable () -> Unit) {
        rule.mainClock.autoAdvance = false
        rule.setContent { CampfireTheme(appearance, content) }
        // Settings load from disk on another thread: give that real time, then step the clock.
        repeat(8) { Thread.sleep(100); rule.mainClock.advanceTimeBy(250) }
        val view = rule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(android.graphics.Canvas(bitmap)) }
        File(out).apply { mkdirs() }.resolve("$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private val light = Appearance(mode = Appearance.Mode.LIGHT)
    private val dark = Appearance(mode = Appearance.Mode.DARK)

    /** A chat with some history, fed through the same events the server sends. */
    private fun chat(): ChatClient {
        val me = MemberInfo("me", "Prem", 0, isHost = true, online = true)
        val asha = MemberInfo("asha", "Asha Rao", 5, isHost = false, online = true)
        val ben = MemberInfo("ben", "Ben", 2, isHost = false, online = true)
        return ChatClient { 10_000L }.also { c ->
            c.visible = true
            c.onEvent(
                ServerEvent.Welcome(
                    me, "t", "Friday night",
                    listOf(RoomInfo("public", "Public", false, null, listOf("me", "asha", "ben")), RoomInfo("games", "Board games", true, "asha", listOf("asha")), RoomInfo("food", "Food order", false, "me", listOf("me", "ben"))),
                    listOf(me, asha, ben), listOf(ConvSummary(PUBLIC_CONV, null, 0), ConvSummary("room:food", Message(9, "room:food", "ben", "Pizza?", 9), 2)),
                ),
            )
            c.onEvent(
                ServerEvent.History(
                    PUBLIC_CONV,
                    listOf(
                        Message(1, PUBLIC_CONV, "asha", "Is everyone here? 🔥", 1_000),
                        Message(2, PUBLIC_CONV, "asha", "I brought the speaker", 1_100, reactions = mapOf("❤️" to setOf("me", "ben"))),
                        Message(3, PUBLIC_CONV, "me", "Yes! Starting the playlist now", 1_200, replyTo = 1),
                        Message(4, PUBLIC_CONV, "ben", "Here's the menu https://example.org/menu", 1_300),
                        Message(5, PUBLIC_CONV, "ben", "", 1_400, attachment = Attachment("f", "menu.pdf", 240_000, "application/pdf")),
                        Message(6, PUBLIC_CONV, "me", "Perfect, thanks Ben", 1_500, editedAt = 1_600),
                    ),
                    listOf(Receipt("asha", 6), Receipt("ben", 5)),
                ),
            )
            c.onEvent(ServerEvent.Typing(PUBLIC_CONV, "asha"))
        }
    }

    private val connection get() = ChatConnection(ChatClient(), HttpClient(), "http://127.0.0.1:1", "x", { "Prem" })

    @Test fun homeLight() = shoot("home-light", light) { HomeScreen({}, {}) }
    @Test fun homeDark() = shoot("home-dark", dark) { HomeScreen({}, {}) }
    @Test fun settingsDark() = shoot("settings-dark", dark) { SettingsScreen({}, {}, {}) }
    @Test fun chatListDark() = chat().let { c -> shoot("chat-list-dark", dark) { ConversationList(c.state.value, {}, {}, {}, {}, {}, null) } }
    @Test fun conversationLight() = chat().let { c -> shoot("conversation-light", light) { Conversation(c.state.value, c, connection, PUBLIC_CONV, onBack = {}) } }
    @Test fun conversationDark() = chat().let { c -> shoot("conversation-dark", dark) { Conversation(c.state.value, c, connection, PUBLIC_CONV, onBack = {}) } }

    @Config(qualifiers = "w891dp-h411dp-land-xxhdpi")
    @Test fun homeLandscape() = shoot("home-land", dark) { HomeScreen({}, {}) }
}
