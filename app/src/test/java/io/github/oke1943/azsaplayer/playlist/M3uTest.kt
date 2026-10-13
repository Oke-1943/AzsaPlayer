package io.github.oke1943.azsaplayer.playlist

import io.github.oke1943.azsaplayer.settings.PathRule
import org.junit.Assert.assertEquals
import org.junit.Test

class M3uTest {

    @Test
    fun parsesEntriesSkippingComments() {
        val text = "#EXTM3U\r\n#EXTINF:123,Artist - Title\r\nD:\\Music\\A\\01.flac\r\n\r\nD:\\Music\\B\\02.m4a\r\n"
        assertEquals(listOf("D:\\Music\\A\\01.flac", "D:\\Music\\B\\02.m4a"), M3u.parse(text))
    }

    @Test
    fun windowsPrefixIsReplacedCaseInsensitively() {
        val rules = listOf(PathRule("D:\\Music\\", "/storage/1234-5678/Music/"))
        assertEquals(
            "/storage/1234-5678/Music/愛二咲花/01 眼差し.flac",
            M3u.resolve("d:\\music\\愛二咲花\\01 眼差し.flac", rules, null),
        )
    }

    @Test
    fun relativePathsResolveAgainstPlaylistFolder() {
        assertEquals("/storage/emulated/0/Music/A/01.flac", M3u.resolve("..\\Music\\A\\01.flac", emptyList(), "/storage/emulated/0/Playlists"))
    }

    @Test
    fun suffixMatchesIgnoringRoot() {
        assertEquals("artist/album/01.flac", M3u.suffix("D:\\Music\\Artist\\Album\\01.flac"))
        assertEquals("artist/album/01.flac", M3u.suffix("/storage/emulated/0/Music/Artist/Album/01.flac"))
    }
}
