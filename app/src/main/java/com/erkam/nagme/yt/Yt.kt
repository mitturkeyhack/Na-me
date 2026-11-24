package com.erkam.nagme.yt

import com.erkam.nagme.data.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import java.util.concurrent.ConcurrentHashMap

/** YouTube'dan sadece ses: arama, ses bağlantısı, benzer şarkılar. */
object Yt {
    private val yt get() = ServiceList.YouTube

    fun init() {
        NewPipe.init(NpDownloader(), Localization("tr", "TR"), ContentCountry("TR"))
    }

    private class Cached(val info: StreamInfo, val at: Long)

    private val infoCache = ConcurrentHashMap<String, Cached>()
    private const val TTL = 4L * 60 * 60 * 1000 // bağlantılar ~6 saatte bozulur

    fun watchUrl(id: String) = "https://www.youtube.com/watch?v=$id"

    private fun info(id: String): StreamInfo {
        infoCache[id]?.let { if (System.currentTimeMillis() - it.at < TTL) return it.info }
        val i = StreamInfo.getInfo(yt, watchUrl(id))
        infoCache[id] = Cached(i, System.currentTimeMillis())
        return i
    }

    fun invalidate(id: String) {
        infoCache.remove(id)
    }

    private fun pickAudio(list: List<AudioStream>, high: Boolean): AudioStream? {
        val all = list.filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
        // Dublajlı ses varsa orijinal sesi seç
        val ok = all.filter { it.audioTrackType == null || it.audioTrackType.toString() == "ORIGINAL" }.ifEmpty { all }
        if (ok.isEmpty()) return null
        return if (high) ok.maxByOrNull { it.averageBitrate }
        else ok.filter { it.averageBitrate >= 48 }.minByOrNull { it.averageBitrate } ?: ok.minByOrNull { it.averageBitrate }
    }

    /** Engelleyici çağrı: oynatıcının yükleme iş parçacığından çağrılır. */
    fun streamUrlBlocking(id: String, high: Boolean): String {
        val i = info(id)
        val a = pickAudio(i.audioStreams, high) ?: throw IllegalStateException("Ses bulunamadı")
        return a.content
    }

    suspend fun streamUrl(id: String, high: Boolean): String = withContext(Dispatchers.IO) { streamUrlBlocking(id, high) }

    suspend fun search(q: String): List<Song> = withContext(Dispatchers.IO) {
        val music = runCatching {
            val qh = yt.searchQHFactory.fromQuery(q, listOf(YoutubeSearchQueryHandlerFactory.MUSIC_SONGS), "")
            SearchInfo.getInfo(yt, qh).relatedItems.filterIsInstance<StreamInfoItem>().mapNotNull { toSong(it, true) }
        }.getOrDefault(emptyList())
        if (music.size >= 5) return@withContext music
        val qh = yt.searchQHFactory.fromQuery(q, listOf(YoutubeSearchQueryHandlerFactory.VIDEOS), "")
        val videos = SearchInfo.getInfo(yt, qh).relatedItems.filterIsInstance<StreamInfoItem>()
            .filter { it.streamType != StreamType.LIVE_STREAM }
            .mapNotNull { toSong(it, false) }
        (music + videos).distinctBy { it.id }
    }

    suspend fun suggestions(q: String): List<String> = withContext(Dispatchers.IO) {
        runCatching { yt.suggestionExtractor.suggestionList(q).take(6) }.getOrDefault(emptyList())
    }

    /** Bir şarkıya benzer şarkılar (YouTube'un "sıradaki" önerileri). */
    suspend fun related(id: String): List<Song> = withContext(Dispatchers.IO) {
        info(id).relatedItems.filterIsInstance<StreamInfoItem>()
            .filter { it.streamType != StreamType.LIVE_STREAM }
            .filter { it.duration <= 0 || it.duration in 60..720 }
            .mapNotNull { toSong(it, false) }
            .filter { it.id != id }
    }

    fun idFromUrl(url: String): String? {
        Regex("[?&]v=([A-Za-z0-9_-]{11})").find(url)?.let { return it.groupValues[1] }
        Regex("youtu\\.be/([A-Za-z0-9_-]{11})").find(url)?.let { return it.groupValues[1] }
        Regex("/shorts/([A-Za-z0-9_-]{11})").find(url)?.let { return it.groupValues[1] }
        return null
    }

    private fun toSong(it: StreamInfoItem, music: Boolean): Song? {
        val id = idFromUrl(it.url) ?: return null
        var thumb = it.thumbnails.maxByOrNull { t -> t.height }?.url
            ?: "https://i.ytimg.com/vi/$id/hqdefault.jpg"
        // YouTube Music kare kapakları küçük gelir: büyük boyutunu iste
        if (thumb.contains("googleusercontent.com")) thumb = thumb.replace(Regex("=w\\d+-h\\d+[^&]*$"), "=w544-h544-l90-rj")
        val uploader = cleanArtist(it.uploaderName ?: "")
        if (music) return Song(id, it.name, uploader, it.duration.coerceAtLeast(0), thumb)
        val (title, artist) = splitTitle(it.name, uploader)
        return Song(id, title, artist, it.duration.coerceAtLeast(0), thumb)
    }

    private fun cleanArtist(s: String) = s
        .replace(" - Topic", "").replace(" - Konu", "")
        .replace(Regex("VEVO$", RegexOption.IGNORE_CASE), "").trim()

    private val junk = Regex(
        "\\s*[\\(\\[【](?:[^\\)\\]】]*(?:official|resmi|video|klip|audio|lyric|sözleri|şarkı sözü|hd|4k|remaster|music|müzik|visualizer)[^\\)\\]】]*)[\\)\\]】]",
        RegexOption.IGNORE_CASE
    )

    fun splitTitle(raw: String, uploader: String): Pair<String, String> {
        var t = raw.replace(junk, "")
        t = t.split(" | ", " ｜ ", " // ").first()
        t = t.replace(Regex("\\s*#\\S+"), "").trim()
        val dash = Regex("\\s[-–—]\\s").find(t)
        if (dash != null) {
            val a = t.substring(0, dash.range.first).trim()
            val b = t.substring(dash.range.last + 1).trim()
            if (a.isNotEmpty() && b.isNotEmpty()) return b.trim('"', '\'') to a
        }
        return t.ifEmpty { raw } to uploader
    }
}
