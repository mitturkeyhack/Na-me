package com.erkam.nagme.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.erkam.nagme.R
import com.erkam.nagme.data.Downloads
import com.erkam.nagme.data.Song
import com.erkam.nagme.data.Store

@Composable
fun LibraryScreen(vm: MainViewModel, bottomPad: PaddingValues) {
    var filter by rememberSaveable { mutableStateOf(0) }
    var newList by remember { mutableStateOf(false) }
    val liked by Store.liked.collectAsState()
    val downloads by Store.downloads.collectAsState()
    val history by Store.history.collectAsState()
    val playlists by Store.playlists.collectAsState()

    Column {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Kitaplık", style = T.H1, modifier = Modifier.weight(1f))
            IconBtn(R.drawable.ic_plus, "Yeni liste oluştur", { newList = true })
            Avatar { vm.showSettings.value = true }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val names = listOf("Listeler", "Beğenilenler", "Cihazda", "Geçmiş")
            items(names.size) { i -> Pill(names[i], filter == i) { filter = i } }
        }

        LazyColumn(contentPadding = bottomPad) {
            when (filter) {
                0 -> {
                    item {
                        LibRow("Beğenilenler", "Otomatik liste · ${liked.size} şarkı", R.drawable.ic_heart_fill, C.Accent, C.OnAccent) {
                            vm.openCollection.value = Collection.Liked
                        }
                    }
                    item {
                        val mb = Downloads.totalBytes() / (1024 * 1024)
                        LibRow("Cihazdakiler", "İnternetsiz çalar · ${downloads.size} şarkı · $mb MB", R.drawable.ic_download, Color(0xFF2A2A33), C.Text) {
                            vm.openCollection.value = Collection.Downloads
                        }
                    }
                    item {
                        LibRow("Geçmiş", "${history.size} şarkı", R.drawable.ic_history, Color(0xFF2A2A33), C.Text) {
                            vm.openCollection.value = Collection.History
                        }
                    }
                    item { Box(Modifier.padding(horizontal = 20.dp, vertical = 8.dp).fillMaxWidth().height(1.dp).background(C.Line)) }
                    items(playlists, key = { it.id }) { p ->
                        Row(
                            Modifier.fillMaxWidth().clickable { vm.openCollection.value = Collection.User(p.id) }
                                .padding(horizontal = 20.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(60.dp).clip(RoundedCornerShape(10.dp)).background(coverColor(p.id))) {
                                p.songs.firstOrNull()?.let { Cover(it, 60.dp, 10.dp) }
                            }
                            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                                Text(p.name, style = T.Title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("Liste · ${p.songs.size} şarkı", style = T.Small, color = C.Muted)
                            }
                            if (p.songs.isNotEmpty() && p.songs.all { s -> downloads.any { it.id == s.id } }) {
                                Ic(R.drawable.ic_check_circle, "Cihaza kaydedildi", tint = C.Accent, size = 20.dp)
                                Spacer(Modifier.width(12.dp))
                            }
                        }
                    }
                    item {
                        Row(
                            Modifier.padding(20.dp).fillMaxWidth().height(52.dp).clip(RoundedCornerShape(14.dp))
                                .border(1.dp, Color(0xFF3A3A44), RoundedCornerShape(14.dp))
                                .clickable { newList = true },
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Ic(R.drawable.ic_plus, null, size = 20.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Yeni liste oluştur", style = T.Body)
                        }
                    }
                }
                1 -> songList(liked, vm, "Henüz beğendiğin şarkı yok. Oynatıcıdaki kalbe dokun.")
                2 -> songList(downloads, vm, "Cihazda şarkı yok. Şarkıların yanındaki indirme simgesine dokun.")
                3 -> songList(history, vm, "Henüz bir şey dinlemedin.")
            }
        }
    }

    if (newList) NewPlaylistDialog(onDismiss = { newList = false })
}

private fun androidx.compose.foundation.lazy.LazyListScope.songList(list: List<Song>, vm: MainViewModel, empty: String) {
    if (list.isEmpty()) {
        item { Text(empty, color = C.Muted, style = T.Body, modifier = Modifier.padding(24.dp)) }
        return
    }
    item {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BigButton("Çal", R.drawable.ic_play, true, Modifier.weight(1f)) { vm.play(list) }
            BigButton("Karıştır", R.drawable.ic_shuffle, false, Modifier.weight(1f)) { vm.play(list, shuffled = true) }
        }
    }
    itemsIndexed(list, key = { _, s -> s.id }) { i, s -> SongRow(s, vm, onClick = { vm.play(list, i) }) }
}

@Composable
private fun LibRow(title: String, meta: String, icon: Int, bg: Color, tint: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(60.dp).clip(RoundedCornerShape(10.dp)).background(bg), contentAlignment = Alignment.Center) {
            Ic(icon, null, tint = tint, size = 28.dp)
        }
        Column(Modifier.padding(start = 14.dp)) {
            Text(title, style = T.Title)
            Text(meta, style = T.Small, color = C.Muted)
        }
    }
}

@Composable
fun NewPlaylistDialog(onDismiss: () -> Unit, initial: String = "", onSave: ((String) -> Unit)? = null) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.Surface2,
        title = { Text(if (onSave == null) "Yeni liste" else "Listeyi yeniden adlandır") },
        text = {
            OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Liste adı") })
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank()) {
                    if (onSave != null) onSave(name.trim()) else Store.createPlaylist(name)
                    onDismiss()
                }
            }) { Text("Kaydet") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } }
    )
}

/** Beğenilenler / Cihazdakiler / Geçmiş / kendi listen */
@Composable
fun CollectionScreen(vm: MainViewModel, col: Collection, bottomPad: PaddingValues) {
    val liked by Store.liked.collectAsState()
    val downloads by Store.downloads.collectAsState()
    val history by Store.history.collectAsState()
    val playlists by Store.playlists.collectAsState()
    var rename by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val (title, songs, pid) = when (col) {
        Collection.Liked -> Triple("Beğenilenler", liked, null)
        Collection.Downloads -> Triple("Cihazdakiler", downloads, null)
        Collection.History -> Triple("Geçmiş", history, null)
        is Collection.User -> {
            val p = playlists.firstOrNull { it.id == col.id }
            if (p == null) { vm.openCollection.value = null; return }
            Triple(p.name, p.songs, p.id)
        }
    }

    Column(Modifier.fillMaxSize().background(C.Bg)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBtn(R.drawable.ic_back, "Geri", { vm.openCollection.value = null })
            Spacer(Modifier.weight(1f))
            if (pid != null) {
                TextButton(onClick = { rename = true }) { Text("Adını değiştir", color = C.Muted) }
                IconBtn(R.drawable.ic_trash, "Listeyi sil", { confirmDelete = true }, tint = C.Muted)
            }
            if (col == Collection.History && songs.isNotEmpty()) {
                TextButton(onClick = { Store.clearHistory() }) { Text("Temizle", color = C.Muted) }
            }
        }
        LazyColumn(contentPadding = bottomPad) {
            item {
                Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(96.dp).clip(RoundedCornerShape(14.dp)).background(if (col == Collection.Liked) C.Accent else coverColor(title)),
                        contentAlignment = Alignment.Center) {
                        when {
                            col == Collection.Liked -> Ic(R.drawable.ic_heart_fill, null, tint = C.OnAccent, size = 40.dp)
                            col == Collection.Downloads -> Ic(R.drawable.ic_download, null, size = 40.dp)
                            songs.isNotEmpty() -> Cover(songs.first(), 96.dp, 14.dp)
                        }
                    }
                    Column(Modifier.padding(start = 16.dp)) {
                        Text(title, style = T.H1, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("${songs.size} şarkı", style = T.Small, color = C.Muted)
                    }
                }
            }
            if (songs.isNotEmpty()) {
                item {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        BigButton("Çal", R.drawable.ic_play, true, Modifier.weight(1f)) { vm.play(songs) }
                        BigButton("Karıştır", R.drawable.ic_shuffle, false, Modifier.weight(1f)) { vm.play(songs, shuffled = true) }
                    }
                }
                if (col != Collection.Downloads) {
                    val missing = songs.filter { s -> downloads.none { it.id == s.id } }
                    if (missing.isNotEmpty()) item {
                        TextButton(onClick = { Downloads.enqueue(missing) }, modifier = Modifier.padding(horizontal = 8.dp)) {
                            Ic(R.drawable.ic_download, null, tint = C.Accent, size = 18.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Tümünü cihaza kaydet (${missing.size})", color = C.Accent)
                        }
                    }
                }
            } else {
                item { Text("Bu liste boş.", color = C.Muted, modifier = Modifier.padding(24.dp)) }
            }
            itemsIndexed(songs, key = { _, s -> s.id }) { i, s ->
                SongRow(s, vm, onClick = { vm.play(songs, i) }, playlistId = pid)
            }
        }
    }

    if (rename && pid != null) NewPlaylistDialog(onDismiss = { rename = false }, initial = title) { Store.renamePlaylist(pid, it) }
    if (confirmDelete && pid != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = C.Surface2,
            title = { Text("Liste silinsin mi?") },
            text = { Text("\"$title\" listesi silinecek. Şarkılar cihazdan silinmez.") },
            confirmButton = {
                TextButton(onClick = { Store.deletePlaylist(pid); confirmDelete = false; vm.openCollection.value = null }) { Text("Sil") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Vazgeç") } }
        )
    }
}
