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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.erkam.nagme.App
import com.erkam.nagme.R
import com.erkam.nagme.data.Song
import com.erkam.nagme.data.Store

@Composable
fun HomeScreen(vm: MainViewModel, bottomPad: PaddingValues) {
    val online by App.online.collectAsState()
    val history by Store.history.collectAsState()
    val forYou by vm.forYou.collectAsState()
    val loading by vm.homeLoading.collectAsState()
    val mood by vm.mood.collectAsState()
    val downloads by Store.downloads.collectAsState()
    val liked by Store.liked.collectAsState()
    val playlists by Store.playlists.collectAsState()
    val current by vm.current.collectAsState()
    val playing by vm.isPlaying.collectAsState()

    PullToRefreshBox(isRefreshing = loading, onRefresh = { vm.loadHome(force = true) }) {
        LazyColumn(contentPadding = bottomPad) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 14.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(C.Accent),
                        contentAlignment = Alignment.Center
                    ) { Ic(R.drawable.ic_note, null, tint = C.OnAccent, size = 20.dp) }
                    Spacer(Modifier.width(10.dp))
                    Text("Nağme", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconBtn(R.drawable.ic_search, "Ara", { vm.tab.value = Tab.Search })
                    Avatar { vm.showSettings.value = true }
                }
            }

            if (!online) {
                item {
                    Row(
                        Modifier
                            .padding(horizontal = 20.dp, vertical = 8.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF17171C))
                            .border(1.dp, C.Line, RoundedCornerShape(12.dp))
                            .clickable { vm.openCollection.value = Collection.Downloads }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Ic(R.drawable.ic_cloud_off, null, tint = C.Accent, size = 22.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("İnternet yok", style = T.Title)
                            Text("Cihazdaki ${downloads.size} şarkını dinleyebilirsin", style = T.Small, color = C.Muted)
                        }
                    }
                }
                if (downloads.isNotEmpty()) {
                    item { SectionTitle("Cihazdakiler", "Karışık çal") { vm.play(downloads, shuffled = true) } }
                    itemsIndexed(downloads.take(30), key = { _, s -> "d" + s.id }) { i, s ->
                        SongRow(s, vm, onClick = { vm.play(downloads, i) }, showDownload = false)
                    }
                }
            }

            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item { Pill("Hepsi", mood == null) { vm.selectMood(null) } }
                    items(vm.moods.keys.toList()) { m -> Pill(m, mood == m) { vm.selectMood(m) } }
                }
            }

            val quick = history.take(6)
            if (quick.isNotEmpty() && mood == null && online) {
                item { SectionTitle("Hızlı seçimler") }
                item {
                    Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        quick.chunked(2).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { s ->
                                    QuickTile(s, current?.id == s.id, playing, Modifier.weight(1f)) {
                                        vm.playSong(s)
                                    }
                                }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            if (online) {
                item {
                    SectionTitle(
                        if (mood != null) "$mood şarkılar" else if (history.isEmpty()) "Popüler şarkılar" else "Senin için",
                        if (forYou.isNotEmpty()) "Tümünü çal" else null
                    ) { vm.play(forYou) }
                }
                if (forYou.isEmpty() && loading) {
                    item {
                        Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = C.Accent)
                        }
                    }
                }
                itemsIndexed(forYou.take(20), key = { _, s -> "f" + s.id }) { i, s ->
                    SongRow(s, vm, onClick = { vm.play(forYou, i) })
                }
            }

            item { SectionTitle("Senin listelerin") }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        ListCard("Beğenilenler", "${liked.size} şarkı", C.Accent, R.drawable.ic_heart_fill, C.OnAccent, liked.firstOrNull()) {
                            vm.openCollection.value = Collection.Liked
                        }
                    }
                    item {
                        ListCard("Cihazdakiler", "${downloads.size} şarkı", Color(0xFF2A2A33), R.drawable.ic_download, C.Text, downloads.firstOrNull()) {
                            vm.openCollection.value = Collection.Downloads
                        }
                    }
                    items(playlists, key = { it.id }) { p ->
                        ListCard(p.name, "${p.songs.size} şarkı", coverColor(p.id), R.drawable.ic_library, Color.White, p.songs.firstOrNull()) {
                            vm.openCollection.value = Collection.User(p.id)
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun QuickTile(s: Song, isCurrent: Boolean, playing: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .height(56.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(C.Surface)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(contentAlignment = Alignment.Center) {
            Cover(s, 56.dp, 0.dp)
            if (isCurrent) {
                Box(Modifier.size(56.dp).background(Color(0x99000000)))
                EqBars(playing)
            }
        }
        Column(Modifier.padding(horizontal = 10.dp)) {
            Text(s.title, style = T.Small.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (isCurrent) C.Accent else C.Text)
            Text(s.artist, style = T.Small, color = C.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ListCard(
    title: String, meta: String, bg: Color, icon: Int, iconTint: Color, first: Song?, onClick: () -> Unit
) {
    Column(Modifier.width(148.dp).clickable(onClick = onClick), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(148.dp).clip(RoundedCornerShape(12.dp)).background(bg)) {
            if (first != null && icon == R.drawable.ic_library) Cover(first, 148.dp, 12.dp)
            Box(
                Modifier.align(Alignment.BottomStart).padding(10.dp).size(36.dp)
                    .clip(RoundedCornerShape(10.dp)).background(Color(0x55000000)),
                contentAlignment = Alignment.Center
            ) { Ic(icon, null, tint = if (icon == R.drawable.ic_library) Color.White else iconTint, size = 20.dp) }
        }
        Text(title, style = T.Title, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(meta, style = T.Small, color = C.Muted)
    }
}
