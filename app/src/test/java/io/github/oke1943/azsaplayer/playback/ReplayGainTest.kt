package io.github.oke1943.azsaplayer.playback

import androidx.media3.common.C
import io.github.oke1943.azsaplayer.settings.ReplayGainMode
import io.github.oke1943.azsaplayer.settings.ReplayGainSettings
import io.github.oke1943.azsaplayer.tag.ReplayGainInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

/** 第5章（リプレイゲイン）のテスト。T7〜T9。 */
class ReplayGainTest {

    private val track = ReplayGainSettings(mode = ReplayGainMode.TRACK)

    // ---- 5.1 読み取り ----

    @Test
    fun parsesGainStringsRobustly() {
        assertEquals(-6.54f, ReplayGainInfo.parseNumber("-6.54 dB")!!, 1e-6f)
        assertEquals(-6.54f, ReplayGainInfo.parseNumber("  -6.54dB ")!!, 1e-6f)
        assertEquals(3.2f, ReplayGainInfo.parseNumber("+3,2 dB")!!, 1e-6f)
        assertEquals(-1.0f, ReplayGainInfo.parseNumber("−1.0")!!, 1e-6f)
        assertEquals(null, ReplayGainInfo.parseNumber("abc"))
    }

    @Test
    fun readsStandardTagsCaseInsensitivelyNormalizedByTagLib() {
        val info = ReplayGainInfo.fromProperties(
            mapOf(
                "REPLAYGAIN_TRACK_GAIN" to listOf("-9.21 dB"),
                "REPLAYGAIN_TRACK_PEAK" to listOf("1.015481"),
                "REPLAYGAIN_ALBUM_GAIN" to listOf("-9.13 dB"),
                "REPLAYGAIN_ALBUM_PEAK" to listOf("1.015481"),
            ),
            "AAC",
        )
        assertEquals(-9.21f, info.trackGain!!, 1e-6f)
        assertEquals(1.015481f, info.albumPeak!!, 1e-6f)
    }

    @Test
    fun t9_opusR128IsShiftedToReplayGainReference() {
        val info = ReplayGainInfo.fromProperties(mapOf("R128_TRACK_GAIN" to listOf("-1280")), "Opus")
        assertEquals(0f, info.trackGain!!, 1e-6f) // −1280/256 = −5 dB、+5 dB で 0 dB
        val d = ReplayGainCalc.decide(info, track, albumContext = false)
        assertEquals(1.0f, d.factor, 1e-6f)
    }

    // ---- 5.2・5.3 補正量 ----

    @Test
    fun t7_minus6dbWithPeakHalfGivesAbout0501() {
        val info = ReplayGainInfo(trackGain = -6.00f, trackPeak = 0.5f)
        val d = ReplayGainCalc.decide(info, track, albumContext = false)
        assertEquals(0.501f, d.factor, 0.0005f)
        assertEquals(GainSource.TRACK, d.source)
    }

    @Test
    fun t8_clippingPreventionLimitsToInversePeak() {
        val info = ReplayGainInfo(trackGain = 10f, trackPeak = 0.9f)
        val d = ReplayGainCalc.decide(info, track, albumContext = false)
        assertEquals(1f / 0.9f, d.factor, 1e-5f)
        assertTrue(d.limitedByPeak)
        val off = ReplayGainCalc.decide(info, track.copy(preventClipping = false), albumContext = false)
        assertEquals(3.1623f, off.factor, 1e-3f)
    }

    @Test
    fun noPeakMeansNoLimit() {
        val d = ReplayGainCalc.decide(ReplayGainInfo(trackGain = 10f), track, albumContext = false)
        assertEquals(3.1623f, d.factor, 1e-3f)
    }

    @Test
    fun modeFallbacks() {
        val onlyAlbum = ReplayGainInfo(albumGain = -3f)
        assertEquals(GainSource.ALBUM, ReplayGainCalc.decide(onlyAlbum, track, false).source)
        val onlyTrack = ReplayGainInfo(trackGain = -3f)
        assertEquals(GainSource.TRACK, ReplayGainCalc.decide(onlyTrack, track.copy(mode = ReplayGainMode.ALBUM), false).source)
        val both = ReplayGainInfo(trackGain = -3f, albumGain = -5f)
        val auto = ReplayGainSettings(mode = ReplayGainMode.AUTO)
        assertEquals(GainSource.ALBUM, ReplayGainCalc.decide(both, auto, albumContext = true).source)
        assertEquals(GainSource.TRACK, ReplayGainCalc.decide(both, auto, albumContext = false).source)
        assertEquals(1f, ReplayGainCalc.decide(both, ReplayGainSettings(mode = ReplayGainMode.OFF), true).factor, 0f)
    }

    @Test
    fun preampsForTaggedAndUntagged() {
        val s = track.copy(preampTagged = 2f, preampUntagged = -6f)
        assertEquals(1f, ReplayGainCalc.decide(ReplayGainInfo(trackGain = -2f), s, false).factor, 1e-5f)
        val untagged = ReplayGainCalc.decide(ReplayGainInfo(), s, false)
        assertEquals(GainSource.UNTAGGED, untagged.source)
        assertEquals(0.501f, untagged.factor, 0.0005f)
    }

    @Test
    fun albumContextDefinition() {
        assertTrue(ReplayGainCalc.isAlbumContext(false, "a", "a", "b"))
        assertTrue(ReplayGainCalc.isAlbumContext(false, "a", null, "a")) // アルバムの1曲目
        assertTrue(!ReplayGainCalc.isAlbumContext(true, "a", "a", "a")) // シャッフル中
        assertTrue(!ReplayGainCalc.isAlbumContext(false, "a", "b", "c")) // 混在プレイリスト
    }

    @Test
    fun appliedGainForDisplay() {
        // 表示文字列そのものは文言ファイル（言語別）で組み立てるため、ここでは中身を確かめる
        val d = ReplayGainCalc.decide(ReplayGainInfo(albumGain = -6.5f), ReplayGainSettings(mode = ReplayGainMode.ALBUM), true)
        assertEquals(GainSource.ALBUM, d.source)
        assertEquals(-6.5f, 20f * kotlin.math.log10(d.factor), 1e-4f)
    }

    // ---- 5.4 サンプル処理（出力振幅の確認） ----

    @Test
    fun t7_outputAmplitudeIsScaled_16bitWithDither() {
        val pcm = GainPcm()
        val n = 4096
        val input = ByteBuffer.allocate(n * 2).order(ByteOrder.LITTLE_ENDIAN)
        repeat(n) { i -> input.putShort((if (i % 2 == 0) 16000 else -16000).toShort()) }
        input.flip()
        val out = ByteBuffer.allocate(n * 2)
        val factor = ReplayGainCalc.decide(ReplayGainInfo(trackGain = -6f, trackPeak = 0.5f), track, false).factor
        pcm.process(input, C.ENCODING_PCM_16BIT, factor, out)
        out.order(ByteOrder.LITTLE_ENDIAN)
        var maxErr = 0.0
        repeat(n) { i ->
            val expected = (if (i % 2 == 0) 16000 else -16000) * 0.501187
            maxErr = maxOf(maxErr, abs(out.short - expected))
        }
        assertTrue("ディザ込みで±2LSB以内であること: $maxErr", maxErr <= 2.0)
    }

    @Test
    fun unityGainIs16bitPassthrough() {
        val input = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).apply { putShort(1); putShort(-2); putShort(32767); putShort(-32768); flip() }
        val out = ByteBuffer.allocate(8)
        GainPcm().process(input.duplicate().order(ByteOrder.LITTLE_ENDIAN), C.ENCODING_PCM_16BIT, 1f, out)
        assertEquals(input, out)
    }

    @Test
    fun floatAnd24bitPathsOutputFloat() {
        val pcm = GainPcm()
        assertEquals(C.ENCODING_PCM_FLOAT, pcm.outputEncoding(C.ENCODING_PCM_24BIT))
        val in24 = ByteBuffer.allocate(3).apply { put(0x00); put(0x00); put(0x40); flip() } // 0x400000 = 0.5
        val out = ByteBuffer.allocate(4)
        pcm.process(in24, C.ENCODING_PCM_24BIT, 0.5f, out)
        assertEquals(0.25f, out.order(ByteOrder.LITTLE_ENDIAN).float, 1e-7f)

        val inF = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).apply { putFloat(0.8f); flip() }
        val outF = ByteBuffer.allocate(4)
        pcm.process(inF, C.ENCODING_PCM_FLOAT, 1f / 0.9f, outF)
        assertEquals(0.8f / 0.9f, outF.order(ByteOrder.LITTLE_ENDIAN).float, 1e-6f)
    }

    @Test
    fun ditherClampsAtFullScale() {
        val pcm = GainPcm()
        repeat(1000) { assertTrue(pcm.quantize16(1.5f) == 32767.toShort()) }
    }
}
