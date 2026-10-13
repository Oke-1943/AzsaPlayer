package io.github.oke1943.azsaplayer.ui.browse

import org.junit.Assert.assertEquals
import org.junit.Test

class FastScrollTest {

    @Test
    fun alwaysShowsHashAndAtoZ_andKanaRowsOnlyWhenPresent() {
        val labels = indexLabels(setOf("A", "ら", "漢"))
        assertEquals("#", labels.first())
        assertEquals(('A'..'Z').map { it.toString() }, labels.subList(1, 27))
        assertEquals(listOf("ら", "漢"), labels.drop(27))
    }

    @Test
    fun jumpsToFirstEntryOrNearestFollowingLetter() {
        val letters = listOf("A", "A", "C", "R", "ら", "漢")
        val labels = indexLabels(letters.toSet())
        assertEquals(2, jumpTarget("C", labels, letters))
        assertEquals(3, jumpTarget("Q", labels, letters)) // Q がなければ次の R
        assertEquals(3, jumpTarget("S", labels, letters)) // S は前の R が最も近い
        assertEquals(4, jumpTarget("ら", labels, letters))
        assertEquals(0, jumpTarget("#", labels, letters))
    }
}
