package io.github.oke1943.azsaplayer.tag

import java.nio.ByteBuffer

/** TagLibが返した1件の値。[index] は値リスト内の位置（区切りの確認用）。 */
data class TagDumpRecord(
    val section: String,
    val key: String,
    val index: Int?,
    val value: String,
)

/** 区分 → キー → 値の並び。順序はTagLibが返した順のまま。 */
data class TagDumpField(val key: String, val values: List<TagDumpRecord>)
data class TagDumpSection(val name: String, val fields: List<TagDumpField>)

data class TagDump(val path: String, val records: List<TagDumpRecord>) {

    val sections: List<TagDumpSection>
        get() = records.groupBy { it.section }.map { (name, inSection) ->
            TagDumpSection(name, inSection.groupBy { it.key }.map { (key, values) -> TagDumpField(key, values) })
        }

    /** 共有・ファイル保存用のテキスト表現。 */
    fun toText(): String = buildString {
        append("==== ").append(path).append(" ====\n")
        for (section in sections) {
            append("[").append(section.name).append("]\n")
            for (field in section.fields) {
                append("  ").append(field.key).append('\n')
                for (r in field.values) append("    ").append(formatValueLine(r)).append('\n')
            }
        }
    }

    companion object {
        /**
         * ネイティブ側の形式（4フィールド × 「4バイト長 + UTF-8」）を読む。
         */
        fun parse(path: String, bytes: ByteArray): TagDump {
            val buf = ByteBuffer.wrap(bytes)
            fun field(): String {
                val n = buf.int
                val s = String(bytes, buf.position(), n, Charsets.UTF_8)
                buf.position(buf.position() + n)
                return s
            }
            val records = mutableListOf<TagDumpRecord>()
            while (buf.hasRemaining()) {
                val section = field()
                val key = field()
                val index = field().toIntOrNull()
                val value = field()
                records += TagDumpRecord(section, key, index, value)
            }
            return TagDump(path, records)
        }

        /** 例：`[0] ‹米津玄師›`。前後の空白が見えるよう値を‹›で囲み、制御文字は \uXXXX で示す。 */
        fun formatValueLine(r: TagDumpRecord): String {
            val prefix = r.index?.let { "[$it] " } ?: ""
            if (r.index == null) return prefix + r.value
            return "$prefix‹${escapeControl(r.value)}›"
        }

        fun escapeControl(s: String): String = buildString {
            for (c in s) {
                if (c.isISOControl() || c == '﻿') append("\\u%04X".format(c.code)) else append(c)
            }
        }
    }
}
