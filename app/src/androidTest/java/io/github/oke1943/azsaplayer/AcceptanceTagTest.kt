package io.github.oke1943.azsaplayer

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.oke1943.azsaplayer.data.AppDatabase
import io.github.oke1943.azsaplayer.data.Role
import io.github.oke1943.azsaplayer.library.BrowseEngine
import io.github.oke1943.azsaplayer.library.GroupEntry
import io.github.oke1943.azsaplayer.library.LibraryScanner
import io.github.oke1943.azsaplayer.library.LibrarySnapshot
import io.github.oke1943.azsaplayer.library.SortRules
import io.github.oke1943.azsaplayer.library.TrackParser
import io.github.oke1943.azsaplayer.library.loadSnapshot
import io.github.oke1943.azsaplayer.playback.MediaItems
import io.github.oke1943.azsaplayer.settings.LibrarySettings
import io.github.oke1943.azsaplayer.settings.ViewConfig
import io.github.oke1943.azsaplayer.settings.ViewKind
import io.github.oke1943.azsaplayer.tag.TagLibNative
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * 受け入れテスト T1〜T6（9章）を実ファイルで確認する。
 * ファイル生成 → TagLib（JNI）→ 解析 → スキャナ → DB → 一覧、の全経路を通す。
 */
@RunWith(AndroidJUnit4::class)
class AcceptanceTagTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var dir: File
    private lateinit var db: AppDatabase
    private val settings get() = LibrarySettings(folders = listOf(dir.path))

    @Before
    fun setUp() {
        dir = File(context.cacheDir, "acceptance-${System.nanoTime()}").apply { mkdirs() }
        db = AppDatabase.create(context, name = null)
    }

    @After
    fun tearDown() {
        db.close()
        dir.deleteRecursively()
    }

    private val yonezuSuda = listOf("米津玄師", "菅田将暉")

    private fun scan(): LibrarySnapshot = runBlocking {
        LibraryScanner(context, db).scan(settings, full = true) {}
        loadSnapshot(db.libraryDao())
    }

    private fun artistGroups(lib: LibrarySnapshot): List<GroupEntry> =
        BrowseEngine(lib, SortRules(false), emptyList(), { emptyMap() })
            .entries(ViewConfig(id = "a", kind = ViewKind.ARTIST), 0, emptyList()).filterIsInstance<GroupEntry>()

    /** T1・T2 の共通の確認：曲一覧の表示と、アーティスト一覧の両方に現れること。 */
    private fun assertTwoArtists(file: File) {
        val raw = TagLibNative.read(file.path)
        assertNull(raw.error)
        assertEquals(yonezuSuda, raw.values("ARTIST"))

        val lib = scan()
        val track = lib.tracks.single { it.e.path == file.path }
        assertEquals(yonezuSuda, track.artists.map { it.name })
        // 曲一覧・通知・ロック画面に出す表示文字列
        assertEquals("米津玄師; 菅田将暉", MediaItems.build(track, "; ").mediaMetadata.artist.toString())
        val groups = artistGroups(lib)
        for (name in yonezuSuda) {
            val g = groups.single { it.title == name }
            assertTrue("$name の一覧に曲があること", g.tracks.any { it.id == track.id })
        }
    }

    @Test
    fun t1_mp3Id3v23NullSeparated() {
        val f = File(dir, "t1.mp3")
        TestMedia.mp3v23(f, listOf("TIT2" to listOf("T1"), "TPE1" to yonezuSuda))
        assertTwoArtists(f)
    }

    @Test
    fun t2_mp3Id3v24() {
        val f = File(dir, "t2.mp3")
        TestMedia.mp3v24(f, listOf("TIT2" to listOf("T2"), "TPE1" to yonezuSuda))
        assertTwoArtists(f)
    }

    @Test
    fun t2_flacTwoArtistFields() {
        val f = File(dir, "t2.flac")
        TestMedia.flac(f, listOf("TITLE=T2", "ARTIST=米津玄師", "ARTIST=菅田将暉"))
        assertTwoArtists(f)
    }

    @Test
    fun t2_m4aRepeatedAtomsLikeMusicBee() {
        val f = File(dir, "t2.m4a")
        TestMedia.m4a(f, listOf("©nam" to listOf("T2"), "©ART" to yonezuSuda))
        assertTwoArtists(f)
    }

    @Test
    fun t3_flacSingleSemicolonValueIsSplit() {
        val f = File(dir, "t3.flac")
        TestMedia.flac(f, listOf("TITLE=T3", "ARTIST=A; B"))
        val lib = scan()
        assertEquals(listOf("A", "B"), lib.tracks.single().artists.map { it.name })
        assertEquals(setOf("A", "B"), artistGroups(lib).map { it.title }.toSet())
    }

    @Test
    fun t4_noSplitOnSlashCommaAmpersand() {
        TestMedia.flac(File(dir, "acdc.flac"), listOf("TITLE=x", "ARTIST=AC/DC"))
        TestMedia.mp3v23(File(dir, "ewf.mp3"), listOf("TIT2" to listOf("y"), "TPE1" to listOf("Earth, Wind & Fire")))
        val names = artistGroups(scan()).map { it.title }.toSet()
        assertEquals(setOf("AC/DC", "Earth, Wind & Fire"), names)
    }

    @Test
    fun t5_sortTagOrderingAndIndex() {
        TestMedia.flac(File(dir, "mj.flac"), listOf("TITLE=x", "ARTIST=Michael Jackson", "ARTISTSORT=Jackson, Michael"))
        TestMedia.flac(File(dir, "kylie.flac"), listOf("TITLE=y", "ARTIST=Kylie Minogue"))
        TestMedia.flac(File(dir, "abba.flac"), listOf("TITLE=z", "ARTIST=ABBA"))
        TestMedia.flac(File(dir, "lady.flac"), listOf("TITLE=w", "ARTIST=Lady Gaga"))
        val groups = artistGroups(scan())
        // 表示は通常タグ、並びはソートタグ：Jの位置（Kylie より前）に来る
        assertEquals(listOf("ABBA", "Michael Jackson", "Kylie Minogue", "Lady Gaga"), groups.map { it.title })
        val mj = groups.single { it.title == "Michael Jackson" }
        assertEquals("J", SortRules(false).indexChar(mj.sortText))
    }

    @Test
    fun t6_sortValuesMappedPerArtist() {
        TestMedia.flac(File(dir, "t6.flac"), listOf("TITLE=x", "ARTIST=A; B", "ARTISTSORT=Sa; Sb"))
        val lib = scan()
        val byName = lib.persons.values.associateBy { it.name }
        assertEquals("Sa", byName.getValue("A").sortKey)
        assertEquals("Sb", byName.getValue("B").sortKey)
    }

    @Test
    fun m4aSortAtomsAndReplayGainFreeformAreRead() {
        val f = File(dir, "rg.m4a")
        TestMedia.m4a(
            f,
            listOf("©ART" to listOf("佐渡裕", "ベルリン・ドイツ交響楽団"), "soar" to listOf("Sado, Yutaka", "Berlin German Symphony Orchestra")),
            freeform = mapOf("replaygain_track_gain" to "-2.73 dB", "replaygain_track_peak" to "0.952142"),
        )
        val parsed = TrackParser.parse(f.path, TagLibNative.read(f.path), LibrarySettings())
        assertEquals(
            listOf("佐渡裕" to "Sado, Yutaka", "ベルリン・ドイツ交響楽団" to "Berlin German Symphony Orchestra"),
            parsed.persons.filter { it.role == Role.ARTIST }.map { it.name to it.sortValue },
        )
        assertEquals(-2.73f, parsed.replayGain.trackGain!!, 1e-4f)
        assertEquals(0.952142f, parsed.replayGain.trackPeak!!, 1e-6f)
    }

    @Test
    fun differentialScanOnlyRereadsChangedFiles() {
        val a = File(dir, "a.flac")
        TestMedia.flac(a, listOf("TITLE=one", "ARTIST=X"))
        val first = scan().tracks.single()
        Thread.sleep(1100)
        TestMedia.flac(a, listOf("TITLE=two", "ARTIST=X"))
        a.setLastModified(System.currentTimeMillis())
        val second = runBlocking {
            LibraryScanner(context, db).scan(settings, full = false) {}
            loadSnapshot(db.libraryDao())
        }.tracks.single()
        assertEquals("two", second.title)
        assertEquals("追加日は保たれる", first.e.dateAdded, second.e.dateAdded)
        assertEquals(first.id, second.id)
    }
}
