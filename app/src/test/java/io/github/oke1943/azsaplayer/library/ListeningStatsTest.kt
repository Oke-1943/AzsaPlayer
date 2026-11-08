package io.github.oke1943.azsaplayer.library

import org.junit.Assert.assertEquals
import org.junit.Test

class ListeningStatsTest {
    private val day = 86_400_000L
    private val now = 1_000 * day

    @Test
    fun moreCompletedPlaysRankHigher() {
        val events = List(3) { ListenEvent(1, now - it * day, true) } + List(1) { ListenEvent(2, now, true) }
        assertEquals(listOf(1L, 2L), ListeningStats.frequentlyPlayed(events, now, 10))
    }

    @Test
    fun skipsLowerTheScoreAndCanRemoveATrack() {
        val events = listOf(
            ListenEvent(1, now, true), ListenEvent(1, now, false), ListenEvent(1, now, false),
            ListenEvent(2, now, true),
        )
        // 1 は +1 −0.7 −0.7 で0未満 → 入らない
        assertEquals(listOf(2L), ListeningStats.frequentlyPlayed(events, now, 10))
    }

    @Test
    fun recentListeningOutweighsOldListening() {
        // 1 は半年前に4回、2 は最近2回：最近のほうが上
        val events = List(4) { ListenEvent(1, now - 180 * day, true) } + List(2) { ListenEvent(2, now - day, true) }
        assertEquals(listOf(2L, 1L), ListeningStats.frequentlyPlayed(events, now, 10))
    }

    @Test
    fun limitsToSizeAndSkipsMissingTracks() {
        val events = (1L..5L).map { ListenEvent(it, now - it * day, true) }
        assertEquals(listOf(1L, 3L), ListeningStats.frequentlyPlayed(events, now, 2) { it != 2L })
    }
}
