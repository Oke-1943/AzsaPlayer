package io.github.oke1943.azsaplayer.playback

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import io.github.oke1943.azsaplayer.library.LibTrack
import io.github.oke1943.azsaplayer.tag.ReplayGainInfo
import java.io.File

/** 曲 → MediaItem の変換。リプレイゲイン値とアルバムキーをextrasに入れて再生スレッドへ渡す。 */
object MediaItems {
    const val ART_SCHEME = "musicapp-art"

    private const val TG = "rg.tg"
    private const val TP = "rg.tp"
    private const val AG = "rg.ag"
    private const val AP = "rg.ap"
    const val ALBUM_KEY = "album.key"
    const val FORMAT = "format"

    fun build(t: LibTrack, join: String): MediaItem {
        val e = t.e
        val extras = Bundle().apply {
            e.rgTrackGain?.let { putFloat(TG, it) }
            e.rgTrackPeak?.let { putFloat(TP, it) }
            e.rgAlbumGain?.let { putFloat(AG, it) }
            e.rgAlbumPeak?.let { putFloat(AP, it) }
            putString(ALBUM_KEY, e.albumKey)
            putString(FORMAT, formatText(t))
        }
        val metadata = MediaMetadata.Builder()
            .setTitle(e.title)
            // 複数アーティストは全員を連結して表示（3.4：先頭以外を捨てない）
            .setArtist(t.artists.joinToString(join) { it.name })
            .setAlbumTitle(e.album)
            .setAlbumArtist(t.effectiveAlbumArtists.joinToString(join) { it.name })
            .setComposer(t.composers.joinToString(join) { it.name }.ifEmpty { null })
            .setTrackNumber(e.trackNo)
            .setDiscNumber(e.discNo)
            .setReleaseYear(e.year)
            .setDurationMs(e.durationMs)
            .setArtworkUri(Uri.parse("$ART_SCHEME://track/${t.album.artTrack.id}"))
            .setIsPlayable(e.playable)
            .setIsBrowsable(false)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .setExtras(extras)
            .build()
        return MediaItem.Builder()
            .setMediaId(e.id.toString())
            .setUri(Uri.fromFile(File(e.path)))
            .setMediaMetadata(metadata)
            .build()
    }

    fun replayGain(item: MediaItem): ReplayGainInfo {
        val x = item.mediaMetadata.extras ?: return ReplayGainInfo()
        fun f(k: String) = if (x.containsKey(k)) x.getFloat(k) else null
        return ReplayGainInfo(f(TG), f(TP), f(AG), f(AP))
    }

    fun albumKey(item: MediaItem?): String? = item?.mediaMetadata?.extras?.getString(ALBUM_KEY)

    fun formatText(t: LibTrack): String {
        val e = t.e
        val parts = mutableListOf(e.codec)
        if (e.bitsPerSample > 0) parts += "${e.bitsPerSample}bit"
        if (e.sampleRate > 0) parts += io.github.oke1943.azsaplayer.library.Template.formatRate(e.sampleRate)
        if (e.bitrate > 0 && e.codec in setOf("AAC", "MP3", "Vorbis", "Opus")) parts += "${e.bitrate}kbps"
        return parts.joinToString(" / ")
    }
}
