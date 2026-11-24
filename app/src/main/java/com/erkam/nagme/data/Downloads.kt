package com.erkam.nagme.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.erkam.nagme.R
import com.erkam.nagme.yt.Net
import com.erkam.nagme.yt.Yt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicLong

/** Şarkıyı parça parça, aynı anda birkaç bağlantıyla indirir (hızlı). İnternetsiz çalar. */
object Downloads {
    private lateinit var app: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queue = Semaphore(2) // aynı anda 2 şarkı
    private const val CHUNK = 1L shl 20 // 1 MB
    private const val PARALLEL = 4
    private const val CHANNEL = "indirme"
    private const val NOTIF_ID = 4200

    /** id -> 0..1 ilerleme */
    val progress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val failed = MutableStateFlow<Set<String>>(emptySet())

    fun init(ctx: Context) {
        app = ctx.applicationContext
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = app.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "İndirmeler", NotificationManager.IMPORTANCE_LOW))
        }
    }

    private val musicDir get() = File(app.filesDir, "muzik").apply { mkdirs() }
    private val thumbDir get() = File(app.filesDir, "kapak").apply { mkdirs() }

    fun fileFor(id: String): File? = File(musicDir, "$id.ses").takeIf { it.exists() && it.length() > 0 }

    fun isDownloaded(id: String) = Store.downloads.value.any { it.id == id } && fileFor(id) != null

    fun totalBytes(): Long = musicDir.listFiles()?.sumOf { it.length() } ?: 0L

    fun enqueue(songs: List<Song>) = songs.forEach { enqueue(it) }

    fun enqueue(song: Song) {
        if (isDownloaded(song.id) || progress.value.containsKey(song.id)) return
        progress.update { it + (song.id to 0f) }
        failed.update { it - song.id }
        notifyProgress()
        scope.launch {
            queue.withPermit {
                val ok = runCatching { download(song) }.onFailure {
                    // Bağlantı süresi dolmuş olabilir: bir kez daha dene
                    Yt.invalidate(song.id)
                }.isSuccess || runCatching { download(song) }.isSuccess
                progress.update { it - song.id }
                if (!ok) failed.update { it + song.id }
                notifyProgress()
            }
        }
    }

    private suspend fun download(song: Song) {
        val high = Store.settings.value.highQuality
        val url = Yt.streamUrl(song.id, high)
        val part = File(musicDir, "${song.id}.part")
        val total = contentLength(url)
        if (total <= 0) {
            // Boyut bilinmiyorsa tek parça indir
            Net.client.newCall(okhttp3.Request.Builder().url(url).build()).execute().use { r ->
                if (!r.isSuccessful) error("HTTP ${r.code}")
                part.outputStream().use { r.body.byteStream().copyTo(it) }
            }
        } else {
            RandomAccessFile(part, "rw").use { it.setLength(total) }
            val done = AtomicLong(0)
            val sem = Semaphore(PARALLEL)
            val ranges = (0 until total step CHUNK).map { it to minOf(it + CHUNK, total) - 1 }
            coroutineScopeAll(ranges.map { (s, e) ->
                suspend {
                    sem.withPermit { fetchRange(url, part, s, e, done, total, song.id) }
                }
            })
        }
        part.renameTo(File(musicDir, "${song.id}.ses"))
        val thumb = saveThumb(song)
        Store.addDownload(song.copy(thumb = thumb))
        Lyrics.prefetch(app, song)
    }

    private suspend fun coroutineScopeAll(jobs: List<suspend () -> Unit>) {
        kotlinx.coroutines.coroutineScope { jobs.map { j -> async { j() } }.awaitAll() }
    }

    private fun contentLength(url: String): Long {
        val req = okhttp3.Request.Builder().url(url).header("Range", "bytes=0-0").build()
        Net.client.newCall(req).execute().use { r ->
            val cr = r.header("Content-Range") ?: return -1
            return cr.substringAfter('/').toLongOrNull() ?: -1
        }
    }

    private fun fetchRange(url: String, file: File, start: Long, end: Long, done: AtomicLong, total: Long, id: String) {
        var attempt = 0
        while (true) {
            try {
                val req = okhttp3.Request.Builder().url(url).header("Range", "bytes=$start-$end").build()
                Net.client.newCall(req).execute().use { r ->
                    if (r.code != 206 && r.code != 200) error("HTTP ${r.code}")
                    val bytes = r.body.bytes()
                    RandomAccessFile(file, "rw").use { raf -> raf.seek(start); raf.write(bytes) }
                    val d = done.addAndGet(bytes.size.toLong())
                    progress.update { it + (id to (d.toFloat() / total).coerceIn(0f, 1f)) }
                }
                notifyProgress()
                return
            } catch (e: Exception) {
                if (++attempt >= 3) throw e
                Thread.sleep(400L * attempt)
            }
        }
    }

    private fun saveThumb(song: Song): String {
        if (song.thumb.isEmpty() || song.thumb.startsWith("file:")) return song.thumb
        return runCatching {
            val f = File(thumbDir, "${song.id}.jpg")
            Net.client.newCall(okhttp3.Request.Builder().url(song.thumb).build()).execute().use { r ->
                if (!r.isSuccessful) error("thumb")
                f.outputStream().use { r.body.byteStream().copyTo(it) }
            }
            Uri.fromFile(f).toString()
        }.getOrDefault(song.thumb)
    }

    fun delete(id: String) {
        File(musicDir, "$id.ses").delete()
        File(thumbDir, "$id.jpg").delete()
        Store.removeDownload(id)
    }

    private var lastNotify = 0L

    private fun notifyProgress() {
        val now = System.currentTimeMillis()
        val p = progress.value
        if (p.isNotEmpty() && now - lastNotify < 500) return
        lastNotify = now
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val nm = NotificationManagerCompat.from(app)
        if (p.isEmpty()) {
            nm.cancel(NOTIF_ID); return
        }
        val avg = p.values.average().toFloat()
        val n = NotificationCompat.Builder(app, CHANNEL)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle("Cihaza kaydediliyor")
            .setContentText("${p.size} şarkı kaldı")
            .setProgress(100, (avg * 100).toInt(), false)
            .setOngoing(true)
            .setSilent(true)
            .build()
        runCatching { nm.notify(NOTIF_ID, n) }
    }
}
