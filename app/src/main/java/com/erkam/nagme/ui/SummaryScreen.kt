package com.erkam.nagme.ui

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C as MC
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.erkam.nagme.R
import com.erkam.nagme.data.Play
import com.erkam.nagme.data.Song
import com.erkam.nagme.data.Store
import com.erkam.nagme.play.PlaybackService
import kotlinx.coroutines.delay
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

private val Ink = Color(0xFF1A1206)
private val Ink2 = Color(0x591A1206)

data class TopSong(val song: Song, val count: Int, val ms: Long)
data class TopArtist(val name: String, val ms: Long, val song: Song)

data class Summary(
    val monthly: Boolean,
    val minutes: Long,
    val prevMinutes: Long,
    val bars: List<Pair<String, Long>>, // etiket -> dakika
    val bestBar: Int,
    val top: List<TopSong>,
    val artists: List<TopArtist>,
    val songCount: Int,
    val artistCount: Int,
    val streak: Int,
)

private val zone: ZoneId get() = ZoneId.systemDefault()
private fun Play.day(): LocalDate = Instant.ofEpochMilli(at).atZone(zone).toLocalDate()

fun buildSummary(all: List<Play>, monthly: Boolean): Summary {
    val today = LocalDate.now(zone)
    val start = if (monthly) today.withDayOfMonth(1) else today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val prevStart = if (monthly) start.minusMonths(1) else start.minusWeeks(1)
    val plays = all.filter { !it.day().isBefore(start) }
    val prev = all.filter { val d = it.day(); !d.isBefore(prevStart) && d.isBefore(start) }
    val minutes = plays.sumOf { it.ms } / 60000
    val bars = if (!monthly) {
        val names = listOf("Pzt", "Sal", "Çar", "Per", "Cum", "Cmt", "Paz")
        (0..6).map { i -> val d = start.plusDays(i.toLong()); names[i] to plays.filter { it.day() == d }.sumOf { it.ms } / 60000 }
    } else {
        val weeks = (today.dayOfMonth - 1) / 7 + 1
        (0 until weeks).map { w ->
            val a = start.plusDays(w * 7L); val b = a.plusDays(7)
            "${w + 1}. hf" to plays.filter { val d = it.day(); !d.isBefore(a) && d.isBefore(b) }.sumOf { it.ms } / 60000
        }
    }
    val top = plays.groupBy { it.song.id }.map { (_, l) -> TopSong(l.last().song, l.size, l.sumOf { it.ms }) }
        .sortedWith(compareByDescending<TopSong> { it.count }.thenByDescending { it.ms }).take(5)
    val artists = plays.filter { it.song.artist.isNotBlank() }.groupBy { it.song.artist }
        .map { (a, l) -> TopArtist(a, l.sumOf { it.ms }, l.groupBy { it.song.id }.maxBy { it.value.size }.value.last().song) }
        .sortedByDescending { it.ms }.take(3)
    // Art arda dinlenen gün sayısı (bugünden geriye)
    val days = all.map { it.day() }.toHashSet()
    var streak = 0
    var d = if (today in days) today else today.minusDays(1)
    while (d in days) { streak++; d = d.minusDays(1) }
    return Summary(
        monthly, minutes, prev.sumOf { it.ms } / 60000, bars,
        bars.indices.maxByOrNull { bars[it].second } ?: 0,
        top, artists, plays.map { it.song.id }.distinct().size,
        plays.map { it.song.artist }.filter { it.isNotBlank() }.distinct().size, streak
    )
}

private fun fmt(n: Long) = "%,d".format(n).replace(',', '.')

/** Nağme Özetim: hikâye gibi kaydırılan sayfalar; arkada en çok dinlediğin şarkılar çalar. */
@Composable
fun SummaryScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    val all by Store.plays.collectAsState()
    var monthly by remember { mutableStateOf(false) }
    val sum = remember(all, monthly) { buildSummary(all, monthly) }
    val pageCount = if (sum.top.isEmpty()) 1 else 5
    var page by remember(monthly) { mutableIntStateOf(0) }
    var paused by remember { mutableStateOf(false) }
    var progress by remember(page, monthly) { mutableFloatStateOf(0f) }
    val period = if (monthly) "Bu ay" else "Bu hafta"

    // --- Arka plan müziği: her sayfada en çok dinlediklerinden biri ---
    val player = remember {
        ExoPlayer.Builder(ctx)
            .setMediaSourceFactory(DefaultMediaSourceFactory(PlaybackService.dataSourceFactory(ctx)))
            .setAudioAttributes(AudioAttributes.Builder().setUsage(MC.USAGE_MEDIA).setContentType(MC.AUDIO_CONTENT_TYPE_MUSIC).build(), false)
            .build()
    }
    DisposableEffect(Unit) {
        vm.pauseForSummary()
        onDispose { player.release(); vm.resumeAfterSummary() }
    }
    val pageSong: Song? = when (page) {
        0 -> sum.top.getOrNull(2)?.song ?: sum.top.firstOrNull()?.song
        1 -> sum.top.firstOrNull()?.song
        2 -> sum.top.getOrNull(1)?.song ?: sum.top.firstOrNull()?.song
        3 -> sum.artists.firstOrNull()?.song
        else -> sum.top.firstOrNull()?.song
    }
    LaunchedEffect(pageSong?.id) {
        // Önceki şarkıyı kısarak bitir
        for (i in 8 downTo 0) { player.volume = player.volume.coerceAtMost(i / 10f); delay(30) }
        val s = pageSong ?: run { player.stop(); return@LaunchedEffect }
        player.setMediaItem(PlaybackService.resolveItem(s.toMediaItem()))
        player.prepare()
        // Nakarat genelde şarkının üçte birinde: oradan başlat
        val startMs = if (s.duration > 60) s.duration * 1000 / 3 else 30_000L
        player.seekTo(startMs)
        player.volume = 0f
        player.play()
        for (i in 1..16) { delay(90); player.volume = i / 20f }
    }
    LaunchedEffect(paused) { if (paused) player.pause() else if (player.mediaItemCount > 0) player.play() }

    // Sayfa ilerlemesi (8 saniye)
    LaunchedEffect(page, paused, monthly) {
        while (!paused && progress < 1f) {
            delay(50); progress += 50f / 8000f
        }
        if (progress >= 1f && page < pageCount - 1) page++
    }

    Box(
        Modifier.fillMaxSize().background(C.Accent)
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val w = constraints.maxWidth
            Box(Modifier.fillMaxSize().pointerInput(pageCount, monthly) {
                detectTapGestures(
                    onPress = { paused = true; tryAwaitRelease(); paused = false },
                    onTap = { o ->
                        if (o.x < w / 3f) { if (page > 0) page-- else progress = 0f }
                        else if (page < pageCount - 1) page++
                    }
                )
            })
        }
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp)
        ) {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(pageCount) { i ->
                    val f = when { i < page -> 1f; i == page -> progress.coerceIn(0f, 1f); else -> 0f }
                    Box(Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color(0x401A1206))) {
                        Box(Modifier.fillMaxWidth(f).fillMaxHeight().background(Ink))
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.foundation.Image(
                    androidx.compose.ui.res.painterResource(R.drawable.app_icon), null,
                    Modifier.size(28.dp).clip(RoundedCornerShape(8.dp))
                )
                Spacer(Modifier.width(8.dp))
                Text("Nağme Özetim", color = Ink, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.width(10.dp))
                PeriodChip("Hafta", !monthly) { monthly = false }
                Spacer(Modifier.width(6.dp))
                PeriodChip("Ay", monthly) { monthly = true }
                Spacer(Modifier.weight(1f))
                IconBtn(R.drawable.ic_close, "Kapat", { vm.showSummary.value = false }, tint = Ink, size = 22.dp)
            }

            AnimatedContent(page to monthly, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "page",
                modifier = Modifier.weight(1f).fillMaxWidth()) { (p, _) ->
                Column(Modifier.fillMaxSize().padding(top = 36.dp)) {
                    if (sum.top.isEmpty()) EmptyPage(period) else when (p) {
                        0 -> MinutesPage(sum, period)
                        1 -> TopSongPage(sum, period)
                        2 -> TopFivePage(sum, period)
                        3 -> ArtistsPage(sum, period)
                        else -> EndPage(sum, period)
                    }
                }
            }

            Row(Modifier.fillMaxWidth().padding(bottom = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.weight(1f).height(50.dp).clip(RoundedCornerShape(25.dp)).background(Ink).clickable {
                        val text = buildString {
                            append("$period Nağme'de ${fmt(sum.minutes)} dakika müzik dinledim.")
                            sum.top.firstOrNull()?.let { append(" En çok: ${it.song.title} – ${it.song.artist}.") }
                            sum.artists.firstOrNull()?.let { append(" Favori sanatçım: ${it.name}.") }
                            append(" #NağmeÖzetim")
                        }
                        ctx.startActivity(Intent.createChooser(
                            Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Paylaş"
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    },
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Ic(R.drawable.ic_share, null, tint = Color.White, size = 18.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Paylaş", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
                Box(
                    Modifier.weight(1f).height(50.dp).clip(RoundedCornerShape(25.dp)).border(2.dp, Ink, RoundedCornerShape(25.dp))
                        .clickable { if (page < pageCount - 1) page++ else vm.showSummary.value = false },
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (page < pageCount - 1) "Sonraki" else "Bitti", color = Ink, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    }
}

@Composable
private fun PeriodChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.height(28.dp).clip(RoundedCornerShape(14.dp)).background(if (selected) Ink else Color(0x1F1A1206))
            .clickable(onClick = onClick).padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) { Text(text, color = if (selected) C.Accent else Ink, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun Big(text: String, size: Int = 88) =
    Text(text, color = Ink, fontSize = size.sp, lineHeight = (size * 0.98).sp, fontWeight = FontWeight.Black, letterSpacing = (-2).sp)

@Composable
private fun Label(text: String, size: Int = 18) = Text(text, color = Ink, fontSize = size.sp, fontWeight = FontWeight.SemiBold)

@Composable
private fun ColumnScope.EmptyPage(period: String) {
    Label("$period henüz özet yok")
    Spacer(Modifier.height(12.dp))
    Text("Biraz müzik dinle; en çok dinlediğin şarkılar, sanatçılar ve dakikaların burada birikir.",
        color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold, lineHeight = 28.sp)
}

@Composable
private fun ColumnScope.MinutesPage(s: Summary, period: String) {
    Label("$period tam")
    Big(fmt(s.minutes))
    Text("dakika dinledin", color = Ink, fontSize = 32.sp, fontWeight = FontWeight.Bold)
    if (s.prevMinutes > 0) {
        val diff = ((s.minutes - s.prevMinutes) * 100 / s.prevMinutes)
        Spacer(Modifier.height(12.dp))
        Box(Modifier.clip(RoundedCornerShape(14.dp)).background(Color(0x1F1A1206)).padding(horizontal = 12.dp, vertical = 6.dp)) {
            Text(
                if (diff >= 0) "Öncekinden %$diff fazla" else "Öncekinden %${-diff} az",
                color = Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold
            )
        }
    }
    Spacer(Modifier.weight(1f))
    val max = s.bars.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
    Row(Modifier.fillMaxWidth().height(170.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        s.bars.forEachIndexed { i, (label, m) ->
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                    Box(Modifier.fillMaxWidth().fillMaxHeight((m.toFloat() / max).coerceAtLeast(0.03f))
                        .clip(RoundedCornerShape(8.dp)).background(if (i == s.bestBar && m > 0) Ink else Ink2))
                }
                Spacer(Modifier.height(8.dp))
                Text(label, color = Ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
    s.bars.getOrNull(s.bestBar)?.takeIf { it.second > 0 }?.let { (l, m) ->
        Spacer(Modifier.height(12.dp))
        Text("En çok ${if (s.monthly) "$l haftasında" else l.lowercase() + " günü"} dinledin: ${m / 60} sa ${m % 60} dk",
            color = Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
    Spacer(Modifier.height(20.dp))
}

@Composable
private fun ColumnScope.TopSongPage(s: Summary, period: String) {
    val t = s.top.first()
    Label("$period en çok dinlediğin şarkı")
    Spacer(Modifier.height(24.dp))
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Cover(t.song, 250.dp, 20.dp)
    }
    Spacer(Modifier.height(24.dp))
    Text(t.song.title, color = Ink, fontSize = 32.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 34.sp)
    Text(t.song.artist, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(12.dp))
    Text("${t.count} kez · ${t.ms / 60000} dakika", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun ColumnScope.TopFivePage(s: Summary, period: String) {
    Label("$period ilk ${s.top.size} şarkın")
    Spacer(Modifier.height(20.dp))
    s.top.forEachIndexed { i, t ->
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${i + 1}", color = Ink, fontSize = 30.sp, fontWeight = FontWeight.Black, modifier = Modifier.width(36.dp))
            Cover(t.song, 58.dp, 10.dp)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(t.song.title, color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(t.song.artist, color = Ink, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text("${t.count}×", color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ColumnScope.ArtistsPage(s: Summary, period: String) {
    val a = s.artists.firstOrNull() ?: return EmptyPage(period)
    Label("$period en çok dinlediğin sanatçı")
    Spacer(Modifier.height(20.dp))
    Box(Modifier.size(140.dp).clip(CircleShape).background(Ink), contentAlignment = Alignment.Center) {
        Cover(a.song, 140.dp, 70.dp)
    }
    Spacer(Modifier.height(18.dp))
    Text(a.name, color = Ink, fontSize = 40.sp, fontWeight = FontWeight.Black, lineHeight = 42.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    Text("${a.ms / 60000} dakika", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(28.dp))
    s.artists.drop(1).forEachIndexed { i, x ->
        Text("${i + 2}. ${x.name}", color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 4.dp))
    }
}

@Composable
private fun ColumnScope.EndPage(s: Summary, period: String) {
    Label("$period özetin")
    Spacer(Modifier.height(18.dp))
    Stat(fmt(s.minutes), "dakika müzik")
    Stat("${s.songCount}", "farklı şarkı")
    Stat("${s.artistCount}", "sanatçı")
    if (s.streak > 1) Stat("${s.streak} gün", "art arda dinleme")
    Spacer(Modifier.weight(1f))
    Text("Reklamsız dinledim · Nağme", color = Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun Stat(big: String, small: String) {
    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.Bottom) {
        Text(big, color = Ink, fontSize = 44.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.width(10.dp))
        Text(small, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))
    }
}
