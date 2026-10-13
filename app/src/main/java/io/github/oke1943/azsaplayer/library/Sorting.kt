package io.github.oke1943.azsaplayer.library

import java.text.Normalizer
import java.util.Locale

/**
 * 並び替えの比較規則（4.3）。ICUのCollator（日本語ロケール）を使い、大文字小文字は区別しない。
 *
 * AndroidのICU日本語照合は、かな・漢字を英字より前に並べる。MusicBee（Windowsの日本語ロケール）は
 * 英字 → かな → 漢字の順なので、文字種の並び順（reorder codes）だけをそれに合わせる。
 * 単体テスト（JVM）ではandroid.icuが使えないため java.text.Collator で代用する。
 */
class SortRules(private val ignoreArticles: Boolean) {

    private val icu: android.icu.text.Collator? = runCatching {
        android.icu.text.Collator.getInstance(android.icu.util.ULocale.JAPANESE)?.apply {
            strength = android.icu.text.Collator.SECONDARY
            decomposition = android.icu.text.Collator.CANONICAL_DECOMPOSITION
            setReorderCodes(android.icu.lang.UScript.LATIN, android.icu.lang.UScript.HIRAGANA, android.icu.lang.UScript.HAN)
        }
    }.getOrNull()

    private val jvm: java.text.Collator by lazy {
        java.text.Collator.getInstance(Locale.JAPANESE).apply {
            strength = java.text.Collator.SECONDARY
            decomposition = java.text.Collator.CANONICAL_DECOMPOSITION
        }
    }

    /** 並び替えに使う文字列（冠詞の除去を反映）。 */
    fun normalize(sortText: String): String {
        val t = sortText.trim()
        if (!ignoreArticles) return t
        for (article in ARTICLES) {
            if (t.length > article.length && t.regionMatches(0, article, 0, article.length, ignoreCase = true)) {
                return t.substring(article.length).trimStart()
            }
        }
        return t
    }

    fun key(sortText: String): SortKey {
        val s = normalize(sortText)
        return icu?.let { SortKey(it.getCollationKey(s), null) } ?: SortKey(null, jvm.getCollationKey(s))
    }

    fun compare(a: String, b: String): Int = icu?.compare(normalize(a), normalize(b)) ?: jvm.compare(normalize(a), normalize(b))

    /** 早送りスクロールの索引文字。英字は大文字、数字・記号は「#」、かなは行の頭文字。 */
    fun indexChar(sortText: String): String = indexCharOf(normalize(sortText))

    companion object {
        private val ARTICLES = listOf("The ", "An ", "A ")

        private val ROW_STARTS = "あかさたなはまやらわ".toCharArray()
        private val ROW_BOUNDARIES = charArrayOf('あ', 'か', 'さ', 'た', 'な', 'は', 'ま', 'や', 'ら', 'わ')

        fun indexCharOf(text: String): String {
            val first = text.firstOrNull { !it.isWhitespace() } ?: return "#"
            // 濁点などを外し、全角英数を半角に寄せる
            val base = Normalizer.normalize(first.toString(), Normalizer.Form.NFKD).firstOrNull() ?: first
            return when {
                base in 'a'..'z' || base in 'A'..'Z' -> base.uppercaseChar().toString()
                base in 'ぁ'..'ゖ' -> kanaRow(base)
                base in 'ァ'..'ヶ' -> kanaRow(base - 0x60)
                Character.isLetter(base) && Character.UnicodeScript.of(base.code) == Character.UnicodeScript.HAN -> "漢"
                Character.isLetter(base) -> base.uppercaseChar().toString()
                else -> "#"
            }
        }

        private fun kanaRow(hiragana: Char): String {
            var row = ROW_STARTS[0]
            for (i in ROW_BOUNDARIES.indices) {
                if (hiragana >= ROW_BOUNDARIES[i]) row = ROW_STARTS[i]
            }
            if (hiragana >= 'を') row = 'わ' // を・ん
            return row.toString()
        }
    }
}

/** ICU・JVMどちらの照合キーでも比較できる入れ物。 */
class SortKey(private val icu: android.icu.text.CollationKey?, private val jvm: java.text.CollationKey?) : Comparable<SortKey> {
    override fun compareTo(other: SortKey): Int =
        if (icu != null && other.icu != null) icu.compareTo(other.icu) else jvm!!.compareTo(other.jvm)
}
