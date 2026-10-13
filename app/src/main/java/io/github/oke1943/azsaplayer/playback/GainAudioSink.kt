package io.github.oke1943.azsaplayer.playback

import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * リプレイゲインを適用する音声出力（5.4）。
 *
 * ExoPlayerは曲（ストリーム）が切り替わるたびに、その曲の最初のバッファを渡す前に configure を呼ぶ。
 * 補正量はここで決め、以降のバッファにだけ適用するため、ギャップレス再生でも
 * 前の曲の末尾に次の曲のゲインがかかることはない（サンプル単位で正確に切り替わる）。
 * 端末の音量機構（システムボリューム、LoudnessEnhancer）は使わない。
 */
@UnstableApi
class GainAudioSink(
    sink: AudioSink,
    /** (タイムライン, 期間UID) → この曲のゲイン値と再生文脈。再生スレッドから呼ばれる。 */
    private val resolver: (Timeline, Any?) -> GainContext?,
    /** 現在のリプレイゲイン設定。設定変更は再生中の曲にも即座に反映する。 */
    private val settings: () -> io.github.oke1943.azsaplayer.settings.ReplayGainSettings,
) : ForwardingAudioSink(sink) {

    private val pcm = GainPcm()
    private var active = false
    private var inputEncoding = C.ENCODING_INVALID
    private var context: GainContext? = null
    private var pendingOutput: ByteBuffer? = null
    private var scratch: ByteBuffer = ByteBuffer.allocateDirect(0)

    /** 最後に設定した補正（デバッグ・テスト用）。 */
    @Volatile var lastDecision: GainDecision = GainDecision.UNITY
        private set

    override fun getFormatSupport(format: androidx.media3.common.Format): Int {
        if (MimeTypes.AUDIO_RAW == format.sampleMimeType && GainPcm.isSupported(format.pcmEncoding)) {
            val out = format.buildUpon().setPcmEncoding(pcm.outputEncoding(format.pcmEncoding)).build()
            return super.getFormatSupport(out)
        }
        return super.getFormatSupport(format)
    }

    override fun supportsFormat(format: androidx.media3.common.Format): Boolean =
        getFormatSupport(format) != AudioSink.SINK_FORMAT_UNSUPPORTED

    override fun configure(audioSinkConfig: AudioSink.AudioSinkConfig) {
        val format = audioSinkConfig.format
        if (MimeTypes.AUDIO_RAW == format.sampleMimeType && GainPcm.isSupported(format.pcmEncoding)) {
            context = resolver(audioSinkConfig.timeline, audioSinkConfig.mediaPeriodId?.periodUid)
            inputEncoding = format.pcmEncoding
            active = true
            val outFormat = format.buildUpon().setPcmEncoding(pcm.outputEncoding(inputEncoding)).build()
            @Suppress("DEPRECATION")
            val builder = AudioSink.AudioSinkConfig.Builder(outFormat)
                .setTimeline(audioSinkConfig.timeline)
                .setMediaPeriodId(audioSinkConfig.mediaPeriodId)
                .setOutputChannelMapping(audioSinkConfig.outputChannelMapping)
                .setPreferredBufferSizeOverride(audioSinkConfig.preferredBufferSizeOverride)
            super.configure(builder.build())
        } else {
            active = false
            super.configure(audioSinkConfig)
        }
    }

    override fun handleBuffer(buffer: ByteBuffer, presentationTimeUs: Long, encodedAccessUnitCount: Int): Boolean {
        if (!active) return super.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
        val out = pendingOutput ?: convert(buffer).also { pendingOutput = it }
        val consumed = super.handleBuffer(out, presentationTimeUs, encodedAccessUnitCount)
        if (consumed) pendingOutput = null
        return consumed
    }

    private fun convert(buffer: ByteBuffer): ByteBuffer {
        val size = pcm.outputSize(buffer.remaining(), inputEncoding)
        if (scratch.capacity() < size) scratch = ByteBuffer.allocateDirect(size * 2).order(ByteOrder.nativeOrder())
        // 下流が前のバッファを読み切ってから（handleBufferがtrueを返してから）しか呼ばれないので使い回せる
        scratch.clear()
        val decision = context?.let { ReplayGainCalc.decide(it.info, settings(), it.albumContext) } ?: GainDecision.UNITY
        lastDecision = decision
        pcm.process(buffer, inputEncoding, decision.factor, scratch)
        return scratch
    }

    override fun flush() {
        pendingOutput = null
        super.flush()
    }

    override fun reset() {
        pendingOutput = null
        active = false
        super.reset()
    }
}

/** 1曲分のゲイン値と「アルバム単位で再生している」かどうか。 */
data class GainContext(val info: io.github.oke1943.azsaplayer.tag.ReplayGainInfo, val albumContext: Boolean)
