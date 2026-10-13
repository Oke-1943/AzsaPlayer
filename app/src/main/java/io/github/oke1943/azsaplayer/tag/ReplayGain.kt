package io.github.oke1943.azsaplayer.tag

/** ファイルに書かれたリプレイゲイン値（仕様書 5.1）。値がなければnull。 */
data class ReplayGainInfo(
    val trackGain: Float? = null,
    val trackPeak: Float? = null,
    val albumGain: Float? = null,
    val albumPeak: Float? = null,
) {
    val hasAny: Boolean get() = trackGain != null || albumGain != null

    companion object {
        private val NUMBER = Regex("""[-+−]?\d+(?:[.,]\d+)?""")

        /** 「-6.54 dB」「+3,2dB」「−1.0」などから数値部分を取り出す。 */
        fun parseNumber(text: String?): Float? {
            if (text == null) return null
            val m = NUMBER.find(text.trim()) ?: return null
            return m.value.replace('−', '-').replace(',', '.').toFloatOrNull()?.takeIf { it.isFinite() }
        }

        /** Opus の R128 値（Q7.8固定小数点、基準 −23 LUFS）を ReplayGain 2.0 の基準（−18 LUFS）に揃える。 */
        fun r128ToReplayGainDb(q78: String?): Float? {
            val raw = q78?.trim()?.toIntOrNull() ?: return null
            return raw / 256f + 5f
        }

        /**
         * PropertyMap から読み取る。キーは大文字化済みであること
         * （ID3v2のTXXX、MP4の自由形式アトム、APE、Vorbis Commentとも大文字小文字を区別しない）。
         */
        fun fromProperties(props: Map<String, List<String>>, codec: String): ReplayGainInfo {
            fun first(key: String) = props[key]?.firstOrNull()
            val standard = ReplayGainInfo(
                trackGain = parseNumber(first("REPLAYGAIN_TRACK_GAIN")),
                trackPeak = parseNumber(first("REPLAYGAIN_TRACK_PEAK"))?.takeIf { it > 0f },
                albumGain = parseNumber(first("REPLAYGAIN_ALBUM_GAIN")),
                albumPeak = parseNumber(first("REPLAYGAIN_ALBUM_PEAK"))?.takeIf { it > 0f },
            )
            if (codec == "Opus") {
                val r128Track = r128ToReplayGainDb(first("R128_TRACK_GAIN"))
                val r128Album = r128ToReplayGainDb(first("R128_ALBUM_GAIN"))
                if (r128Track != null || r128Album != null) {
                    // Opus本来の方式を優先する。R128にはピーク値がない
                    return ReplayGainInfo(trackGain = r128Track, albumGain = r128Album)
                }
            }
            return standard
        }
    }
}
