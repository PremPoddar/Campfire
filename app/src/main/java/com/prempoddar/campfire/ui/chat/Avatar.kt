package com.prempoddar.campfire.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.prempoddar.campfire.client.ChatClient
import com.prempoddar.campfire.core.MemberInfo

/** A member's colour, the same one the browser shows them in. */
fun memberColor(index: Int): Color = Color(0xFF000000.toInt() or ChatClient.COLORS[(index % ChatClient.COLORS.size + ChatClient.COLORS.size) % ChatClient.COLORS.size])

/** Text that reads on [background]: near-black on light colours, white on dark ones. */
fun onColor(background: Color): Color = if (background.luminance() > 0.4f) Color(0xFF1B1B1B) else Color.White

fun initials(name: String): String {
    val words = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return (if (words.size > 1) "${words[0].first()}${words[1].first()}" else words.firstOrNull()?.take(2) ?: "?").uppercase()
}

@Composable
fun MemberAvatar(m: MemberInfo, size: Dp, modifier: Modifier = Modifier) {
    val c = memberColor(m.color)
    Box(modifier.size(size).clip(CircleShape).background(c), contentAlignment = Alignment.Center) {
        Text(initials(m.name), color = onColor(c), style = TextStyle(fontSize = (size.value * 0.38f).sp, fontWeight = FontWeight.SemiBold))
    }
}
