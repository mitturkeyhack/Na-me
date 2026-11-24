package com.erkam.nagme.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.erkam.nagme.R

@Composable
fun NagmeApp(vm: MainViewModel) {
    val tab by vm.tab.collectAsState()
    val showPlayer by vm.showPlayer.collectAsState()
    val showSettings by vm.showSettings.collectAsState()
    val col by vm.openCollection.collectAsState()
    val current by vm.current.collectAsState()
    val msg by vm.message.collectAsState()
    val snack = remember { SnackbarHostState() }

    LaunchedEffect(msg) {
        msg?.let { snack.showSnackbar(it); vm.message.value = null }
    }

    BackHandler(showPlayer || showSettings || col != null || tab != Tab.Home) {
        when {
            showPlayer -> vm.showPlayer.value = false
            showSettings -> vm.showSettings.value = false
            col != null -> vm.openCollection.value = null
            else -> vm.tab.value = Tab.Home
        }
    }

    NagmeTheme {
        Box(Modifier.fillMaxSize().background(C.Bg)) {
            val bottomPad = PaddingValues(bottom = if (current != null) 150.dp else 86.dp)
            Box(Modifier.fillMaxSize().statusBarsPadding()) {
                when (tab) {
                    Tab.Home -> HomeScreen(vm, bottomPad)
                    Tab.Search -> SearchScreen(vm, bottomPad)
                    Tab.Library -> LibraryScreen(vm, bottomPad)
                }
                AnimatedVisibility(
                    col != null,
                    enter = slideInHorizontally(tween(220)) { it / 3 } + fadeIn(tween(220)),
                    exit = slideOutHorizontally(tween(180)) { it / 3 } + fadeOut(tween(180))
                ) {
                    col?.let { CollectionScreen(vm, it, bottomPad) }
                }
            }

            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(
                androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, C.Bg, C.Bg))
            ).navigationBarsPadding()) {
                if (current != null) MiniPlayer(vm)
                BottomBar(tab) { vm.openCollection.value = null; vm.tab.value = it }
            }

            SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).padding(bottom = 160.dp))

            AnimatedVisibility(
                showSettings,
                enter = slideInHorizontally(tween(240)) { it } ,
                exit = slideOutHorizontally(tween(200)) { it }
            ) { SettingsScreen(vm) }

            AnimatedVisibility(
                showPlayer && current != null,
                enter = slideInVertically(tween(280)) { it },
                exit = slideOutVertically(tween(240)) { it }
            ) {
                Box(Modifier.pointerInput(Unit) {
                    detectVerticalDragGestures { _, drag -> if (drag > 40) vm.showPlayer.value = false }
                }) { PlayerScreen(vm) }
            }

            AddToPlaylistDialog()
        }
    }
}

@Composable
private fun MiniPlayer(vm: MainViewModel) {
    val s by vm.current.collectAsState()
    val playing by vm.isPlaying.collectAsState()
    val pos by vm.position.collectAsState()
    val dur by vm.duration.collectAsState()
    val song = s ?: return
    Column(
        Modifier.padding(horizontal = 8.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.Surface2)
            .clickable { vm.showPlayer.value = true }
            .pointerInput(Unit) { detectVerticalDragGestures { _, drag -> if (drag < -30) vm.showPlayer.value = true } }
    ) {
        Box(Modifier.fillMaxWidth().height(3.dp).background(Color(0xFF3A3A44))) {
            val f = if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f
            Box(Modifier.fillMaxWidth(f).height(3.dp).background(C.Accent))
        }
        Row(Modifier.padding(start = 8.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center) {
                Cover(song, 44.dp)
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(Color(0x66000000)))
                EqBars(playing, height = 14.dp)
            }
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(song.title, style = T.Title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song.artist, style = T.Small, color = C.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconBtn(if (playing) R.drawable.ic_pause else R.drawable.ic_play, if (playing) "Duraklat" else "Çal", { vm.toggle() }, size = 26.dp)
            IconBtn(R.drawable.ic_next, "Sonraki", { vm.next() })
        }
    }
}

@Composable
private fun BottomBar(tab: Tab, onSelect: (Tab) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 6.dp)) {
        NavItem("Ana Sayfa", if (tab == Tab.Home) R.drawable.ic_home else R.drawable.ic_home_outline, tab == Tab.Home, Modifier.weight(1f)) { onSelect(Tab.Home) }
        NavItem("Ara", R.drawable.ic_search, tab == Tab.Search, Modifier.weight(1f)) { onSelect(Tab.Search) }
        NavItem("Kitaplık", R.drawable.ic_library, tab == Tab.Library, Modifier.weight(1f)) { onSelect(Tab.Library) }
    }
}

@Composable
private fun NavItem(label: String, icon: Int, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clickable(onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Ic(icon, null, tint = if (selected) C.Text else C.Muted)
        Spacer(Modifier.height(3.dp))
        Text(label, style = T.Small.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal),
            color = if (selected) C.Text else C.Muted)
    }
}

