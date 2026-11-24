package com.erkam.nagme.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class Settings(
    val crossfade: Int = 3,          // saniye: 0 = kapalı
    val resumeOnHeadset: Boolean = true,
    val autoplay: Boolean = true,    // sıra bitince benzer şarkılarla devam
    val bubble: Boolean = true,      // yüzen simge
    val highQuality: Boolean = true,
    val accent: Long = 0xFFF5B14C,   // tema rengi
    val amoled: Boolean = false,     // simsiyah arka plan
    val longSpeed: Float = 1f,       // uzun kayıtlarda (podcast, sohbet) hız
)

/** Uygulama kapanınca kalınan yer */
data class Session(val queue: List<Song>, val index: Int, val position: Long)

/** Uzun kayıtta kalınan yer */
data class Resume(val song: Song, val position: Long, val duration: Long, val at: Long)

/** Bir dinleme kaydı (özet için) */
data class Play(val song: Song, val at: Long, val ms: Long)

const val LONG_MS = 20L * 60 * 1000 // 20 dakikadan uzunsa "uzun kayıt"

/** Beğenilenler, geçmiş, listeler, cihazdakiler ve ayarlar. Hepsi telefonda saklanır. */
object Store {
    private lateinit var dir: File
    private lateinit var prefs: SharedPreferences
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()

    private val _settings = MutableStateFlow(Settings())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    val liked = MutableStateFlow<List<Song>>(emptyList())
    val history = MutableStateFlow<List<Song>>(emptyList())
    val playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val downloads = MutableStateFlow<List<Song>>(emptyList())
    val homeCache = MutableStateFlow<List<Song>>(emptyList())
    val session = MutableStateFlow<Session?>(null)
    val resumes = MutableStateFlow<List<Resume>>(emptyList())
    val plays = MutableStateFlow<List<Play>>(emptyList())

    fun init(ctx: Context) {
        dir = ctx.filesDir
        prefs = ctx.getSharedPreferences("ayarlar", Context.MODE_PRIVATE)
        _settings.value = Settings(
            crossfade = prefs.getInt("crossfade", 3),
            resumeOnHeadset = prefs.getBoolean("resumeOnHeadset", true),
            autoplay = prefs.getBoolean("autoplay", true),
            bubble = prefs.getBoolean("bubble", true),
            highQuality = prefs.getBoolean("highQuality", true),
            accent = prefs.getLong("accent", 0xFFF5B14C),
            amoled = prefs.getBoolean("amoled", false),
            longSpeed = prefs.getFloat("longSpeed", 1f),
        )
        val f = File(dir, "kitaplik.json")
        if (f.exists()) runCatching {
            val o = JSONObject(f.readText())
            liked.value = Song.listFromJson(o.optJSONArray("liked"))
            history.value = Song.listFromJson(o.optJSONArray("history"))
            downloads.value = Song.listFromJson(o.optJSONArray("downloads"))
            homeCache.value = Song.listFromJson(o.optJSONArray("home"))
            val pl = o.optJSONArray("playlists") ?: JSONArray()
            playlists.value = (0 until pl.length()).map { Playlist.fromJson(pl.getJSONObject(it)) }
            o.optJSONObject("session")?.let { se ->
                session.value = Session(Song.listFromJson(se.optJSONArray("q")), se.optInt("i"), se.optLong("p"))
            }
            val rs = o.optJSONArray("resumes") ?: JSONArray()
            resumes.value = (0 until rs.length()).mapNotNull { i ->
                runCatching {
                    val r = rs.getJSONObject(i)
                    Resume(Song.fromJson(r.getJSONObject("s")), r.getLong("p"), r.getLong("d"), r.getLong("at"))
                }.getOrNull()
            }
        }
        val pf = File(dir, "dinlemeler.json")
        if (pf.exists()) runCatching {
            val a = JSONArray(pf.readText())
            plays.value = (0 until a.length()).mapNotNull { i ->
                runCatching {
                    val r = a.getJSONObject(i)
                    Play(Song.fromJson(r.getJSONObject("s")), r.getLong("at"), r.getLong("ms"))
                }.getOrNull()
            }
        }
    }

    fun updateSettings(block: (Settings) -> Settings) {
        val s = block(_settings.value)
        _settings.value = s
        prefs.edit()
            .putInt("crossfade", s.crossfade)
            .putBoolean("resumeOnHeadset", s.resumeOnHeadset)
            .putBoolean("autoplay", s.autoplay)
            .putBoolean("bubble", s.bubble)
            .putBoolean("highQuality", s.highQuality)
            .putLong("accent", s.accent)
            .putBoolean("amoled", s.amoled)
            .putFloat("longSpeed", s.longSpeed)
            .apply()
    }

    private fun save() {
        scope.launch {
            lock.withLock {
                val o = JSONObject()
                    .put("liked", Song.listToJson(liked.value))
                    .put("history", Song.listToJson(history.value))
                    .put("downloads", Song.listToJson(downloads.value))
                    .put("home", Song.listToJson(homeCache.value))
                    .put("playlists", JSONArray().apply { playlists.value.forEach { put(it.toJson()) } })
                    .put("resumes", JSONArray().apply {
                        resumes.value.forEach { r ->
                            put(JSONObject().put("s", r.song.toJson()).put("p", r.position).put("d", r.duration).put("at", r.at))
                        }
                    })
                session.value?.let { se ->
                    o.put("session", JSONObject().put("q", Song.listToJson(se.queue)).put("i", se.index).put("p", se.position))
                }
                val tmp = File(dir, "kitaplik.json.tmp")
                tmp.writeText(o.toString())
                tmp.renameTo(File(dir, "kitaplik.json"))
            }
        }
    }

    private fun savePlays() {
        scope.launch {
            lock.withLock {
                val a = JSONArray()
                plays.value.forEach { a.put(JSONObject().put("s", it.song.toJson()).put("at", it.at).put("ms", it.ms)) }
                val tmp = File(dir, "dinlemeler.json.tmp")
                tmp.writeText(a.toString())
                tmp.renameTo(File(dir, "dinlemeler.json"))
            }
        }
    }

    /** En az 20 saniye dinlenen şarkılar özete yazılır. */
    fun addPlay(s: Song, ms: Long) {
        if (ms < 20_000 || s.id.isEmpty()) return
        val now = System.currentTimeMillis()
        val cutoff = now - 400L * 24 * 3600 * 1000
        plays.update { l -> (l.filter { it.at > cutoff } + Play(s, now, ms)).takeLast(20000) }
        savePlays()
    }

    fun saveSession(se: Session?) {
        session.value = se; save()
    }

    fun resumeFor(id: String) = resumes.value.firstOrNull { it.song.id == id }

    fun setResume(s: Song, pos: Long, dur: Long) {
        if (dur < LONG_MS) return
        resumes.update { l ->
            val rest = l.filter { it.song.id != s.id }
            // Bitmek üzereyse ya da hiç başlamadıysa listeden çıkar
            if (pos < 30_000 || dur - pos < 60_000) rest
            else (listOf(Resume(s, pos, dur, System.currentTimeMillis())) + rest).take(30)
        }
        save()
    }

    fun removeResume(id: String) {
        resumes.update { l -> l.filter { it.song.id != id } }
        save()
    }

    fun isLiked(id: String) = liked.value.any { it.id == id }

    fun toggleLike(s: Song) {
        liked.update { l -> if (l.any { it.id == s.id }) l.filter { it.id != s.id } else listOf(s) + l }
        save()
    }

    fun addHistory(s: Song) {
        if (s.id.isEmpty()) return
        history.update { l -> (listOf(s) + l.filter { it.id != s.id }).take(300) }
        save()
    }

    fun clearHistory() {
        history.value = emptyList(); save()
    }

    fun setHome(l: List<Song>) {
        homeCache.value = l; save()
    }

    fun addDownload(s: Song) {
        downloads.update { l -> listOf(s) + l.filter { it.id != s.id } }
        save()
    }

    fun removeDownload(id: String) {
        downloads.update { l -> l.filter { it.id != id } }
        save()
    }

    fun createPlaylist(name: String, first: Song? = null): Playlist {
        val p = Playlist(System.currentTimeMillis().toString(), name.trim().ifEmpty { "Yeni liste" }, listOfNotNull(first))
        playlists.update { it + p }
        save()
        return p
    }

    fun addToPlaylist(pid: String, s: Song) {
        playlists.update { l -> l.map { if (it.id == pid && it.songs.none { x -> x.id == s.id }) it.copy(songs = it.songs + s) else it } }
        save()
    }

    fun removeFromPlaylist(pid: String, sid: String) {
        playlists.update { l -> l.map { if (it.id == pid) it.copy(songs = it.songs.filter { x -> x.id != sid }) else it } }
        save()
    }

    fun deletePlaylist(pid: String) {
        playlists.update { l -> l.filter { it.id != pid } }
        save()
    }

    fun renamePlaylist(pid: String, name: String) {
        playlists.update { l -> l.map { if (it.id == pid) it.copy(name = name) else it } }
        save()
    }
}
