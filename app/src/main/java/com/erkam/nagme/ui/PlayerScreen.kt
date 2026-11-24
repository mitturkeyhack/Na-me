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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import com.erkam.nagme.data.Song
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

    val base = rememberCoverTint(song?.thumb)
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
        // Hız: sadece uzun kayıtlarda (podcast, sohbet)
        if (dur >= com.erkam.nagme.data.LONG_MS) {
            val speed by remember { Store.settings }.collectAsState()
            Row(
                Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Hız", style = T.Small, color = Color(0xFFCBD0D4))
                listOf(0.75f, 1f, 1.25f, 1.5f, 2f).forEach { v ->
                    val sel = speed.longSpeed == v
                    Box(
                        Modifier.height(32.dp).clip(RoundedCornerShape(16.dp))
                            .background(if (sel) Color.White else Color(0x33000000))
                            .clickable { vm.setLongSpeed(v) }.padding(horizontal = 11.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(speedLabel(v), style = T.Small, color = if (sel) Color(0xFF14211F) else Color.White)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BottomAction("Sıradakiler", R.drawable.ic_queue, Modifier.weight(1f)) { showQueue = true }
            BottomAction("Sözler", R.drawable.ic_lyrics, Modifier.weight(1f)) { vm.showLyrics.value = true }
            val dl = s != null && downloads.any { it.id == s.id }
            val p = s?.let { progress[it.id] }
            BottomAction(
                when { dl -> "Cihazda"; p != null -> "%${(p * 100).toInt()}"; else -> "Kaydet" },
                if (dl) R.drawable.ic_check_circle else R.drawable.ic_download,
                Modifier.weight(1f), tint = if (dl) C.Accent else C.Text
            ) { if (s != null && !dl) Downloads.enqueue(s) }
            BottomAction(
                when { sleepUntil == -1L -> "Şarkı sonu"; sleepUntil > 0 -> "${((sleepUntil - System.currentTimeMillis()) / 60000 + 1)} dk"; else -> "Uyku" },
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

fun speedLabel(v: Float) = when (v) { 1f -> "1x"; 2f -> "2x"; else -> v.toString().replace('.', ',') + "x" }

private class QItem(val uid: String, val song: Song)

/** Sıradakiler: tutamaktan sürükle-bırak, sola kaydırıp çıkar */
@Composable
private fun QueueSheet(vm: MainViewModel, onDismiss: () -> Unit) {
    val queue by vm.queue.collectAsState()
    val index by vm.queueIndex.collectAsState()
    val playing by vm.isPlaying.collectAsState()
    val radio by vm.radioSeed.collectAsState()
    val items = remember { mutableStateListOf<QItem>() }
    var dragUid by remember { mutableStateOf<String?>(null) }
    var dragFrom by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val itemH = with(density) { 62.dp.toPx() }

    LaunchedEffect(queue, index) {
        if (dragUid == null) {
            items.clear()
            queue.forEachIndexed { i, s -> items.add(QItem("$i-${s.id}", s)) }
        }
    }
    val state = rememberLazyListState(initialFirstVisibleItemIndex = (index - 1).coerceAtLeast(0))

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = C.Surface2, sheetGesturesEnabled = dragUid == null) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Sıradakiler", style = T.H2, modifier = Modifier.weight(1f))
            Text("${queue.size} şarkı", style = T.Small, color = C.Muted)
        }
        if (radio != null) {
            Row(
                Modifier.padding(horizontal = 20.dp, vertical = 6.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF2A2A33))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Ic(R.drawable.ic_radio, null, tint = C.Accent, size = 18.dp)
                Spacer(Modifier.width(8.dp))
                Text("Radyo: ${radio!!.title}'na benzer şarkılar", style = T.Small, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Text("Tutamaktan sürükle · sola kaydırıp çıkar", style = T.Small, color = C.Muted,
            modifier = Modifier.padding(start = 20.dp, top = 4.dp, bottom = 6.dp))
        LazyColumn(state = state, modifier = Modifier.fillMaxWidth().height(520.dp)) {
            itemsIndexed(items, key = { _, q -> q.uid }) { i, q ->
                val dragged = q.uid == dragUid
                val cur = q.uid == "$index-${queue.getOrNull(index)?.id}"
                var swipe by remember(q.uid) { mutableFloatStateOf(0f) }
                val rowMod = if (dragged) Modifier.zIndex(1f).graphicsLayer {
                    translationY = dragOffset; shadowElevation = 24f; scaleX = 1.02f; scaleY = 1.02f
                } else Modifier.animateItem()
                Box(rowMod.fillMaxWidth().height(62.dp)) {
                    if (swipe < -10f) {
                        Row(Modifier.matchParentSize().background(Color(0xFF5A2A2A)).padding(end = 20.dp),
                            horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                            Text("Sıradan çıkar", style = T.Small, color = Color.White)
                        }
                    }
                    Row(
                        Modifier.fillMaxSize()
                            .graphicsLayer { translationX = swipe }
                            .background(if (dragged) Color(0xFF2E2E38) else if (cur) Color(0xFF2A2A33) else C.Surface2)
                            .clickable { val at = items.indexOfFirst { it.uid == q.uid }; if (at >= 0) vm.skipTo(at) }
                            .pointerInput(q.uid, cur) {
                                if (cur) return@pointerInput
                                detectHorizontalDragGestures(
                                    onDragEnd = {
                                        if (swipe < -size.width * 0.35f) {
                                            val at = items.indexOfFirst { it.uid == q.uid }
                                            if (at >= 0) { items.removeAt(at); vm.removeFromQueue(at) }
                                        }
                                        swipe = 0f
                                    },
                                    onDragCancel = { swipe = 0f }
                                ) { _, dx -> swipe = (swipe + dx).coerceAtMost(0f) }
                            }
                            .padding(start = 16.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Cover(q.song, 46.dp)
                            if (cur) {
                                Box(Modifier.size(46.dp).clip(RoundedCornerShape(8.dp)).background(Color(0x99000000)))
                                EqBars(playing)
                            }
                        }
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(q.song.title, style = T.Title, color = if (cur) C.Accent else Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(q.song.artist, style = T.Small, color = C.Muted, maxLines = 1)
                        }
                        Box(
                            Modifier.size(44.dp).pointerInput(q.uid) {
                                detectDragGestures(
                                    onDragStart = {
                                        dragUid = q.uid
                                        dragFrom = items.indexOfFirst { it.uid == q.uid }
                                        dragOffset = 0f
                                    },
                                    onDragEnd = {
                                        val to = items.indexOfFirst { it.uid == dragUid }
                                        if (dragFrom >= 0 && to >= 0 && to != dragFrom) vm.moveInQueue(dragFrom, to)
                                        dragUid = null; dragFrom = -1; dragOffset = 0f
                                    },
                                    onDragCancel = { dragUid = null; dragFrom = -1; dragOffset = 0f }
                                ) { change, amount ->
                                    change.consume()
                                    dragOffset += amount.y
                                    var at = items.indexOfFirst { it.uid == dragUid }
                                    while (dragOffset > itemH / 2 && at < items.lastIndex) {
                                        items.add(at + 1, items.removeAt(at)); at++; dragOffset -= itemH
                                    }
                                    while (dragOffset < -itemH / 2 && at > 0) {
                                        items.add(at - 1, items.removeAt(at)); at--; dragOffset += itemH
                                    }
                                }
                            },
                            contentAlignment = Alignment.Center
                        ) {
                            Ic(R.drawable.ic_grip, "Sürükleyerek taşı", tint = if (dragged) C.Accent else Color(0xFFC9C9D0), size = 22.dp)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}
