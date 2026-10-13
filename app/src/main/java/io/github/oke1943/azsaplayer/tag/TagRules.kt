package io.github.oke1943.azsaplayer.tag

import java.text.Normalizer

/** 複数値の解析規則（仕様書 3.3）。 */
object TagRules {

    /** 規則2〜4：区切り記号で分割し、前後の空白を除いてNFC正規化、空文字列は捨てる（重複はまだ残す）。 */
    fun splitRaw(values: List<String>, separators: List<String>): List<String> {
        var parts = values
        for (sep in separators.filter { it.isNotEmpty() }) {
            parts = parts.flatMap { it.split(sep) }
        }
        return parts.map { Normalizer.normalize(it.trim(), Normalizer.Form.NFC) }.filter { it.isNotEmpty() }
    }

    /** 規則5まで適用した最終的な値の並び。同じ名前（大文字小文字を区別しない）は最初の1つにまとめる。 */
    fun split(values: List<String>, separators: List<String>): List<String> =
        splitRaw(values, separators).distinctBy { identityKey(it) }

    /** 同一人物・同一値とみなすための比較キー（NFC済みの文字列を小文字化）。 */
    fun identityKey(name: String): String = Normalizer.normalize(name, Normalizer.Form.NFC).lowercase()

    /**
     * 名前とソート値の対応づけ（4.2）。
     * 分割後の個数が一致する場合のみ i 番目同士を対応させる。重複は名前で除き、最初の組を残す。
     */
    fun pairWithSort(names: List<String>, sorts: List<String>, separators: List<String>): List<Pair<String, String?>> {
        val n = splitRaw(names, separators)
        val s = splitRaw(sorts, separators)
        val pairs = if (s.size == n.size) n.zip(s) { a, b -> a to b as String? } else n.map { it to null }
        return pairs.distinctBy { identityKey(it.first) }
    }
}
