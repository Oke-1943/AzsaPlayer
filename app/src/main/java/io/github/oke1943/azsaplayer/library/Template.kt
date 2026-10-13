package io.github.oke1943.azsaplayer.library

import java.io.File

/**
 * MusicBee風のテンプレート文字列（6.3）。例：`<Artist> — <Year>`。
 * 使えるフィールドは [FIELDS]。存在しない値は空文字になり、空になった区切りは残さない。
 */
object Template {

    val FIELDS = listOf(
        "Name", "Title", "Artist", "Album Artist", "Album", "Year", "Track#", "Disc#", "Genre", "Composer",
        "Duration", "Count", "Format", "Bit Depth", "Sample Rate", "Kbps", "File", "Path",
    )

    private val TOKEN = Regex("<([^<>]+)>")

    fun render(template: String, entry: BrowseEntry, join: String, labels: BrowseLabels = BrowseLabels()): String {
        val values: (String) -> String? = when (entry) {
            is TrackEntry -> { name -> trackField(entry.track, name, join) }
            is GroupEntry -> { name -> groupField(entry, name, join, labels) }
        }
        val out = TOKEN.replace(template) { m -> values(m.groupValues[1].trim()) ?: "" }
        return out.trim().trim('—', '-', '/', '·').trim()
    }

    fun trackField(t: LibTrack, name: String, join: String): String? = when (name.lowercase()) {
        "name", "title" -> t.title
        "artist" -> t.artists.joinToString(join) { it.name }
        "album artist" -> t.effectiveAlbumArtists.joinToString(join) { it.name }
        "album" -> t.e.album
        "year" -> t.e.year?.toString()
        "track#", "track" -> t.e.trackNo?.toString()
        "disc#", "disc" -> t.e.discNo?.toString()
        "genre" -> t.genres.joinToString(join)
        "composer" -> t.composers.joinToString(join) { it.name }
        "duration" -> formatDuration(t.e.durationMs)
        "format" -> t.e.codec
        "bit depth" -> t.e.bitsPerSample.takeIf { it > 0 }?.let { "${it}bit" }
        "sample rate" -> t.e.sampleRate.takeIf { it > 0 }?.let { formatRate(it) }
        "kbps" -> t.e.bitrate.takeIf { it > 0 }?.let { "${it}kbps" }
        "file" -> File(t.e.path).name
        "path" -> t.e.path
        else -> null
    }

    private fun groupField(g: GroupEntry, name: String, join: String, labels: BrowseLabels): String? = when (name.lowercase()) {
        "name", "title" -> g.title
        "album" -> g.album?.title ?: g.title
        "album artist", "artist" -> g.album?.albumArtists?.joinToString(join) { it.name }
            ?: g.tracks.flatMap { it.artists }.distinctBy { it.id }.take(3).joinToString(join) { it.name }
        "year" -> g.year?.toString()
        "count" -> labels.tracks(g.tracks.size)
        "duration" -> formatDuration(g.tracks.sumOf { it.e.durationMs })
        "genre" -> g.tracks.flatMap { it.genres }.distinct().joinToString(join)
        "path" -> g.subtitle.ifEmpty { null }
        else -> null
    }

    fun formatDuration(ms: Long): String {
        val totalSec = ms / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    fun formatRate(hz: Int): String =
        if (hz % 1000 == 0) "${hz / 1000}kHz" else "%.1fkHz".format(hz / 1000.0)
}
