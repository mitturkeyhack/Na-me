package com.erkam.nagme.data

import android.content.Context
import com.erkam.nagme.yt.Net
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

data class LWord(val text: String, val start: Long)
data class LLine(val start: Long, val text: String, val words: List<LWord>)

/** synced: zamanlı satırlar; plain: zamansız metin; ikisi de boşsa bulunamadı */
data class LyricsResult(val synced: List<LLine>, val plain: String) {
    val found get() = synced.isNotEmpty() || plain.isNotBlank()
}

/** Şarkı sözleri: LRCLIB'den (ücretsiz, açık arşiv). Telefonda saklanır, internetsiz de görünür. */
object Lyrics {
    private val mem = ConcurrentHashMap<String, LyricsResult>()
    private const val UA = "Nagme/1.0 (https://github.com/mitturkeyhack/Na-me)"
    private const val RETRY_MS = 3L * 24 * 3600 * 1000

    private fun dir(ctx: Context) = File(ctx.filesDir, "sozler").apply { mkdirs() }

    suspend fun get(ctx: Context, song: Song): LyricsResult = withContext(Dispatchers.IO) {
        mem[song.id]?.let { return@withContext it }
        val f = File(dir(ctx), "${song.id}.json")
        if (f.exists()) runCatching {
            val o = JSONObject(f.readText())
            val r = LyricsResult(parse(o.optString("synced")), o.optString("plain"))
            if (r.found || System.currentTimeMillis() - o.optLong("at") < RETRY_MS) {
                mem[song.id] = r
                return@withContext r
            }
        }
        val (synced, plain) = runCatching { fetch(song) }.getOrDefault("" to "")
        runCatching {
            f.writeText(JSONObject().put("synced", synced).put("plain", plain).put("at", System.currentTimeMillis()).toString())
        }
        LyricsResult(parse(synced), plain).also { mem[song.id] = it }
    }

    private fun cleanTitle(t: String) = t
        .replace(Regex("\\s*[\\(\\[].*?[\\)\\]]"), "")
        .replace(Regex("(?i)\\s+(feat\\.?|ft\\.?)\\s.*$"), "")
        .trim()

    private fun cleanArtist(a: String) = a
        .split(",", "&", " ve ", " x ", " X ", " feat", " ft.").first()
        .replace(" - Topic", "").trim()

    private fun call(path: String, params: Map<String, String>): String? {
        val b = "https://lrclib.net$path".toHttpUrl().newBuilder()
        params.forEach { (k, v) -> b.addQueryParameter(k, v) }
        val req = okhttp3.Request.Builder().url(b.build()).header("User-Agent", UA).build()
        Net.client.newCall(req).execute().use { r -> return if (r.isSuccessful) r.body.string() else null }
    }

    private fun fetch(song: Song): Pair<String, String> {
        val title = cleanTitle(song.title)
        val artist = cleanArtist(song.artist)
        // 1) Tam eşleşme
        if (song.duration > 0) {
            call("/api/get", mapOf("track_name" to title, "artist_name" to artist, "duration" to song.duration.toString()))?.let {
                val o = JSONObject(it)
                val s = o.optString("syncedLyrics").takeIf { x -> x != "null" } ?: ""
                val p = o.optString("plainLyrics").takeIf { x -> x != "null" } ?: ""
                if (s.isNotBlank() || p.isNotBlank()) return s to p
            }
        }
        // 2) Arama: zamanlı olanı ve süresi en yakını seç
        val lists = listOfNotNull(
            call("/api/search", mapOf("track_name" to title, "artist_name" to artist)),
            call("/api/search", mapOf("q" to "$title $artist")),
        )
        for (raw in lists) {
            val a = JSONArray(raw)
            val items = (0 until a.length()).map { a.getJSONObject(it) }.filter { !it.optBoolean("instrumental") }
            if (items.isEmpty()) continue
            val best = items.sortedWith(compareBy<JSONObject>(
                { if (it.optString("syncedLyrics").let { s -> s.isBlank() || s == "null" }) 1 else 0 },
                { if (song.duration > 0) abs(it.optDouble("duration", 0.0) - song.duration) else 0.0 }
            )).first()
            if (song.duration > 0 && abs(best.optDouble("duration", 0.0) - song.duration) > 25) continue
            val s = best.optString("syncedLyrics").takeIf { it != "null" } ?: ""
            val p = best.optString("plainLyrics").takeIf { it != "null" } ?: ""
            if (s.isNotBlank() || p.isNotBlank()) return s to p
        }
        return "" to ""
    }

    private val lineTag = Regex("\\[(\\d{1,2}):(\\d{2})(?:[.:](\\d{1,3}))?]")
    private val wordTag = Regex("<(\\d{1,2}):(\\d{2})(?:[.:](\\d{1,3}))?>")

    private fun ms(m: MatchResult): Long {
        val (mm, ss, frac) = m.destructured
        val f = when (frac.length) { 0 -> 0; 1 -> frac.toInt() * 100; 2 -> frac.toInt() * 10; else -> frac.take(3).toInt() }
        return mm.toLong() * 60_000 + ss.toLong() * 1000 + f
    }

    /** LRC biçimini satırlara ve kelimelere ayırır. Kelime zamanı yoksa satır süresine göre tahmin edilir. */
    fun parse(lrc: String): List<LLine> {
        if (lrc.isBlank()) return emptyList()
        data class Raw(val start: Long, val body: String)
        val raws = ArrayList<Raw>()
        lrc.lineSequence().forEach { line ->
            val tags = lineTag.findAll(line).toList()
            if (tags.isEmpty()) return@forEach
            val body = line.substring(tags.last().range.last + 1).trim()
            tags.forEach { raws.add(Raw(ms(it), body)) }
        }
        raws.sortBy { it.start }
        val out = ArrayList<LLine>()
        for ((i, r) in raws.withIndex()) {
            val plainText = r.body.replace(wordTag, "").replace(Regex("\\s+"), " ").trim()
            if (plainText.isEmpty()) continue // boş satırlar (müzik arası)
            val next = raws.getOrNull(i + 1)?.start ?: (r.start + 5000)
            val tagged = wordTag.findAll(r.body).toList()
            val words = if (tagged.isNotEmpty()) {
                // Gelişmiş LRC: <00:12.34>kelime
                val parts = ArrayList<LWord>()
                var lastStart = r.start
                var idx = 0
                for (m in tagged) {
                    val before = r.body.substring(idx, m.range.first).trim()
                    if (before.isNotEmpty()) before.split(" ").filter { it.isNotBlank() }.forEach { parts.add(LWord(it, lastStart)) }
                    lastStart = ms(m)
                    idx = m.range.last + 1
                }
                r.body.substring(idx).trim().split(" ").filter { it.isNotBlank() }.forEach { parts.add(LWord(it, lastStart)) }
                parts
            } else {
                val ws = plainText.split(" ").filter { it.isNotBlank() }
                val span = ((next - r.start) * 0.85).toLong().coerceIn(600, 9000)
                val total = ws.sumOf { it.length + 1 }.coerceAtLeast(1)
                var acc = 0
                ws.map { w -> LWord(w, r.start + span * acc / total).also { acc += w.length + 1 } }
            }
            out.add(LLine(r.start, plainText, words))
        }
        return out
    }

    /** Cihaza kaydedilen şarkının sözlerini de önceden al (internetsiz görünsün). */
    suspend fun prefetch(ctx: Context, song: Song) { runCatching { get(ctx, song) } }
}
