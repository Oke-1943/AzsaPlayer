package io.github.oke1943.azsaplayer.library

import io.github.oke1943.azsaplayer.data.TrackEntity
import io.github.oke1943.azsaplayer.settings.AlbumOrder
import io.github.oke1943.azsaplayer.settings.ViewConfig
import io.github.oke1943.azsaplayer.settings.ViewKind
import io.github.oke1943.azsaplayer.tag.TagRules
import org.junit.Assert.assertEquals
import org.junit.Test

/** アーティストのアルバム一覧の並び順（全体の既定・アーティストごと・手動）。 */
class AlbumOrderTest {

    private val artist = LibPerson(1, "Aimer", "Aimer")
    private val key = TagRules.identityKey("Aimer")

    /** (アルバム名, 年, 追加日) */
    private val albums = listOf(Triple("B-side", 2015, 3L), Triple("Anthem", 2012, 2L), Triple("Clear", 2019, 1L))

    private fun snapshot(): LibrarySnapshot {
        val libAlbums = ArrayList<LibAlbum>()
        val tracks = albums.mapIndexed { i, (title, year, added) ->
            val e = TrackEntity(
                id = i + 1L, path = "/m/$i.flac", folder = "/m", size = 1, mtime = 1, dateAdded = added, title = "T$i", titleSort = null,
                album = title, albumSort = null, albumKey = title, artistSortText = "", albumArtistSortText = "",
                year = year, date = null, trackNo = 1, trackTotal = null, discNo = null, discTotal = null,
                durationMs = 1000, codec = "FLAC", sampleRate = 44100, bitsPerSample = 16, channels = 2, bitrate = 0,
                playable = true, hasEmbeddedArt = false, rgTrackGain = null, rgTrackPeak = null, rgAlbumGain = null, rgAlbumPeak = null,
            )
            val t = LibTrack(e, listOf(artist), listOf(artist), emptyList(), emptyList())
            t.album = LibAlbum(title, title, null, listOf(artist)).also { it.tracks += t; libAlbums += it }
            t
        }
        return LibrarySnapshot(tracks, libAlbums, mapOf(artist.id to artist))
    }

    private fun order(
        default: AlbumOrder,
        perArtist: Map<String, AlbumOrder> = emptyMap(),
        manual: Map<String, List<String>> = emptyMap(),
    ): List<String> {
        val engine = BrowseEngine(snapshot(), SortRules(false), emptyList(), { emptyMap() }, albumOrder = default, artistAlbumOrder = perArtist, customAlbumOrder = manual)
        val view = ViewConfig(id = "aa", kind = ViewKind.ALBUM_ARTIST)
        return engine.entries(view, 1, listOf(Selection(ViewKind.ALBUM_ARTIST, "1", "Aimer"))).map { it.title }
    }

    @Test
    fun defaultOrderAppliesToEveryArtist() {
        assertEquals(listOf("Anthem", "B-side", "Clear"), order(AlbumOrder.YEAR_ASC))
        assertEquals(listOf("Clear", "B-side", "Anthem"), order(AlbumOrder.YEAR_DESC))
        assertEquals(listOf("B-side", "Anthem", "Clear"), order(AlbumOrder.ADDED_NEWEST))
    }

    @Test
    fun perArtistOrderOverridesTheDefault() {
        assertEquals(listOf("Anthem", "B-side", "Clear"), order(AlbumOrder.YEAR_DESC, perArtist = mapOf(key to AlbumOrder.TITLE)))
    }

    @Test
    fun manualOrderPutsNewAlbumsLast() {
        // 「Clear」はあとから増えたアルバム：手動の並びにないので最後
        val got = order(AlbumOrder.YEAR_ASC, perArtist = mapOf(key to AlbumOrder.MANUAL), manual = mapOf(key to listOf("B-side", "Anthem")))
        assertEquals(listOf("B-side", "Anthem", "Clear"), got)
    }
}
