package io.github.oke1943.azsaplayer.playback

import androidx.media3.common.C
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

/**
 * PCMに倍率をかける（5.4）。内部は32ビット浮動小数点で計算する。
 * - 入力が16ビット整数なら16ビットで出力し、丸めの前にTPDFディザをかける
 * - 入力が24/32ビット整数・浮動小数点なら浮動小数点で出力する（丸めは発生しない）
 */
class GainPcm(seed: Long = 0x5EED) {

    private var rng = seed.toInt() or 1

    fun outputEncoding(inputEncoding: Int): Int =
        if (inputEncoding == C.ENCODING_PCM_16BIT) C.ENCODING_PCM_16BIT else C.ENCODING_PCM_FLOAT

    fun outputSize(inputBytes: Int, inputEncoding: Int): Int = when (inputEncoding) {
        C.ENCODING_PCM_16BIT -> inputBytes
        C.ENCODING_PCM_24BIT -> inputBytes / 3 * 4
        else -> inputBytes
    }

    /** [input] の残り全部を処理して [out] に書く。[out] は書き込み後に flip 済みで返す。 */
    fun process(input: ByteBuffer, inputEncoding: Int, factor: Float, out: ByteBuffer) {
        val src = input.order(ByteOrder.LITTLE_ENDIAN)
        out.order(ByteOrder.LITTLE_ENDIAN)
        when (inputEncoding) {
            C.ENCODING_PCM_16BIT -> {
                if (factor == 1f) {
                    // 倍率1は無加工で通す（ビットパーフェクト）
                    out.put(src)
                } else {
                    while (src.remaining() >= 2) {
                        val x = src.short / 32768f * factor
                        out.putShort(quantize16(x))
                    }
                }
            }
            C.ENCODING_PCM_24BIT -> while (src.remaining() >= 3) {
                val b0 = src.get().toInt() and 0xFF
                val b1 = src.get().toInt() and 0xFF
                val b2 = src.get().toInt()
                val v = (b2 shl 16) or (b1 shl 8) or b0
                out.putFloat(v / 8388608f * factor)
            }
            C.ENCODING_PCM_32BIT -> while (src.remaining() >= 4) {
                out.putFloat((src.int / 2147483648.0).toFloat() * factor)
            }
            C.ENCODING_PCM_FLOAT -> while (src.remaining() >= 4) {
                out.putFloat(src.float * factor)
            }
            else -> throw IllegalArgumentException("Unsupported PCM encoding: $inputEncoding")
        }
        out.flip()
    }

    /** TPDFディザ（±1 LSBの三角分布）をかけてから16ビットに丸める。 */
    fun quantize16(x: Float): Short {
        val scaled = x * 32768f + (nextUniform() - nextUniform())
        val r = scaled.roundToInt().coerceIn(-32768, 32767)
        return r.toShort()
    }

    private fun nextUniform(): Float {
        // xorshift32：音声スレッドで割り当てなしに使える軽量な乱数
        var x = rng
        x = x xor (x shl 13)
        x = x xor (x ushr 17)
        x = x xor (x shl 5)
        rng = x
        return (x ushr 8) / 16777216f
    }

    companion object {
        fun isSupported(encoding: Int) = encoding == C.ENCODING_PCM_16BIT || encoding == C.ENCODING_PCM_24BIT ||
            encoding == C.ENCODING_PCM_32BIT || encoding == C.ENCODING_PCM_FLOAT
    }
}
