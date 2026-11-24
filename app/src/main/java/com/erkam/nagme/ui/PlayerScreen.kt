@file:OptIn(ExperimentalMaterial3Api::class)

package com.erkam.nagme.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.palette.graphics.Palette
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.erkam.nagme.R
import com.erkam.nagme.data.Downloads
import com.erkam.nagme.data.Store
import com.erkam.nagme.data.formatTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun PlayerScreen(vm: MainViewModel) {
    val song by vm.current.collectAsState()
    val playing by vm.isPlaying.collectAsState()
    val buffering by vm.buffering.collectAsState()
    val pos by vm.position.collectAsState()
    val dur by vm.duration.collectAsState()
    val shuffle by vm.shuffle.collectAsState()
    val repeat by vm.repeat.collectAsState()
    val liked by Store.liked.collectAsState()
    val downloads by Store.downloads.collectAsState()
    val progress by Downloads.progress.collectAsState()
    val sleepUntil by vm.sleepUntil.collectAsState()
    val ctx = LocalContext.current

    var base by remember { mutableStateOf(Color(0xFF14211F)) }
    LaunchedEffect(song?.thumb) {
        val t = song?.thumb ?: return@LaunchedEffect
        if (t.isEmpty()) return@LaunchedEffect
        val r = SingletonImageLoader.get(ctx).execute(
            ImageRequest.Builder(ctx).data(t).size(128).allowHardware(false).build()
        )
        if (r is SuccessResult) {
            val p = withContext(Dispatchers.Default) { Palette.from(r.image.toBitmap()).generate() }
            val c = p.darkVibrantSwatch?.rgb ?: p.darkMutedSwatch?.rgb ?: p.dominantSwatch?.rgb
            if (c != null) base = lerp(Color(c), Color(0xFF0F0F12), 0.45f)
        }
    }
    val bg by animateColorAsState(base, tween(600), label = "bg")

    var showQueue by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf<Float?>(null) }

    val s = song
    Column(
        Modifier
            .fillMaxSize()
            .background(bg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.padding(start = 0.dp)) {
                IconBtn(R.drawable.ic_chevron_down, "Oynatıcıyı küçült", { vm.showPlayer.value = false }, size = 28.dp)
            }
            Text("ŞİMDİ ÇALIYOR", style = T.Small, color = Color(0xFFD4D4D8), modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            if (s != null) SongMenu(s, vm) else Spacer(Modifier.size(44.dp))
        }

        Spacer(Modifier.weight(0.6f))
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(18.dp)).background(coverColor(s?.id ?: "")),
            contentAlignment = Alignment.Center
        ) {
            if (s != null) Cover(s, 400.dp, 18.dp, Modifier.fillMaxSize())
            if (buffering) CircularProgressIndicator(color = Color.White)
        }
        Spacer(Modifier.weight(0.5f))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(s?.title ?: "", style = T.H1, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(s?.artist ?: "", style = T.Body, color = Color(0xFFCBD0D4), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (s != null) {
                val isLiked = liked.any { it.id == s.id }
                IconBtn(if (isLiked) R.drawable.ic_heart_fill else R.drawable.ic_heart,
                    if (isLiked) "Beğenilenlerden çıkar" else "Beğen",
                    { Store.toggleLike(s) }, tint = if (isLiked) C.Accent else C.Text, size = 26.dp)
            }
        }

        val max = dur.coerceAtLeast(1L).toFloat()
        Slider(
            value = (dragging ?: pos.toFloat()).coerceIn(0f, max),
            onValueChange = { dragging = it },
            onValueChangeFinished = { dragging?.let { vm.seek(it.toLong()) }; dragging = null },
            valueRange = 0f..max,
            colors = SliderDefaults.colors(
                thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color(0x38FFFFFF)
            ),
            modifier = Modifier.padding(top = 12.dp)
        )
        Row {
            Text(formatTime(((dragging ?: pos.toFloat()) / 1000).toLong()), style = T.Small, color = Color(0xFFCBD0D4))
            Spacer(Modifier.weight(1f))
            Text(formatTime(dur / 1000), style = T.Small, color = Color(0xFFCBD0D4))
        }

        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBtn(R.drawable.ic_shuffle, "Karıştır", { vm.toggleShuffle() }, tint = if (shuffle) C.Accent else Color(0xFFD4D4D8), box = 48.dp)
            IconBtn(R.drawable.ic_prev, "Önceki", { vm.prev() }, size = 34.dp, box = 56.dp)
            Box(
                Modifier.size(76.dp).clip(CircleShape).background(Color.White).clickable { vm.toggle() },
                contentAlignment = Alignment.Center
            ) {
                Ic(if (playing) R.drawable.ic_pause else R.drawable.ic_play, if (playing) "Duraklat" else "Çal", tint = Color(0xFF14211F), size = 34.dp)
            }
            IconBtn(R.drawable.ic_next, "Sonraki", { vm.next() }, size = 34.dp, box = 56.dp)
            IconBtn(
                if (repeat == Player.REPEAT_MODE_ONE) R.drawable.ic_repeat_one else R.drawable.ic_repeat,
                when (repeat) { Player.REPEAT_MODE_ALL -> "Tekrar: hepsi"; Player.REPEAT_MODE_ONE -> "Tekrar: bu şarkı"; else -> "Tekrar kapalı" },
                { vm.cycleRepeat() }, tint = if (repeat != Player.REPEAT_MODE_OFF) C.Accent else Color(0xFFD4D4D8), box = 48.dp
            )
        }

        Spacer(Modifier.weight(0.6f))
        Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BottomAction("Sıradakiler", R.drawable.ic_queue, Modifier.weight(1f)) { showQueue = true }
            val dl = s != null && downloads.any { it.id == s.id }
            val p = s?.let { progress[it.id] }
            BottomAction(
                when { dl -> "Cihazda"; p != null -> "%${(p * 100).toInt()}"; else -> "Cihaza kaydet" },
                if (dl) R.drawable.ic_check_circle else R.drawable.ic_download,
                Modifier.weight(1f), tint = if (dl) C.Accent else C.Text
            ) { if (s != null && !dl) Downloads.enqueue(s) }
            BottomAction(
                when { sleepUntil == -1L -> "Şarkı bitince"; sleepUntil > 0 -> "${((sleepUntil - System.currentTimeMillis()) / 60000 + 1)} dk"; else -> "Uyku zamanlayıcı" },
                R.drawable.ic_moon, Modifier.weight(1f), tint = if (sleepUntil != 0L) C.Accent else C.Text
            ) { showSleep = true }
        }
    }

    if (showQueue) QueueSheet(vm) { showQueue = false }
    if (showSleep) {
        AlertDialog(
            onDismissRequest = { showSleep = false },
            containerColor = C.Surface2,
            title = { Text("Uyku zamanlayıcı") },
            text = {
                Column {
                    listOf(15, 30, 45, 60, 90).forEach { m ->
                        Text("$m dakika", style = T.Title, modifier = Modifier.fillMaxWidth()
                            .clickable { vm.sleep(m); showSleep = false }.padding(vertical = 12.dp))
                    }
                    Text("Bu şarkı bitince", style = T.Title, modifier = Modifier.fillMaxWidth()
                        .clickable { vm.sleep(-1); showSleep = false }.padding(vertical = 12.dp))
                    if (sleepUntil != 0L) Text("Kapat", style = T.Title, color = C.Accent, modifier = Modifier.fillMaxWidth()
                        .clickable { vm.sleep(0); showSleep = false }.padding(vertical = 12.dp))
                }
            },
            confirmButton = { TextButton(onClick = { showSleep = false }) { Text("Vazgeç") } }
        )
    }
}

@Composable
private fun BottomAction(text: String, icon: Int, modifier: Modifier, tint: Color = C.Text, onClick: () -> Unit) {
    Column(
        modifier.height(64.dp).clip(RoundedCornerShape(14.dp)).background(Color(0x42000000)).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Ic(icon, null, tint = tint, size = 22.dp)
        Spacer(Modifier.height(5.dp))
        Text(text, style = T.Small, color = tint, maxLines = 1)
    }
}

@Composable
private fun QueueSheet(vm: MainViewModel, onDismiss: () -> Unit) {
    val queue by vm.queue.collectAsState()
    val index by vm.queueIndex.collectAsState()
    val playing by vm.isPlaying.collectAsState()
    val state = rememberLazyListState(initialFirstVisibleItemIndex = index.coerceAtLeast(0))
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = C.Surface2) {
        Text("Sıradakiler", style = T.H2, modifier = Modifier.padding(start = 20.dp, bottom = 8.dp))
        LazyColumn(state = state, modifier = Modifier.fillMaxWidth().height(520.dp)) {
            itemsIndexed(queue, key = { i, s -> "$i-${s.id}" }) { i, s ->
                val cur = i == index
                Row(
                    Modifier.fillMaxWidth().background(if (cur) Color(0x14FFFFFF) else Color.Transparent)
                        .clickable { vm.skipTo(i) }.padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Cover(s, 46.dp)
                        if (cur) {
                            Box(Modifier.size(46.dp).clip(RoundedCornerShape(8.dp)).background(Color(0x99000000)))
                            EqBars(playing)
                        }
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(s.title, style = T.Title, color = if (cur) C.Accent else C.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(s.artist, style = T.Small, color = C.Muted, maxLines = 1)
                    }
                    if (!cur) IconBtn(R.drawable.ic_close, "Sıradan çıkar", { vm.removeFromQueue(i) }, tint = C.Muted, size = 18.dp, box = 40.dp)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

