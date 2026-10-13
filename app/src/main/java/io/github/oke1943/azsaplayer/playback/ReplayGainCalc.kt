package io.github.oke1943.azsaplayer.playback

import io.github.oke1943.azsaplayer.settings.ReplayGainMode
import io.github.oke1943.azsaplayer.settings.ReplayGainSettings
import io.github.oke1943.azsaplayer.tag.ReplayGainInfo
import kotlin.math.min
import kotlin.math.pow

enum class GainSource(@androidx.annotation.StringRes val labelRes: Int) {
    NONE(io.github.oke1943.azsaplayer.R.string.gain_none),
    TRACK(io.github.oke1943.azsaplayer.R.string.gain_track),
    ALBUM(io.github.oke1943.azsaplayer.R.string.gain_album),
    UNTAGGED(io.github.oke1943.azsaplayer.R.string.gain_untagged),
}

/** 適用する補正の結果。[factor] は線形倍率、[gainDb] は G_dB（5.3）。 */
data class GainDecision(val factor: Float, val gainDb: Float, val source: GainSource, val limitedByPeak: Boolean) {
    /** 再生中画面の表示（5.4）。例：「RG −6.5 dB（アルバム）」 */
    fun label(res: android.content.res.Resources): String =
        if (source == GainSource.NONE) res.getString(io.github.oke1943.azsaplayer.R.string.rg_off) else {
            val shownDb = 20f * kotlin.math.log10(factor.toDouble()).toFloat()
            val sign = if (shownDb < 0) "−" else "+"
            res.getString(io.github.oke1943.azsaplayer.R.string.rg_label, sign, kotlin.math.abs(shownDb), res.getString(source.labelRes))
        }

    companion object {
        val UNITY = GainDecision(1f, 0f, GainSource.NONE, false)
    }
}

/** リプレイゲインの補正量の計算（仕様書 5.2・5.3）。 */
object ReplayGainCalc {

    /**
     * @param albumContext 「アルバム単位で再生している」か（自動モードの判定、5.2）
     */
    fun decide(info: ReplayGainInfo, settings: ReplayGainSettings, albumContext: Boolean): GainDecision {
        val preferAlbum = when (settings.mode) {
            ReplayGainMode.OFF -> return GainDecision.UNITY
            ReplayGainMode.TRACK -> false
            ReplayGainMode.ALBUM -> true
            ReplayGainMode.AUTO -> albumContext
        }
        val useAlbum = if (preferAlbum) info.albumGain != null else info.trackGain == null && info.albumGain != null
        val tagGain = if (useAlbum) info.albumGain else info.trackGain
        val peak = if (useAlbum) info.albumPeak else info.trackPeak

        if (tagGain == null) {
            // タグがない曲は G_tag = 0 として、タグなし用のプリアンプだけをかける
            val db = settings.preampUntagged
            return GainDecision(dbToLinear(db), db, GainSource.UNTAGGED, false)
        }
        val db = tagGain + settings.preampTagged
        var factor = dbToLinear(db)
        var limited = false
        if (settings.preventClipping && peak != null && peak > 0f && factor > 1f / peak) {
            factor = min(factor, 1f / peak)
            limited = true
        }
        return GainDecision(factor, db, if (useAlbum) GainSource.ALBUM else GainSource.TRACK, limited)
    }

    fun dbToLinear(db: Float): Float = 10.0.pow(db / 20.0).toFloat()

    /**
     * 「アルバム単位で再生している」の定義（5.2）：シャッフルが無効で、
     * 再生キュー内で現在曲の前または後の曲が同じアルバムであること。
     */
    fun isAlbumContext(shuffleEnabled: Boolean, current: String?, previous: String?, next: String?): Boolean =
        !shuffleEnabled && current != null && (current == previous || current == next)
}
