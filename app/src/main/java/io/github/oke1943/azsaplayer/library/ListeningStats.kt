package io.github.oke1943.azsaplayer.library

import kotlin.math.pow

/** 再生の記録1件。[completed] は曲の半分以上を聞いたか（半分に届かず次へ移ったらスキップ）。 */
data class ListenEvent(val trackId: Long, val at: Long, val completed: Boolean)

/**
 * 「よく聞く音楽」の選び方。
 * - 半分以上聞いた再生は +1、スキップは −[SKIP_PENALTY]
 * - 古い記録ほど効き目を弱める（[HALF_LIFE_DAYS] 日で半分）。日ごろの傾向に合わせて自動で入れ替わる
 * - 点数が0より大きい曲だけを、点数の高い順に [size] 曲まで。同点なら最後に聞いた日時が新しい順
 */
object ListeningStats {
    const val HALF_LIFE_DAYS = 30.0
    const val SKIP_PENALTY = 0.7
    private const val DAY_MS = 86_400_000.0

    fun frequentlyPlayed(events: List<ListenEvent>, now: Long, size: Int, exists: (Long) -> Boolean = { true }): List<Long> {
        if (size <= 0) return emptyList()
        val score = HashMap<Long, Double>()
        val last = HashMap<Long, Long>()
        for (e in events) {
            val ageDays = ((now - e.at).coerceAtLeast(0)) / DAY_MS
            val weight = 0.5.pow(ageDays / HALF_LIFE_DAYS)
            score.merge(e.trackId, if (e.completed) weight else -SKIP_PENALTY * weight, Double::plus)
            if (e.completed) last.merge(e.trackId, e.at, ::maxOf)
        }
        return score.entries
            .filter { it.value > 1e-9 && exists(it.key) }
            .sortedWith(compareByDescending<Map.Entry<Long, Double>> { it.value }.thenByDescending { last[it.key] ?: 0L })
            .take(size)
            .map { it.key }
    }
}
