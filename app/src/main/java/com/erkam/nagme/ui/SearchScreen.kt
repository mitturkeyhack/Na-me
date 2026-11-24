package com.erkam.nagme.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.erkam.nagme.R
import com.erkam.nagme.data.Store

@Composable
fun SearchScreen(vm: MainViewModel, bottomPad: PaddingValues) {
    val q by vm.query.collectAsState()
    val sugg by vm.suggestions.collectAsState()
    val results by vm.results.collectAsState()
    val searching by vm.searching.collectAsState()
    val error by vm.searchError.collectAsState()
    val history by Store.history.collectAsState()
    val focus = remember { FocusRequester() }
    val fm = LocalFocusManager.current

    LaunchedEffect(Unit) { if (results.isEmpty()) runCatching { focus.requestFocus() } }

    Column {
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBtn(R.drawable.ic_back, "Geri", { vm.tab.value = Tab.Home })
            Row(
                Modifier.weight(1f).height(46.dp).clip(RoundedCornerShape(23.dp)).background(C.Surface2)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Ic(R.drawable.ic_search, null, tint = C.Muted, size = 20.dp)
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (q.isEmpty()) Text("Şarkı veya sanatçı ara", color = C.Muted, style = T.Body)
                    BasicTextField(
                        value = q,
                        onValueChange = { vm.onQuery(it) },
                        singleLine = true,
                        textStyle = T.Body.copy(color = C.Text),
                        cursorBrush = SolidColor(C.Accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { fm.clearFocus(); vm.search() }),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus)
                    )
                }
                if (q.isNotEmpty()) IconBtn(R.drawable.ic_close, "Temizle", { vm.onQuery("") }, size = 18.dp, box = 36.dp)
            }
        }

        LazyColumn(contentPadding = bottomPad) {
            if (sugg.isNotEmpty()) {
                items(sugg) { s ->
                    Row(
                        Modifier.fillMaxWidth().clickable { fm.clearFocus(); vm.search(s) }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Ic(R.drawable.ic_search, null, tint = C.Muted, size = 18.dp)
                        Spacer(Modifier.width(14.dp))
                        Text(s, style = T.Body)
                    }
                }
                return@LazyColumn
            }

            if (searching) {
                item {
                    Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = C.Accent)
                    }
                }
            }

            error?.let { e ->
                item { Text(e, color = C.Muted, style = T.Body, modifier = Modifier.padding(24.dp)) }
            }

            if (!searching && results.isNotEmpty()) {
                val top = results.first()
                item {
                    Text("En iyi sonuç", style = T.H2, modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 10.dp))
                    Column(
                        Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp))
                            .background(C.Surface).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Cover(top, 72.dp, 10.dp)
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text(top.title, style = T.H2, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(top.artist, style = T.Small, color = C.Muted, maxLines = 1)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            BigButton("Çal", R.drawable.ic_play, true, Modifier.weight(1f)) { vm.playSong(top) }
                            BigButton("Karıştır", R.drawable.ic_shuffle, false, Modifier.weight(1f)) { vm.play(results, shuffled = true) }
                        }
                    }
                }
                item { Text("Şarkılar", style = T.H2, modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 6.dp)) }
                itemsIndexed(results.drop(1), key = { _, s -> "r" + s.id }) { i, s ->
                    SongRow(s, vm, onClick = { vm.play(results, i + 1) })
                }
            }

            if (!searching && results.isEmpty() && error == null && history.isNotEmpty()) {
                item { SectionTitle("Son dinlediklerin") }
                itemsIndexed(history.take(15), key = { _, s -> "h" + s.id }) { i, s ->
                    SongRow(s, vm, onClick = { vm.playSong(s) })
                }
            }
        }
    }
}

@Composable
fun BigButton(text: String, icon: Int, primary: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier.height(44.dp).clip(RoundedCornerShape(22.dp))
            .background(if (primary) C.Accent else androidx.compose.ui.graphics.Color(0xFF2C2C34))
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Ic(icon, null, tint = if (primary) C.OnAccent else C.Text, size = 18.dp)
        Spacer(Modifier.width(8.dp))
        Text(text, style = T.Title, color = if (primary) C.OnAccent else C.Text)
    }
}

