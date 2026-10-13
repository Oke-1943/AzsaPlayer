package io.github.oke1943.azsaplayer.tag

import java.nio.ByteBuffer

/** TagLib（JNI）への入口。読み取り専用で、ファイルへの書き込みは行わない。 */
object TagLibNative {
    init {
        System.loadLibrary("musicapp_native")
    }

    // パスはUTF-8のバイト列で渡す（JNIの修正UTF-8を避けるため）
    private external fun dumpNative(pathUtf8: ByteArray): ByteArray?
    private external fun readNative(pathUtf8: ByteArray): ByteArray?
    private external fun pictureNative(pathUtf8: ByteArray): ByteArray?

    /** 生タグダンプ（3.1）。 */
    fun dump(path: String): TagDump {
        val bytes = dumpNative(path.toByteArray(Charsets.UTF_8))
            ?: return TagDump(path, listOf(TagDumpRecord("Error", "Read failed", null, "No result was returned")))
        return TagDump.parse(path, bytes)
    }

    /** ライブラリ取り込み用の読み取り。 */
    fun read(path: String): RawTags {
        val bytes = readNative(path.toByteArray(Charsets.UTF_8))
            ?: return RawTags.error(RawTags.ERROR_NO_RESULT)
        return RawTags.parse(bytes)
    }

    /** 埋め込みアートワーク（表紙を優先）。なければnull。 */
    fun picture(path: String): ByteArray? = pictureNative(path.toByteArray(Charsets.UTF_8))
}

/**
 * TagLibが返したタグの一次情報。
 * [properties] のキーはTagLibのPropertyMap名（大文字）、値はTagLibが展開した複数値のリスト。
 */
data class RawTags(
    val properties: Map<String, List<String>>,
    val codec: String,
    val lengthMs: Long,
    val bitrate: Int,
    val sampleRate: Int,
    val channels: Int,
    val bitsPerSample: Int,
    val hasPicture: Boolean,
    val error: String? = null,
) {
    fun values(key: String): List<String> = properties[key] ?: emptyList()
    fun first(key: String): String? = values(key).firstOrNull()?.takeIf { it.isNotBlank() }

    companion object {
        /** 読み取り失敗の理由（コード）。画面で表示言語の文言に変える。 */
        const val ERROR_OPEN_FAILED = "open_failed"
        const val ERROR_NO_RESULT = "no_result"

        fun error(code: String) = RawTags(emptyMap(), "", 0, 0, 0, 0, 0, false, code)

        fun parse(bytes: ByteArray): RawTags {
            val buf = ByteBuffer.wrap(bytes)
            fun field(): String {
                val n = buf.int
                val s = String(bytes, buf.position(), n, Charsets.UTF_8)
                buf.position(buf.position() + n)
                return s
            }
            val props = LinkedHashMap<String, MutableList<String>>()
            val audio = HashMap<String, String>()
            var error: String? = null
            while (buf.hasRemaining()) {
                val section = field()
                val key = field()
                field() // index（出現順に並んでいるので不要）
                val value = field()
                when (section) {
                    "P" -> props.getOrPut(key.uppercase()) { mutableListOf() } += value
                    "A" -> audio[key] = value
                    "E" -> error = value
                }
            }
            return RawTags(
                properties = props,
                codec = audio["codec"] ?: "",
                lengthMs = audio["lengthMs"]?.toLongOrNull() ?: 0,
                bitrate = audio["bitrate"]?.toIntOrNull() ?: 0,
                sampleRate = audio["sampleRate"]?.toIntOrNull() ?: 0,
                channels = audio["channels"]?.toIntOrNull() ?: 0,
                bitsPerSample = audio["bits"]?.toIntOrNull() ?: 0,
                hasPicture = audio["picture"] == "1",
                error = error,
            )
        }
    }
}
