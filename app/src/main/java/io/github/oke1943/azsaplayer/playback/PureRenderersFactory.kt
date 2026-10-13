package io.github.oke1943.azsaplayer.playback

import android.content.Context
import android.media.MediaFormat
import android.os.Handler
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer
import androidx.media3.exoplayer.mediacodec.MediaCodecAdapter
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector

/**
 * 音質重視のレンダラ構成。
 * - 音声出力は [sinkFactory]（リプレイゲインを適用するGainAudioSink）
 * - デコーダ側で勝手に音量・ダイナミクスを変えないよう、AACのDRCとラウドネス正規化を無効にする
 * - 拡張レンダラ（MIDIなど）は読み込まない
 */
@UnstableApi
class PureRenderersFactory(
    context: Context,
    private val sinkFactory: (Context) -> AudioSink,
) : DefaultRenderersFactory(context) {

    override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioOutputPlaybackParams: Boolean): AudioSink =
        sinkFactory(context)

    override fun buildAudioRenderers(
        context: Context,
        extensionRendererMode: Int,
        mediaCodecSelector: MediaCodecSelector,
        enableDecoderFallback: Boolean,
        audioSink: AudioSink,
        eventHandler: Handler,
        eventListener: AudioRendererEventListener,
        out: ArrayList<Renderer>,
    ) {
        out.add(PureAudioRenderer(context, codecAdapterFactory, mediaCodecSelector, enableDecoderFallback, eventHandler, eventListener, audioSink))
    }
}

@UnstableApi
private class PureAudioRenderer(
    context: Context,
    adapterFactory: MediaCodecAdapter.Factory,
    selector: MediaCodecSelector,
    fallback: Boolean,
    handler: Handler,
    listener: AudioRendererEventListener,
    sink: AudioSink,
) : MediaCodecAudioRenderer(context, adapterFactory, selector, fallback, handler, listener, sink) {

    override fun getMediaFormat(format: Format, codecMimeType: String, codecMaxInputSize: Int, codecOperatingRate: Float): MediaFormat {
        val mf = super.getMediaFormat(format, codecMimeType, codecMaxInputSize, codecOperatingRate)
        if (codecMimeType == MimeTypes.AUDIO_AAC) {
            // 目標ラウドネス −1：ラウドネス正規化を行わない
            mf.setInteger(MediaFormat.KEY_AAC_DRC_TARGET_REFERENCE_LEVEL, -1)
            // DRCの効果なし（−1 = Off）、圧縮・持ち上げも0
            mf.setInteger(MediaFormat.KEY_AAC_DRC_EFFECT_TYPE, -1)
            mf.setInteger(MediaFormat.KEY_AAC_DRC_BOOST_FACTOR, 0)
            mf.setInteger(MediaFormat.KEY_AAC_DRC_ATTENUATION_FACTOR, 0)
            mf.setInteger(MediaFormat.KEY_AAC_DRC_HEAVY_COMPRESSION, 0)
        }
        return mf
    }
}
