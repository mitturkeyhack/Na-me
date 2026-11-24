package com.erkam.nagme.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.erkam.nagme.R
import com.erkam.nagme.data.LyricsResult
import com.erkam.nagme.data.formatTime

/** Kelime kelime akan şarkı sözleri (Tasarım C) */
@Composable
fun LyricsScreen(vm: MainViewModel) {
    val song by vm.current.collectAsState()
    val playing by vm.isPlaying.collectAsState()
    val dur by vm.duration.collectAsState()
    val ctx = LocalContext.current
    val s = song ?: return

    var result by remember(s.id) { mutableStateOf<LyricsResult?>(null) }
    LaunchedEffect(s.id) { result = com.erkam.nagme.data.Lyrics.get(ctx, s) }

    // Konum: her karede güncellenir, kelimeler akıcı dolar
    var now by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) withFrameMillis { now = vm.livePosition() }
    }

    var textSize by remember { mutableIntStateOf(1) } // 0 küçük, 1 orta, 2 büyük
    val big = listOf(26, 32, 38)[textSize]
    val small = listOf(17, 20, 24)[textSize]
    val tint = rememberCoverTint(s.thumb, Color(0xFF1F2A3A), 0.35f)
    val accent = C.Accent

    Box(
        Modifier.fillMaxSize().background(Color(0xFF121318))
            .pointerInput(Unit) { detectTapGestures { } }
    ) {
        // Arka plandaki iki yumuşak renk dairesi (kapak renginden)
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(tint, radius = size.width * 0.55f, center = Offset(size.width * 0.85f, size.height * 0.18f))
            drawCircle(tint.copy(alpha = 0.55f), radius = size.width * 0.6f, center = Offset(size.width * 0.1f, size.height * 0.85f))
        }
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBtn(R.drawable.ic_chevron_down, "Sözleri kapat", { vm.showLyrics.value = false }, size = 28.dp)
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(s.title, style = T.Title, color = Color.White, maxLines = 1)
                    Text(s.artist, style = T.Small, color = Color(0xFFC3C7D2), maxLines = 1)
                }
                Box(
                    Modifier.size(44.dp).clip(CircleShape).clickable { textSize = (textSize + 1) % 3 },
                    contentAlignment = Alignment.Center
                ) { Text("Aa", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White) }
            }

            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val r = result
                when {
                    r == null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = accent)
                        Spacer(Modifier.height(12.dp))
                        Text("Sözler aranıyor…", color = Color(0xFFC3C7D2), style = T.Body)
                    }
                    r.synced.isNotEmpty() -> SyncedLyrics(vm, r, now, big, small, accent)
                    r.plain.isNotBlank() -> Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Bu şarkının sözleri zamanlı değil", style = T.Small, color = Color(0xFFC3C7D2))
                        Spacer(Modifier.height(16.dp))
                        Text(r.plain, fontSize = small.sp, lineHeight = (small * 1.5).sp, color = Color.White,
                            textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold)
                    }
                    else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Ic(R.drawable.ic_note, null, tint = Color(0xFFC3C7D2), size = 40.dp)
                        Spacer(Modifier.height(12.dp))
                        Text("Bu şarkının sözleri bulunamadı", style = T.Title, color = Color.White)
                        Text("Enstrümantal olabilir ya da arşivde yok", style = T.Small, color = Color(0xFFC3C7D2))
                    }
                }
            }

            // Alt kumanda
            Row(
                Modifier.padding(bottom = 16.dp, top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp))
                    .background(Color(0x14FFFFFF)).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Cover(s, 48.dp, 10.dp)
                Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                    val f = if (dur > 0) (now.toFloat() / dur).coerceIn(0f, 1f) else 0f
                    Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color(0x38FFFFFF))) {
                        Box(Modifier.fillMaxWidth(f).height(3.dp).background(accent))
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("${formatTime(now / 1000)} / ${formatTime(dur / 1000)}", style = T.Small, color = Color(0xFFC3C7D2))
                }
                Box(
                    Modifier.size(48.dp).clip(CircleShape).background(accent).clickable { vm.toggle() },
                    contentAlignment = Alignment.Center
                ) {
                    Ic(if (playing) R.drawable.ic_pause else R.drawable.ic_play, if (playing) "Duraklat" else "Çal", tint = C.OnAccent, size = 22.dp)
                }
            }
        }
    }
}

@Composable
private fun SyncedLyrics(vm: MainViewModel, r: LyricsResult, now: Long, big: Int, small: Int, accent: Color) {
    val lines = r.synced
    val cur = lines.indexOfLast { it.start <= now }.coerceAtLeast(-1)
    val state = rememberLazyListState()
    var userScrollAt by remember { mutableLongStateOf(0L) }

    LaunchedEffect(state.isScrollInProgress) {
        if (state.isScrollInProgress) userScrollAt = System.currentTimeMillis()
    }
    LaunchedEffect(cur) {
        // Kullanıcı az önce kaydırdıysa otomatik kaydırma bekler
        if (System.currentTimeMillis() - userScrollAt < 3500 && userScrollAt != 0L) return@LaunchedEffect
        val h = state.layoutInfo.viewportSize.height
        state.animateScrollToItem((cur + 1).coerceAtLeast(0), -(h / 3))
    }

    LazyColumn(
        state = state,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 240.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        item { Spacer(Modifier.height(1.dp)) }
        itemsIndexed(lines, key = { i, l -> "$i-${l.start}" }) { i, line ->
            val isCur = i == cur
            val dist = kotlin.math.abs(i - cur)
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { vm.seek(line.start); if (!vm.isPlaying.value) vm.toggle() }
                .padding(horizontal = 4.dp, vertical = 2.dp), contentAlignment = Alignment.Center) {
                if (isCur) {
                    val text = buildAnnotatedString {
                        line.words.forEachIndexed { wi, w ->
                            withStyle(SpanStyle(color = if (now >= w.start) accent else Color.White.copy(alpha = 0.55f))) {
                                append(w.text)
                            }
                            if (wi < line.words.lastIndex) append(" ")
                        }
                    }
                    Text(text, fontSize = big.sp, lineHeight = (big * 1.2).sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
                } else {
                    val alpha = when {
                        i < cur -> 0.38f
                        dist == 1 -> 0.68f
                        else -> 0.48f
                    }
                    Text(line.text, fontSize = small.sp, lineHeight = (small * 1.3).sp, fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = alpha), textAlign = TextAlign.Center)
                }
            }
        }
        item {
            Text("Sözler: LRCLIB", style = T.Small, color = Color(0x80FFFFFF))
        }
    }
}

