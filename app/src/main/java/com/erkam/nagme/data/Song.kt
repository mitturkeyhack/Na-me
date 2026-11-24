package com.erkam.nagme.data

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import org.json.JSONArray
import org.json.JSONObject

/** Bir şarkı: YouTube video kimliği + gösterilecek bilgiler. */
data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val duration: Long = 0, // saniye
    val thumb: String = "",
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("t", title).put("a", artist).put("d", duration).put("th", thumb)

    /** Oynatıcıya giden öğe. Bağlantı (uri) servis tarafında çözülür. */
    fun toMediaItem(): MediaItem = MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setArtworkUri(if (thumb.isNotEmpty()) Uri.parse(thumb) else null)
                .setIsPlayable(true)
                .setIsBrowsable(false)
                .setExtras(Bundle().apply { putLong("dur", duration) })
                .build()
        )
        .build()

    companion object {
        fun fromJson(o: JSONObject) = Song(
            o.getString("id"), o.optString("t"), o.optString("a"), o.optLong("d"), o.optString("th")
        )

        fun listFromJson(a: JSONArray?): List<Song> {
            if (a == null) return emptyList()
            return (0 until a.length()).mapNotNull { runCatching { fromJson(a.getJSONObject(it)) }.getOrNull() }
        }

        fun listToJson(l: List<Song>) = JSONArray().apply { l.forEach { put(it.toJson()) } }

        fun from(item: MediaItem): Song {
            val m = item.mediaMetadata
            return Song(
                id = item.mediaId,
                title = m.title?.toString() ?: "",
                artist = m.artist?.toString() ?: "",
                duration = m.extras?.getLong("dur") ?: 0,
                thumb = m.artworkUri?.toString() ?: "",
            )
        }
    }
}

data class Playlist(val id: String, val name: String, val songs: List<Song>) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("n", name).put("s", Song.listToJson(songs))

    companion object {
        fun fromJson(o: JSONObject) = Playlist(o.getString("id"), o.optString("n"), Song.listFromJson(o.optJSONArray("s")))
    }
}

fun formatTime(sec: Long): String {
    if (sec <= 0) return "0:00"
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
