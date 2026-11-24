package com.erkam.nagme.ui

import android.app.Application
import android.content.ComponentName
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.erkam.nagme.App
import com.erkam.nagme.data.Song
import com.erkam.nagme.data.Store
import com.erkam.nagme.play.PlaybackService
import com.erkam.nagme.yt.Yt
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class Tab { Home, Search, Library }

data class Msg(val text: String, val action: String? = null, val onAction: (() -> Unit)? = null)

/** Açık olan liste ekranı */
sealed interface Collection {
    data object Liked : Collection
    data object Downloads : Collection
    data object History : Collection
    data class User(val id: String) : Collection
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    // --- Gezinme ---
    val tab = MutableStateFlow(Tab.Home)
    val showPlayer = MutableStateFlow(false)
    val showSettings = MutableStateFlow(false)
    val openCollection = MutableStateFlow<Collection?>(null)

    // --- Oynatıcı durumu ---
    private var controller: MediaController? = null
    val current = MutableStateFlow<Song?>(null)
    val isPlaying = MutableStateFlow(false)
    val buffering = MutableStateFlow(false)
    val position = MutableStateFlow(0L)
    val duration = MutableStateFlow(0L)
    val queue = MutableStateFlow<List<Song>>(emptyList())
    val queueIndex = MutableStateFlow(0)
    val shuffle = MutableStateFlow(false)
    val repeat = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val sleepUntil = MutableStateFlow(0L) // 0 = yok, -1 = şarkı bitince
    val message = MutableStateFlow<Msg?>(null)
    val radioSeed = MutableStateFlow<Song?>(null)
    val showLyrics = MutableStateFlow(false)
    val showSummary = MutableStateFlow(false)
    private var lastId: String? = null
    val controllerReady = MutableStateFlow(false)

    // --- Ana sayfa ---
    val mood = MutableStateFlow<String?>(null)
    val forYou = MutableStateFlow(Store.homeCache.value)
    val homeLoading = MutableStateFlow(false)
    private var homeLoadedAt = 0L

    // --- Arama ---
    val query = MutableStateFlow("")
    val suggestions = MutableStateFlow<List<String>>(emptyList())
    val results = MutableStateFlow<List<Song>>(emptyList())
    val searching = MutableStateFlow(false)
    val searchError = MutableStateFlow<String?>(null)
    private var suggestJob: Job? = null

    init {
        connect()
        loadHome()
    }

    private fun connect() {
        val ctx = getApplication<Application>()
        val token = SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))
        val future = MediaController.Builder(ctx, token).buildAsync()
        future.addListener({
            val c = runCatching { future.get() }.getOrNull() ?: return@addListener
            controller = c
            controllerReady.value = true
            c.addListener(object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) = sync()
            })
            sync()
        }, ContextCompat.getMainExecutor(ctx))

        viewModelScope.launch {
            while (isActive) {
                controller?.let {
                    position.value = it.currentPosition
                    val d = it.duration
                    if (d > 0) duration.value = d
                }
                delay(if (isPlaying.value) 400 else 1000)
            }
        }
    }

    private fun sync() {
        val c = controller ?: return
        current.value = c.currentMediaItem?.let { Song.from(it) }
        isPlaying.value = c.isPlaying
        buffering.value = c.playbackState == Player.STATE_BUFFERING
        shuffle.value = c.shuffleModeEnabled
        repeat.value = c.repeatMode
        queueIndex.value = c.currentMediaItemIndex
        queue.value = (0 until c.mediaItemCount).map { Song.from(c.getMediaItemAt(it)) }
        val d = c.duration
        duration.value = if (d > 0) d else (current.value?.duration ?: 0) * 1000
        position.value = c.currentPosition
        val id = current.value?.id
        if (id != null && id != lastId) {
            lastId = id
            Store.resumeFor(id)?.let { r ->
                if (r.position > 30_000) message.value = Msg(
                    "Kaldığın yerden devam ediliyor · ${com.erkam.nagme.data.formatTime(r.position / 1000)}", "Baştan"
                ) { controller?.seekTo(0) }
            }
        }
        if (sleepUntil.value > 0 && System.currentTimeMillis() > sleepUntil.value) sleepUntil.value = 0
    }

    // --- Oynatma ---
    fun play(list: List<Song>, index: Int = 0, shuffled: Boolean = false) {
        val c = controller ?: run { message.value = Msg("Oynatıcı hazırlanıyor, tekrar dene"); return }
        if (list.isEmpty()) return
        setRadio(false)
        c.shuffleModeEnabled = shuffled
        val start = if (shuffled) list.indices.random() else index.coerceIn(list.indices)
        c.setMediaItems(list.map { it.toMediaItem() }, start, 0)
        c.prepare()
        c.play()
    }

    /** Tek şarkı: çalar, ardından benzer şarkılar otomatik eklenir. */
    fun playSong(s: Song) = play(listOf(s))

    fun playNext(s: Song) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) return playSong(s)
        c.addMediaItem(c.currentMediaItemIndex + 1, s.toMediaItem())
        message.value = Msg("Sıradaki olarak eklendi")
    }

    fun addToQueue(s: Song) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) return playSong(s)
        c.addMediaItem(s.toMediaItem())
        message.value = Msg("Sıraya eklendi")
    }

    private fun setRadio(on: Boolean) {
        if (!on) radioSeed.value = null
        controller?.sendCustomCommand(SessionCommand(PlaybackService.CMD_RADIO, Bundle.EMPTY), Bundle().apply { putBoolean("on", on) })
    }

    /** Bu şarkının radyosu: şarkı + benzerleri, sıra bitince yenileri eklenir. */
    fun startRadio(s: Song) {
        val c = controller ?: return
        play(listOf(s))
        radioSeed.value = s
        setRadio(true)
        message.value = Msg("Radyo: ${s.title} ve benzerleri")
        viewModelScope.launch {
            val more = runCatching { Yt.related(s.id) }.getOrDefault(emptyList())
                .distinctBy { it.title.lowercase() }.take(15)
            if (c.currentMediaItem?.mediaId == s.id && c.mediaItemCount <= 1) c.addMediaItems(more.map { it.toMediaItem() })
        }
    }

    /** Uygulama kapanmadan önce kalınan yer */
    fun restoreSession() {
        val c = controller ?: return
        val se = Store.session.value ?: return
        if (se.queue.isEmpty()) return
        setRadio(false)
        c.setMediaItems(se.queue.map { it.toMediaItem() }, se.index.coerceIn(se.queue.indices), se.position)
        c.prepare()
        c.play()
    }

    fun resumeLong(r: com.erkam.nagme.data.Resume) {
        val c = controller ?: return
        setRadio(false)
        c.setMediaItems(listOf(r.song.toMediaItem()), 0, r.position)
        c.prepare()
        c.play()
    }

    fun setLongSpeed(v: Float) = Store.updateSettings { it.copy(longSpeed = v) }

    /** Özet açılınca çalan şarkıyı durdur, kapanınca devam et */
    private var pausedForSummary = false
    fun pauseForSummary() {
        val c = controller ?: return
        if (c.isPlaying) { c.pause(); pausedForSummary = true }
    }
    fun resumeAfterSummary() {
        if (pausedForSummary) controller?.play()
        pausedForSummary = false
    }

    fun toggle() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            c.play()
        }
    }

    /** Anlık konum (sözlerin kelime kelime akması için) */
    fun livePosition(): Long = controller?.currentPosition ?: position.value

    fun next() = controller?.seekToNext()
    fun prev() = controller?.seekToPrevious()
    fun seek(ms: Long) = controller?.seekTo(ms)
    fun skipTo(i: Int) = controller?.let { it.seekTo(i, 0); it.play() }
    fun removeFromQueue(i: Int) = controller?.removeMediaItem(i)
    fun moveInQueue(from: Int, to: Int) = controller?.moveMediaItem(from, to)

    fun toggleShuffle() { controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled } }

    fun cycleRepeat() {
        controller?.let {
            it.repeatMode = when (it.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }
    }

    fun sleep(min: Int) {
        val c = controller ?: return
        c.sendCustomCommand(SessionCommand(PlaybackService.CMD_SLEEP, Bundle.EMPTY), Bundle().apply { putInt("min", min) })
        sleepUntil.value = when {
            min > 0 -> System.currentTimeMillis() + min * 60_000L
            min == -1 -> -1
            else -> 0
        }
        message.value = Msg(when {
            min > 0 -> "$min dakika sonra duracak"
            min == -1 -> "Bu şarkı bitince duracak"
            else -> "Uyku zamanlayıcı kapatıldı"
        })
    }

    // --- Ana sayfa: dinlediklerine göre öneriler ---
    val moods = linkedMapOf(
        "Sakin" to "sakin akustik türkçe şarkılar",
        "Yolda" to "yolculuk şarkıları türkçe",
        "Odak" to "odaklanma enstrümantal müzik",
        "Türkü" to "en sevilen türküler",
        "Enerjik" to "enerjik türkçe pop",
    )

    fun selectMood(m: String?) {
        mood.value = m
        loadHome(force = true)
    }

    fun loadHome(force: Boolean = false) {
        if (homeLoading.value) return
        if (!force && forYou.value.isNotEmpty() && System.currentTimeMillis() - homeLoadedAt < 20 * 60_000) return
        if (!App.online.value) return
        homeLoading.value = true
        viewModelScope.launch {
            runCatching {
                val m = mood.value
                val list = if (m != null) {
                    Yt.search(moods[m] ?: m)
                } else {
                    recommend()
                }
                if (list.isNotEmpty()) {
                    forYou.value = list
                    if (m == null) Store.setHome(list)
                    homeLoadedAt = System.currentTimeMillis()
                }
            }
            homeLoading.value = false
        }
    }

    private suspend fun recommend(): List<Song> {
        val hist = Store.history.value
        val liked = Store.liked.value
        val seeds = (hist.take(6) + liked.take(4).shuffled()).distinctBy { it.id }.shuffled().take(4)
        if (seeds.isEmpty()) return Yt.search("türkçe pop en çok dinlenenler")
        val heard = hist.take(40).map { it.id }.toHashSet()
        val lists = seeds.map { s -> viewModelScope.async { runCatching { Yt.related(s.id) }.getOrDefault(emptyList()) } }.awaitAll()
        // Listeleri iç içe geçir: her tohumdan sırayla bir şarkı
        val out = ArrayList<Song>()
        val max = lists.maxOfOrNull { it.size } ?: 0
        for (i in 0 until max) for (l in lists) l.getOrNull(i)?.let { out.add(it) }
        val fresh = out.filter { it.id !in heard }.distinctBy { it.id }.distinctBy { it.title.lowercase().trim() }
        return (fresh.ifEmpty { out.distinctBy { it.id } }).take(24)
    }

    // --- Arama ---
    fun onQuery(q: String) {
        query.value = q
        suggestJob?.cancel()
        if (q.isBlank()) { suggestions.value = emptyList(); return }
        suggestJob = viewModelScope.launch {
            delay(250)
            suggestions.value = Yt.suggestions(q)
        }
    }

    fun search(q: String = query.value) {
        val text = q.trim()
        if (text.isEmpty()) return
        query.value = text
        suggestions.value = emptyList()
        suggestJob?.cancel()
        searching.value = true
        searchError.value = null
        viewModelScope.launch {
            runCatching { Yt.search(text) }
                .onSuccess {
                    results.value = it
                    if (it.isEmpty()) searchError.value = "Sonuç bulunamadı"
                }
                .onFailure {
                    searchError.value = if (!App.online.value) "İnternet yok. Cihazdaki şarkılarını Kitaplık'ta dinleyebilirsin."
                    else "Arama yapılamadı, tekrar dene"
                }
            searching.value = false
        }
    }

    override fun onCleared() {
        controller?.release()
        super.onCleared()
    }
}

fun MediaItem.songId() = mediaId
