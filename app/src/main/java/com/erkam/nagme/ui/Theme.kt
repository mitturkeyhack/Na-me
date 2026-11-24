package com.erkam.nagme.ui

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import com.erkam.nagme.data.Store
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Seçilebilir tema renkleri */
val THEME_COLORS = listOf(
    "Kehribar" to 0xFFF5B14C, "Turkuaz" to 0xFF5EC2B7, "Mavi" to 0xFF8EA8FF,
    "Mercan" to 0xFFF28C82, "Yeşil" to 0xFF8BD17C, "Lila" to 0xFFC3A6FF,
)

object C {
    val accentState = mutableStateOf(Color(0xFFF5B14C))
    val amoledState = mutableStateOf(false)
    val Bg get() = if (amoledState.value) Color.Black else Color(0xFF0F0F12)
    val Surface = Color(0xFF1B1B21)
    val Surface2 = Color(0xFF22222A)
    val Chip = Color(0xFF24242B)
    val Line = Color(0xFF2A2A31)
    val Text = Color(0xFFF4F4F5)
    val Muted = Color(0xFFA9A9B2)
    val Accent get() = accentState.value
    val OnAccent = Color(0xFF1A1206)
    val Covers = listOf(
        Color(0xFF2F5D5A), Color(0xFF8A4B2E), Color(0xFF3D4290),
        Color(0xFF6A6A2C), Color(0xFF6B3A5E), Color(0xFF36525F)
    )
}

fun coverColor(key: String) = C.Covers[(key.hashCode() and 0x7fffffff) % C.Covers.size]

object T {
    val H1 = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp)
    val H2 = TextStyle(fontSize = 21.sp, fontWeight = FontWeight.Bold)
    val Title = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    val Body = TextStyle(fontSize = 14.sp)
    val Small = TextStyle(fontSize = 12.5.sp)
}

@Composable
fun NagmeTheme(content: @Composable () -> Unit) {
    LaunchedEffect(Unit) {
        Store.settings.collect {
            C.accentState.value = Color(it.accent)
            C.amoledState.value = it.amoled
        }
    }
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = C.Accent,
            onPrimary = C.OnAccent,
            background = C.Bg,
            surface = C.Surface,
            surfaceContainer = C.Surface2,
            surfaceContainerHigh = C.Surface2,
            onSurface = C.Text,
            onBackground = C.Text,
            secondaryContainer = C.Chip,
        ),
    ) {
        // Renk verilmeyen bütün yazılar beyaz olsun
        CompositionLocalProvider(LocalContentColor provides C.Text, content = content)
    }
}
