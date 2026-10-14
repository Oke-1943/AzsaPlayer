package io.github.oke1943.azsaplayer.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MigrationTest {

    /** ver.5 までの版で保存された設定（日本語のタブ名が入っている）。 */
    private val v5Json = """
        {"schema":5,"display":{"homeViews":[
          {"id":"album-artist","kind":"ALBUM_ARTIST","title":"アルバムアーティスト"},
          {"id":"album","kind":"ALBUM","title":"アルバム"},
          {"id":"cover-flow","kind":"COVER_FLOW","title":"Cover Flow"},
          {"id":"playlist","kind":"PLAYLIST","title":"マイリスト"},
          {"id":"label","kind":"CUSTOM","title":"LABEL","customTag":"LABEL"}
        ]},"replayGain":{"mode":"TRACK"}}
    """.trimIndent()

    @Test
    fun defaultJapaneseTitlesFollowLanguageButCustomNamesStay() {
        val old = SettingsRepository.decodeOrNull(v5Json)!!
        val s = migrateSettings(old)
        assertEquals(SettingsRepository.CURRENT_SCHEMA, s.schema)
        val titles = s.display.homeViews.associate { it.id to it.title }
        assertEquals("", titles["album-artist"])
        assertEquals("", titles["album"])
        assertEquals("", titles["cover-flow"])
        assertEquals(ViewKind.ALBUM_FLOW, s.display.homeViews.single { it.id == "cover-flow" }.kind)
        assertEquals("マイリスト", titles["playlist"]) // 利用者が付けた名前は残す
        assertEquals("", titles["label"])
        assertEquals(ReplayGainMode.TRACK, s.replayGain.mode)
    }

    @Test
    fun migrationIsIdempotent() {
        val once = migrateSettings(SettingsRepository.decodeOrNull(v5Json)!!)
        assertEquals(once, migrateSettings(once))
    }

    @Test
    fun freshInstallHasLanguageNeutralTitles() {
        assertTrue(AppSettings().display.homeViews.all { it.title.isEmpty() })
    }

    @Test
    fun v6CoverFlowTitleFollowsNewName() {
        val v6 = """{"schema":6,"display":{"homeViews":[{"id":"cover-flow","kind":"COVER_FLOW","title":"Cover Flow"}]}}"""
        val s = migrateSettings(SettingsRepository.decodeOrNull(v6)!!)
        val v = s.display.homeViews.single()
        assertEquals(ViewKind.ALBUM_FLOW, v.kind)
        assertEquals("", v.title)
    }

    @Test
    fun albumArtistArtworkIsOnByDefault() {
        // 新規インストール
        val fresh = AppSettings().display.homeViews.first { it.kind == ViewKind.ALBUM_ARTIST }
        assertTrue(fresh.styleAt(0).showArtwork)
        // 0.6.x で保存された設定（ジャケットなし）も移行でオンになる。アルバムの段は変えない
        val v7 = """{"schema":7,"display":{"homeViews":[{"id":"album-artist","kind":"ALBUM_ARTIST","levels":["ALBUM_ARTIST","ALBUM","TRACK"],
            "styles":[{"showArtwork":false},{"showArtwork":false},{}]}]}}"""
        val v = migrateSettings(SettingsRepository.decodeOrNull(v7)!!).display.homeViews.single()
        assertTrue(v.styleAt(0).showArtwork)
        assertEquals(false, v.styleAt(1).showArtwork)
    }

    @Test
    fun albumFlowIsStillSavedUnderTheOldName() {
        val json = SettingsRepository.json.encodeToString(AppSettings.serializer(), AppSettings())
        assertTrue(json.contains("\"COVER_FLOW\""))
    }
}
