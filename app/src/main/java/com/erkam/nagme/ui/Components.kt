package com.erkam.nagme.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.allowHardware
import coil3.toBitmap
import com.erkam.nagme.R
import com.erkam.nagme.data.Downloads
import com.erkam.nagme.data.Song
import com.erkam.nagme.data.Store
import kotlinx.coroutines.flow.MutableStateFlow

object UiState {
    val addToPlaylist = MutableStateFlow<Song?>(null)
}

@Composable
fun Ic(@DrawableRes res: Int, desc: String?, modifier: Modifier = Modifier, tint: Color = C.Text, size: Dp = 24.dp) {
    Icon(painterResource(res), desc, modifier.size(size), tint = tint)
}

@Composable
fun IconBtn(@DrawableRes res: Int, desc: String, onClick: () -> Unit, tint: Color = C.Text, size: Dp = 24.dp, box: Dp = 44.dp) {
    IconButton(onClick = onClick, modifier = Modifier.size(box)) { Ic(res, desc, tint = tint, size = size) }
}

@Composable
fun Cover(song: Song?, size: Dp, corner: Dp = 8.dp, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(corner)
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(coverColor(song?.id ?: "x"))
    ) {
        if (song != null && song.thumb.isNotEmpty()) {
            AsyncImage(
                model = song.thumb,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/** Çalan şarkının yanında oynayan tını çubukları. */
@Composable
fun EqBars(playing: Boolean, modifier: Modifier = Modifier, color: Color = C.Accent, height: Dp = 14.dp) {
    val t = rememberInfiniteTransition(label = "eq")
    val durations = listOf(520, 680, 450, 600)
    val anims = durations.map { d ->
        t.animateFloat(
            initialValue = 0.25f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(d, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "bar"
        )
    }
    Row(modifier.height(height), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        anims.forEach { a ->
            val f = if (playing) a.value else 0.3f
            Box(
                Modifier
                    .width(3.dp)
                    .fillMaxHeight(f)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color)
            )
        }
    }
}

@Composable
fun Pill(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) C.Text else C.Chip)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = T.Body, color = if (selected) C.Bg else C.Text)
    }
}

@Composable
fun SongRow(
    song: Song,
    vm: MainViewModel,
    onClick: () -> Unit,
    playlistId: String? = null,
    showDownload: Boolean = true,
) {
    val current by vm.current.collectAsState()
    val playing by vm.isPlaying.collectAsState()
    val progress by Downloads.progress.collectAsState()
    val downloads by Store.downloads.collectAsState()
    val isCurrent = current?.id == song.id
    val downloaded = downloads.any { it.id == song.id }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(contentAlignment = Alignment.Center) {
            Cover(song, 50.dp)
            if (isCurrent) {
                Box(Modifier.size(50.dp).clip(RoundedCornerShape(8.dp)).background(Color(0x99000000)))
                EqBars(playing, height = 16.dp)
            }
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                song.title, style = T.Title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (isCurrent) C.Accent else C.Text
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (downloaded) {
                    Ic(R.drawable.ic_check_circle, "Cihazda", tint = C.Accent, size = 14.dp)
                    Spacer(Modifier.width(4.dp))
                }
                Text(song.artist, style = T.Small, color = C.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        val p = progress[song.id]
        if (showDownload) {
            when {
                p != null -> Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(progress = { p }, modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp, color = C.Accent, trackColor = C.Line)
                }
                !downloaded -> IconBtn(R.drawable.ic_download, "Cihaza kaydet", { Downloads.enqueue(song) }, tint = Color(0xFFC9C9D0), size = 22.dp)
            }
        }
        SongMenu(song, vm, playlistId)
    }
}

@Composable
fun SongMenu(song: Song, vm: MainViewModel, playlistId: String? = null) {
    var open by remember { mutableStateOf(false) }
    val liked by Store.liked.collectAsState()
    val downloads by Store.downloads.collectAsState()
    Box {
        IconBtn(R.drawable.ic_more, "Diğer seçenekler", { open = true }, tint = Color(0xFFC9C9D0), size = 20.dp, box = 40.dp)
        DropdownMenu(open, onDismissRequest = { open = false }, containerColor = C.Surface2) {
            val item: @Composable (String, Int, () -> Unit) -> Unit = { text, icon, action ->
                DropdownMenuItem(
                    text = { Text(text) },
                    leadingIcon = { Ic(icon, null, size = 20.dp) },
                    onClick = { open = false; action() }
                )
            }
            item("Bu şarkının radyosu", R.drawable.ic_radio) { vm.startRadio(song) }
            item("Sıradaki olarak çal", R.drawable.ic_queue) { vm.playNext(song) }
            item("Sıraya ekle", R.drawable.ic_plus) { vm.addToQueue(song) }
            val isLiked = liked.any { it.id == song.id }
            item(if (isLiked) "Beğenilenlerden çıkar" else "Beğen", if (isLiked) R.drawable.ic_heart_fill else R.drawable.ic_heart) {
                Store.toggleLike(song)
            }
            item("Listeye ekle", R.drawable.ic_library) { UiState.addToPlaylist.value = song }
            if (downloads.any { it.id == song.id }) {
                item("Cihazdan sil", R.drawable.ic_trash) { Downloads.delete(song.id) }
            } else {
                item("Cihaza kaydet", R.drawable.ic_download) { Downloads.enqueue(song) }
            }
            if (playlistId != null) {
                item("Bu listeden çıkar", R.drawable.ic_close) { Store.removeFromPlaylist(playlistId, song.id) }
            }
        }
    }
}

@Composable
fun AddToPlaylistDialog() {
    val song by UiState.addToPlaylist.collectAsState()
    val s = song ?: return
    val lists by Store.playlists.collectAsState()
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { UiState.addToPlaylist.value = null },
        containerColor = C.Surface2,
        title = { Text("Listeye ekle") },
        text = {
            Column {
                LazyColumn(Modifier.fillMaxWidth().height((lists.size.coerceAtMost(5) * 48).dp)) {
                    items(lists) { p ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .clickable { Store.addToPlaylist(p.id, s); UiState.addToPlaylist.value = null },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Ic(R.drawable.ic_library, null, size = 20.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(p.name, style = T.Title)
                            Spacer(Modifier.weight(1f))
                            Text("${p.songs.size}", style = T.Small, color = C.Muted)
                        }
                    }
                }
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Yeni liste adı") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank()) { Store.createPlaylist(name, s); UiState.addToPlaylist.value = null }
            }) { Text("Oluştur ve ekle") }
        },
        dismissButton = { TextButton(onClick = { UiState.addToPlaylist.value = null }) { Text("Vazgeç") } }
    )
}

@Composable
fun SectionTitle(text: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, style = T.H2, modifier = Modifier.weight(1f))
        if (action != null) TextButton(onClick = onAction) { Text(action, color = C.Muted, style = T.Small) }
    }
}

@Composable
fun Avatar(size: Dp = 34.dp, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.size(size).clip(CircleShape).background(C.Accent), contentAlignment = Alignment.Center) {
            Ic(R.drawable.ic_person, "Profil ve ayarlar", tint = C.OnAccent, size = size * 0.6f)
        }
    }
}

/** Kapak resminin koyu baskın rengi (oynatıcı ve söz ekranı arka planı için) */
@Composable
fun rememberCoverTint(thumb: String?, fallback: Color = Color(0xFF14211F), darken: Float = 0.45f): Color {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var c by remember { mutableStateOf(fallback) }
    androidx.compose.runtime.LaunchedEffect(thumb) {
        if (thumb.isNullOrEmpty()) return@LaunchedEffect
        val r = coil3.SingletonImageLoader.get(ctx).execute(
            coil3.request.ImageRequest.Builder(ctx).data(thumb).size(128).allowHardware(false).build()
        )
        if (r is coil3.request.SuccessResult) {
            val p = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                androidx.palette.graphics.Palette.from(r.image.toBitmap()).generate()
            }
            val rgb = p.darkVibrantSwatch?.rgb ?: p.darkMutedSwatch?.rgb ?: p.dominantSwatch?.rgb
            if (rgb != null) c = androidx.compose.ui.graphics.lerp(Color(rgb), Color(0xFF0F0F12), darken)
        }
    }
    return c
}

/** Uygulama simgesi + "Nağme" yazısı (logo B) */
@Composable
fun Logo(iconSize: Dp = 36.dp, textSize: Int = 27, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.Image(
            painterResource(R.drawable.app_icon), contentDescription = null,
            modifier = Modifier.size(iconSize).clip(RoundedCornerShape(iconSize * 0.27f))
        )
        Spacer(Modifier.width(iconSize * 0.3f))
        Text("Nağme", fontFamily = LogoFont, fontSize = androidx.compose.ui.unit.TextUnit(textSize.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp), color = Brand)
    }
}
