package com.erkam.nagme.play

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.erkam.nagme.MainActivity
import com.erkam.nagme.R
import com.erkam.nagme.data.Downloads
import com.erkam.nagme.data.Song
import com.erkam.nagme.data.Store
import com.erkam.nagme.yt.Net
import com.erkam.nagme.yt.Yt
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

class PlaybackService : MediaSessionService() {

    companion object {
        const val CMD_SLEEP = "nagme.sleep" // args: "min" (Int). 0 = iptal, -1 = şarkı bitince
        private var cache: SimpleCache? = null

        @Synchronized
        fun cache(ctx: Context): SimpleCache = cache ?: ctx.applicationContext.let { app ->
            SimpleCache(
                File(app.cacheDir, "ses"),
                LeastRecentlyUsedCacheEvictor(400L * 1024 * 1024),
                StandaloneDatabaseProvider(app)
            )
        }.also { cache = it }

        fun ytUri(id: String): Uri = Uri.parse(Yt.watchUrl(id))
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var player: ExoPlayer
    private var session: MediaSession? = null
    private lateinit var bubble: Bubble

    private var fadeInActive = false
    private var pausedByNoisy = false
    private var loadingMore = false
    private var sleepJob: Job? = null
    private val retried = HashSet<String>()

    override fun onCreate() {
        super.onCreate()

        // Ses bağlantısı çalma anında çözülür; çalınan şarkılar önbelleğe alınır (tekrar dinlemede anında açılır)
        val resolver = ResolvingDataSource.Resolver { spec ->
            val id = spec.key ?: Yt.idFromUrl(spec.uri.toString())
            if (id == null || spec.uri.scheme == "file") spec
            else spec.withUri(Uri.parse(Yt.streamUrlBlocking(id, Store.settings.value.highQuality)))
        }
        val http = OkHttpDataSource.Factory(Net.client)
        val cached = CacheDataSource.Factory()
            .setCache(cache(this))
            .setUpstreamDataSourceFactory(ResolvingDataSource.Factory(http, resolver))
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        val dataSource = DefaultDataSource.Factory(this, cached)

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSource))
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                true
            )
            .setHandleAudioBecomingNoisy(true) // kulaklık çıkınca durdur
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        player.addListener(listener)

        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).putExtra("player", true).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        session = MediaSession.Builder(this, player)
            .setSessionActivity(open)
            .setCallback(callback)
            .build()

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this).build().apply { setSmallIcon(R.drawable.ic_note) }
        )

        bubble = Bubble(this, player)
        bubble.start()

        registerHeadsetCallback()
        startFadeLoop()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /** Sıradaki öğeye çalınabilir bağlantı ekler: cihazdaysa dosya, değilse YouTube. */
    private fun resolve(item: MediaItem): MediaItem {
        val id = item.mediaId
        val file = Downloads.fileFor(id)
        val b = item.buildUpon()
        if (file != null) {
            b.setUri(Uri.fromFile(file))
            Store.downloads.value.firstOrNull { it.id == id }?.thumb?.takeIf { it.startsWith("file:") }?.let {
                b.setMediaMetadata(item.mediaMetadata.buildUpon().setArtworkUri(Uri.parse(it)).build())
            }
        } else {
            b.setUri(ytUri(id)).setCustomCacheKey(id)
        }
        return b.build()
    }

    private val callback = object : MediaSession.Callback {
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            val cmds = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(SessionCommand(CMD_SLEEP, Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(cmds)
                .build()
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> =
            Futures.immediateFuture(mediaItems.map { resolve(it) }.toMutableList())

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction == CMD_SLEEP) setSleep(args.getInt("min", 0))
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }

    private fun setSleep(min: Int) {
        sleepJob?.cancel()
        player.pauseAtEndOfMediaItems = false
        when {
            min > 0 -> sleepJob = scope.launch {
                delay(min * 60_000L)
                // Son 10 saniyede sesi yavaşça kıs
                for (i in 10 downTo 1) { player.volume = i / 10f; delay(1000) }
                player.pause()
                player.volume = 1f
            }
            min == -1 -> player.pauseAtEndOfMediaItems = true
        }
    }

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            mediaItem ?: return
            Store.addHistory(Song.from(mediaItem))
            fadeInActive = reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && Store.settings.value.crossfade > 0
            if (!fadeInActive) player.volume = 1f
            maybeLoadMore()
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY) pausedByNoisy = true
            else if (playWhenReady) pausedByNoisy = false
            if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM && player.pauseAtEndOfMediaItems) {
                player.pauseAtEndOfMediaItems = false // "şarkı bitince" uyku zamanlayıcısı bir kez çalışır
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            val id = player.currentMediaItem?.mediaId ?: return
            val cause = error.cause
            val expired = cause is HttpDataSource.InvalidResponseCodeException || error.errorCode in 2000..2999
            if (expired && retried.add(id)) {
                // Bağlantının süresi dolmuş olabilir: yenisini al, kaldığı yerden devam et
                Yt.invalidate(id)
                val pos = player.currentPosition
                player.prepare()
                player.seekTo(pos)
                player.play()
            } else if (player.hasNextMediaItem()) {
                player.seekToNextMediaItem()
                player.prepare()
                player.play()
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) retried.clear()
        }
    }

    /** Sıra bitmek üzereyse benzer şarkılarla devam et (radyo). */
    private fun maybeLoadMore() {
        if (!Store.settings.value.autoplay || loadingMore) return
        if (player.repeatMode != Player.REPEAT_MODE_OFF) return
        if (player.currentMediaItemIndex < player.mediaItemCount - 2) return
        val current = player.currentMediaItem?.mediaId ?: return
        loadingMore = true
        scope.launch {
            runCatching {
                val inQueue = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }.toHashSet()
                val recent = Store.history.value.take(25).map { it.id }.toHashSet()
                val more = Yt.related(current)
                    .filter { it.id !in inQueue }
                    .sortedBy { if (it.id in recent) 1 else 0 } // yakında dinlenenleri sona at
                    .distinctBy { it.title.lowercase() }
                    .take(12)
                player.addMediaItems(more.map { resolve(it.toMediaItem()) })
            }
            loadingMore = false
        }
    }

    /** Yumuşak geçiş: şarkı biterken ses yavaşça kısılır, sonraki yavaşça açılır. */
    private fun startFadeLoop() {
        scope.launch {
            while (isActive) {
                delay(100)
                if (sleepJob?.isActive == true && player.volume < 1f && !fadeInActive) continue
                val fade = Store.settings.value.crossfade * 1000L
                if (fade <= 0 || !player.isPlaying) {
                    if (fade <= 0 && player.volume != 1f && sleepJob?.isActive != true) player.volume = 1f
                    continue
                }
                val dur = player.duration
                val pos = player.currentPosition
                var v = 1f
                if (dur != C.TIME_UNSET && dur > fade * 3 && player.hasNextMediaItem() && !player.pauseAtEndOfMediaItems) {
                    val rem = dur - pos
                    if (rem < fade) v = (rem.toFloat() / fade).coerceIn(0f, 1f)
                }
                if (fadeInActive) {
                    if (pos < fade) v = minOf(v, (pos.toFloat() / fade).coerceIn(0.05f, 1f)) else fadeInActive = false
                }
                if (kotlin.math.abs(player.volume - v) > 0.01f) player.volume = v
            }
        }
    }

    /** Kulaklık takılınca kaldığı yerden devam. */
    private val headsetTypes = buildSet {
        add(AudioDeviceInfo.TYPE_WIRED_HEADSET); add(AudioDeviceInfo.TYPE_WIRED_HEADPHONES)
        add(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP); add(AudioDeviceInfo.TYPE_USB_HEADSET)
        if (android.os.Build.VERSION.SDK_INT >= 31) { add(AudioDeviceInfo.TYPE_BLE_HEADSET); add(AudioDeviceInfo.TYPE_BLE_SPEAKER) }
    }
    private var headsetReady = false
    private val headsetCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            if (!headsetReady) return
            if (addedDevices.any { it.isSink && it.type in headsetTypes } &&
                pausedByNoisy && Store.settings.value.resumeOnHeadset && player.mediaItemCount > 0
            ) {
                pausedByNoisy = false
                Handler(Looper.getMainLooper()).postDelayed({ player.play() }, 700)
            }
        }
    }

    private fun registerHeadsetCallback() {
        val am = getSystemService(AudioManager::class.java)
        am.registerAudioDeviceCallback(headsetCallback, Handler(Looper.getMainLooper()))
        // Kayıt anında mevcut cihazlar da bildirilir; onları yok say
        Handler(Looper.getMainLooper()).postDelayed({ headsetReady = true }, 1500)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        runCatching { getSystemService(AudioManager::class.java).unregisterAudioDeviceCallback(headsetCallback) }
        bubble.stop()
        scope.cancel()
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }
}
