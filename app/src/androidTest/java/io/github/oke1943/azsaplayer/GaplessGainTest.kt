package io.github.oke1943.azsaplayer

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.oke1943.azsaplayer.data.TrackEntity
import io.github.oke1943.azsaplayer.library.LibAlbum
import io.github.oke1943.azsaplayer.library.LibTrack
import io.github.oke1943.azsaplayer.playback.GainAudioSink
import io.github.oke1943.azsaplayer.playback.GainContext
import io.github.oke1943.azsaplayer.playback.MediaItems
import io.github.oke1943.azsaplayer.settings.ReplayGainMode
import io.github.oke1943.azsaplayer.settings.ReplayGainSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * 5.4 の「曲の切り替わりでゲインをサンプル単位で正確に切り替える」をExoPlayerの実再生で確認する。
 * ゲインの異なる2曲をギャップレスで続けて再生し、出力の切り替わり位置が1曲目の長さと一致することを見る。
 * （T12 は実機での聴感確認。これはその前提となる自動確認）
 */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class GaplessGainTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /** ゲイン適用後のPCMを記録する。 */
    private class Recorder(sink: AudioSink) : ForwardingAudioSink(sink) {
        val bytes = ByteArrayOutputStream()
        override fun handleBuffer(buffer: ByteBuffer, presentationTimeUs: Long, encodedAccessUnitCount: Int): Boolean {
            val before = buffer.position()
            val dup = buffer.duplicate()
            val result = super.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
            val consumed = buffer.position() - before
            if (consumed > 0) {
                val chunk = ByteArray(consumed)
                dup.get(chunk)
                synchronized(bytes) { bytes.write(chunk) }
            }
            return result
        }
    }

    private fun item(id: Long, file: File, gainDb: Float, album: String): MediaItem {
        val e = TrackEntity(
            id = id, path = file.path, folder = file.parent!!, size = file.length(), mtime = 0, dateAdded = 0, title = "t$id", titleSort = null,
            album = album, albumSort = null, albumKey = album, artistSortText = "", albumArtistSortText = "", year = null, date = null,
            trackNo = null, trackTotal = null, discNo = null, discTotal = null, durationMs = 500, codec = "WAV", sampleRate = 44100,
            bitsPerSample = 16, channels = 2, bitrate = 0, playable = true, hasEmbeddedArt = false,
            rgTrackGain = gainDb, rgTrackPeak = null, rgAlbumGain = null, rgAlbumPeak = null,
        )
        val t = LibTrack(e, emptyList(), emptyList(), emptyList(), emptyList())
        t.album = LibAlbum(album, album, null, emptyList()).also { it.tracks += t }
        return MediaItems.build(t, "; ")
    }

    @Test
    fun gainSwitchesExactlyAtTrackBoundaryDuringGaplessPlayback() {
        val dir = File(context.cacheDir, "gapless").apply { mkdirs() }
        val framesA = 22050 // 0.5秒
        val framesB = 22050
        val a = File(dir, "a.wav").also { TestMedia.wavConstant(it, framesA, 16384) }
        val b = File(dir, "b.wav").also { TestMedia.wavConstant(it, framesB, 16384) }
        val rg = ReplayGainSettings(mode = ReplayGainMode.TRACK, preventClipping = false)

        lateinit var recorder: Recorder
        lateinit var player: ExoPlayer
        val ended = CountDownLatch(1)
        val main = Handler(Looper.getMainLooper())
        main.post {
            val renderers = object : DefaultRenderersFactory(context) {
                override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioOutputPlaybackParams: Boolean): AudioSink {
                    recorder = Recorder(DefaultAudioSink.Builder(context).setEnableFloatOutput(true).build())
                    return GainAudioSink(recorder, resolver = { tl, uid ->
                        val idx = tl.getIndexOfPeriod(uid ?: return@GainAudioSink null)
                        val window = tl.getWindow(tl.getPeriod(idx, androidx.media3.common.Timeline.Period()).windowIndex, androidx.media3.common.Timeline.Window())
                        GainContext(MediaItems.replayGain(window.mediaItem), false)
                    }, settings = { rg })
                }
            }
            player = ExoPlayer.Builder(context, renderers).build()
            player.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_ENDED) ended.countDown()
                }
            })
            // 1曲目 −6 dB（×0.501）、2曲目 −12 dB（×0.251）。別アルバム扱い
            player.setMediaItems(listOf(item(1, a, -6f, "A"), item(2, b, -12f, "B")))
            player.volume = 0f // 出力の音量（AudioTrack側）だけを下げる。記録はその手前
            player.prepare()
            player.play()
        }
        assertTrue("再生が終わること", ended.await(30, TimeUnit.SECONDS))
        main.post { player.release() }

        val pcm = ByteBuffer.wrap(synchronized(recorder.bytes) { recorder.bytes.toByteArray() }).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val samples = ShortArray(pcm.remaining()).also { pcm.get(it) }
        assertEquals("全サンプルが出力されること（曲間に無音の挿入なし）", (framesA + framesB) * 2, samples.size)

        val expectA = 16384 * 0.501187
        val expectB = 16384 * 0.251189
        for (i in samples.indices) {
            val expected = if (i < framesA * 2) expectA else expectB
            assertTrue("サンプル $i: ${samples[i]} ≠ $expected", abs(samples[i] - expected) <= 2.0)
        }
        dir.deleteRecursively()
    }
}
