package io.github.oke1943.azsaplayer

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.oke1943.azsaplayer.library.SortRules
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** 4.3：ICUの日本語照合で、英字 → かな → 漢字（MusicBee／Windowsと同じ並び）になること。 */
@RunWith(AndroidJUnit4::class)
class CollationOrderTest {
    @Test
    fun latinThenKanaThenKanji() {
        val rules = SortRules(false)
        val words = listOf("米津玄師", "ロクデナシ", "AC/DC", "菅田将暉", "abba", "あいみょん", "1999", "Zebra", "かもめ")
        val sorted = words.sortedBy { rules.key(it) }
        assertEquals(listOf("1999", "abba", "AC/DC", "Zebra", "あいみょん", "かもめ", "ロクデナシ"), sorted.take(7))
        assertEquals(setOf("米津玄師", "菅田将暉"), sorted.drop(7).toSet())
    }
}
