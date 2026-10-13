package io.github.oke1943.azsaplayer.library

import io.github.oke1943.azsaplayer.data.PersonSortCandidate
import io.github.oke1943.azsaplayer.data.Role
import io.github.oke1943.azsaplayer.data.TrackEntity
import io.github.oke1943.azsaplayer.settings.LibrarySettings
import io.github.oke1943.azsaplayer.settings.ViewConfig
import io.github.oke1943.azsaplayer.settings.ViewKind
import io.github.oke1943.azsaplayer.tag.RawTags
import io.github.oke1943.azsaplayer.tag.TagRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 第3・4章（タグの解釈・ソート）の規則のテスト。実ファイルを使う T1・T2 は androidTest 側。 */
class TagInterpretationTest {

    private val settings = LibrarySettings()

    private fun tags(vararg pairs: Pair<String, List<String>>) =
        RawTags(linkedMapOf(*pairs), "FLAC", 1000, 0, 44100, 2, 16, false)

    // ---- 3.3 解析規則 ----

    @Test
    fun t3_singleValueWithSemicolonIsSplit() {
        assertEquals(listOf("A", "B"), TagRules.split(listOf("A; B"), listOf(";")))
    }

    @Test
    fun t4_slashCommaAmpersandAreNotSplit() {
        assertEquals(listOf("AC/DC"), TagRules.split(listOf("AC/DC"), listOf(";")))
        assertEquals(listOf("Earth, Wind & Fire"), TagRules.split(listOf("Earth, Wind & Fire"), listOf(";")))
        assertEquals(listOf("Artist feat. Guest"), TagRules.split(listOf("Artist feat. Guest"), listOf(";")))
    }

    @Test
    fun trimsDropsEmptyAndDeduplicatesKeepingOrder() {
        assertEquals(listOf("B", "A"), TagRules.split(listOf(" B ;; A", "b", "A ; "), listOf(";")))
    }

    @Test
    fun appliesNfcNormalization() {
        val decomposed = "が" // 「か」＋結合濁点
        assertEquals(listOf("が"), TagRules.split(listOf(decomposed), listOf(";")))
    }

    @Test
    fun additionalSeparatorsFromSettings() {
        assertEquals(listOf("A", "B", "C"), TagRules.split(listOf("A; B / C"), listOf(";", "/")))
    }

    // ---- 3.4・3.5 表示と内部モデル ----

    @Test
    fun t1_multiValueArtistsBecomeTwoPersonsAndDisplayJoined() {
        val p = TrackParser.parse("/x.flac", tags("ARTIST" to listOf("米津玄師", "菅田将暉")), settings)
        assertEquals(listOf("米津玄師", "菅田将暉"), p.names(Role.ARTIST))
        assertEquals("米津玄師; 菅田将暉", p.names(Role.ARTIST).joinToString("; "))
    }

    @Test
    fun t1_bothArtistsAppearInArtistListAndNeitherIsDropped() {
        val lib = snapshotOf(
            track(1, artists = listOf("米津玄師", "菅田将暉"), album = "A"),
            track(2, artists = listOf("米津玄師"), album = "B"),
        )
        val engine = BrowseEngine(lib, SortRules(false), emptyList(), { emptyMap() })
        val view = ViewConfig(id = "v", kind = ViewKind.ARTIST)
        val groups = engine.entries(view, 0, emptyList()).filterIsInstance<GroupEntry>()
        assertEquals(setOf("米津玄師", "菅田将暉"), groups.map { it.title }.toSet())
        val yonezu = groups.first { it.title == "米津玄師" }
        assertEquals(setOf(1L, 2L), yonezu.tracks.map { it.id }.toSet())
        assertEquals(listOf(1L), groups.first { it.title == "菅田将暉" }.tracks.map { it.id })
    }

    @Test
    fun coAlbumArtistAlbumAppearsUnderEachAlbumArtist() {
        val lib = snapshotOf(track(1, artists = listOf("X"), albumArtists = listOf("P", "Q"), album = "Joint"))
        val engine = BrowseEngine(lib, SortRules(false), emptyList(), { emptyMap() })
        val view = ViewConfig(id = "v", kind = ViewKind.ALBUM_ARTIST)
        val groups = engine.entries(view, 0, emptyList()).filterIsInstance<GroupEntry>()
        assertEquals(setOf("P", "Q"), groups.map { it.title }.toSet())
        for (g in groups) {
            val albums = engine.entries(view, 1, listOf(Selection(ViewKind.ALBUM_ARTIST, g.key, g.title)))
            assertEquals(listOf("Joint"), albums.map { it.title })
        }
    }

    @Test
    fun albumIdentityUsesAlbumArtistSoSameTitleDoesNotMerge() {
        val a = TrackParser.albumKey("Greatest Hits", listOf("Queen"))
        val b = TrackParser.albumKey("Greatest Hits", listOf("ABBA"))
        assertTrue(a != b)
    }

    @Test
    fun albumArtistFallsBackToArtist() {
        val p = TrackParser.parse("/x.flac", tags("ARTIST" to listOf("A"), "ALBUM" to listOf("Z")), settings)
        assertEquals(TrackParser.albumKey("Z", listOf("A")), p.albumKey)
    }

    // ---- 4章 ソート ----

    @Test
    fun t5_sortTagControlsOrderAndIndexButNotDisplay() {
        val p = TrackParser.parse(
            "/x.flac",
            tags("ARTIST" to listOf("Michael Jackson"), "ARTISTSORT" to listOf("Jackson, Michael")),
            settings,
        )
        val ref = p.persons.single { it.role == Role.ARTIST }
        assertEquals("Michael Jackson", ref.name)
        assertEquals("Jackson, Michael", ref.sortValue)
        val rules = SortRules(false)
        assertEquals("J", rules.indexChar(ref.sortValue!!))
        // 「Jackson」は「Kylie」より前、「Abba」より後
        val sorted = listOf("Kylie Minogue", "Jackson, Michael", "Abba").sortedWith { a, b -> rules.compare(a, b) }
        assertEquals(listOf("Abba", "Jackson, Michael", "Kylie Minogue"), sorted)
    }

    @Test
    fun t6_sortValuesArePairedByPosition() {
        val p = TrackParser.parse(
            "/x.flac",
            tags("ARTIST" to listOf("A; B"), "ARTISTSORT" to listOf("Sa; Sb")),
            settings,
        )
        val refs = p.persons.filter { it.role == Role.ARTIST }
        assertEquals(listOf("A" to "Sa", "B" to "Sb"), refs.map { it.name to it.sortValue })
    }

    @Test
    fun sortValuesWithDifferentCountAreNotAssignedToPersons() {
        val p = TrackParser.parse(
            "/x.flac",
            tags("ARTIST" to listOf("A", "B"), "ARTISTSORT" to listOf("Only One")),
            settings,
        )
        assertTrue(p.persons.filter { it.role == Role.ARTIST }.all { it.sortValue == null })
        // 曲単位の並びにだけ使う
        assertEquals("Only One", p.artistSortText)
    }

    @Test
    fun personSortKeyIsMostFrequentThenFirstPath() {
        val c = listOf(
            PersonSortCandidate(1, "Sado, Yutaka", 5, "/b"),
            PersonSortCandidate(1, "Beethoven, Ludwig van", 1, "/a"),
        )
        assertEquals("Sado, Yutaka", pickSortKey(c))
        val tie = listOf(PersonSortCandidate(1, "Y", 2, "/z"), PersonSortCandidate(1, "X", 2, "/a"))
        assertEquals("X", pickSortKey(tie))
        assertNull(pickSortKey(emptyList()))
    }

    @Test
    fun articlesAreIgnoredOnlyWhenEnabled() {
        assertEquals("B", SortRules(true).indexChar("The Beatles"))
        assertEquals("T", SortRules(false).indexChar("The Beatles"))
    }

    @Test
    fun caseInsensitiveComparison() {
        assertEquals(0, SortRules(false).compare("abc", "ABC"))
    }

    @Test
    fun indexCharForKanaAndOthers() {
        assertEquals("か", SortRules.indexCharOf("ぎんいろ"))
        assertEquals("さ", SortRules.indexCharOf("スピカ"))
        assertEquals("#", SortRules.indexCharOf("1999"))
        assertEquals("漢", SortRules.indexCharOf("米津玄師"))
        assertEquals("A", SortRules.indexCharOf("ａｂｃ"))
    }

    @Test
    fun trackAndDiscNumbersParse() {
        assertEquals(1 to 15, TrackParser.parseNumberPair("1/15", null))
        assertEquals(3 to 12, TrackParser.parseNumberPair("3", "12"))
        assertEquals(null to null, TrackParser.parseNumberPair(null, null))
    }

    // ---- 補助 ----

    private var nextPerson = 1L
    private val people = HashMap<String, LibPerson>()

    private fun person(name: String) = people.getOrPut(name) { LibPerson(nextPerson++, name, name) }

    private fun track(id: Long, artists: List<String>, album: String, albumArtists: List<String> = emptyList()) =
        Triple(id, artists.map(::person) to albumArtists.map(::person), album)

    private fun snapshotOf(vararg specs: Triple<Long, Pair<List<LibPerson>, List<LibPerson>>, String>): LibrarySnapshot {
        val albums = HashMap<String, LibAlbum>()
        val tracks = specs.map { (id, people, albumTitle) ->
            val (artists, aa) = people
            val key = TrackParser.albumKey(albumTitle, (aa.ifEmpty { artists }).map { it.name })
            val e = TrackEntity(
                id = id, path = "/m/$id.flac", folder = "/m", size = 1, mtime = 1, dateAdded = id, title = "T$id", titleSort = null,
                album = albumTitle, albumSort = null, albumKey = key, artistSortText = "", albumArtistSortText = "",
                year = null, date = null, trackNo = id.toInt(), trackTotal = null, discNo = null, discTotal = null,
                durationMs = 1000, codec = "FLAC", sampleRate = 44100, bitsPerSample = 16, channels = 2, bitrate = 0,
                playable = true, hasEmbeddedArt = false, rgTrackGain = null, rgTrackPeak = null, rgAlbumGain = null, rgAlbumPeak = null,
            )
            val t = LibTrack(e, artists, aa, emptyList(), emptyList())
            val album = albums.getOrPut(key) { LibAlbum(key, albumTitle, null, t.effectiveAlbumArtists) }
            album.tracks += t
            t.album = album
            t
        }
        return LibrarySnapshot(tracks, albums.values.toList(), people.values.associateBy { it.id })
    }
}
