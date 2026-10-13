package io.github.oke1943.azsaplayer.library

import io.github.oke1943.azsaplayer.data.Role
import java.text.Normalizer

/**
 * 検索（ver.5）。
 * - 対象：アーティスト（アーティスト・アルバムアーティスト・作曲者）、アルバム、曲
 * - 表記ゆれを吸収：全角/半角（NFKC）、大文字/小文字、カタカナ/ひらがな、空白の有無
 * - 表示名に加えてソートタグ（例：Jackson, Michael／Rokudenashi）でも見つかる
 * - 複数の語を空白で区切ると、すべてを含むものだけに絞る（例：「aimer dawn」）
 * - 並びは「先頭一致」→「途中一致」、同じ中では名前順
 */
object SearchText {
    fun normalize(s: String): String {
        val nfkc = Normalizer.normalize(s, Normalizer.Form.NFKC).lowercase()
        val sb = StringBuilder(nfkc.length)
        for (ch in nfkc) {
            when {
                ch.isWhitespace() -> {}
                ch in 'ァ'..'ヶ' -> sb.append(ch - 0x60) // カタカナ → ひらがな
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun terms(query: String): List<String> =
        // 全角スペースでも区切れるよう、先にNFKCで半角に揃える
        Normalizer.normalize(query, Normalizer.Form.NFKC).split(Regex("\\s+")).map { normalize(it) }.filter { it.isNotEmpty() }
}

data class SearchResults(
    val people: List<LibPerson>,
    val albums: List<LibAlbum>,
    val tracks: List<LibTrack>,
) {
    val isEmpty get() = people.isEmpty() && albums.isEmpty() && tracks.isEmpty()

    companion object {
        val EMPTY = SearchResults(emptyList(), emptyList(), emptyList())
    }
}

/** スナップショットごとに正規化済みの文字列を用意しておき、打鍵ごとの検索を速くする。 */
class SearchIndex(private val lib: LibrarySnapshot, private val sort: SortRules) {

    private class Item<T>(val value: T, val primary: String, val all: String, val sortText: String)

    private val people: List<Item<LibPerson>>
    private val albums: List<Item<LibAlbum>>
    private val tracks: List<Item<LibTrack>>

    init {
        val used = HashSet<Long>()
        lib.tracks.forEach { t -> (t.artists + t.albumArtists + t.composers).forEach { used += it.id } }
        people = lib.persons.values.filter { it.id in used }.map { p ->
            val n = SearchText.normalize(p.name)
            Item(p, n, n + "\u0000" + SearchText.normalize(p.sortKey), p.sortKey)
        }
        albums = lib.albums.map { a ->
            val n = SearchText.normalize(a.title)
            val extra = (a.albumArtists.flatMap { listOf(it.name, it.sortKey) } + listOfNotNull(a.titleSort))
                .joinToString("\u0000") { SearchText.normalize(it) }
            Item(a, n, n + "\u0000" + extra, a.titleSort ?: a.title)
        }
        tracks = lib.tracks.map { t ->
            val n = SearchText.normalize(t.title)
            // 曲は、アーティスト・アルバムアーティスト・作曲者・アルバム名でも見つかる（例：「beethoven allegro」）
            val people = (t.artists + t.effectiveAlbumArtists + t.composers).distinctBy { it.id }
            val extra = (people.flatMap { listOf(it.name, it.sortKey) } + listOf(t.e.album) + listOfNotNull(t.e.titleSort))
                .joinToString("\u0000") { SearchText.normalize(it) }
            Item(t, n, n + "\u0000" + extra, t.e.titleSort ?: t.title)
        }
    }

    fun search(query: String, limit: Int = 200): SearchResults {
        val terms = SearchText.terms(query)
        if (terms.isEmpty()) return SearchResults.EMPTY
        return SearchResults(rank(people, terms, limit), rank(albums, terms, limit), rank(tracks, terms, limit))
    }

    private fun <T> rank(items: List<Item<T>>, terms: List<String>, limit: Int): List<T> {
        val first = terms.first()
        return items.asSequence()
            .filter { item -> terms.all { item.all.contains(it) } }
            .map { item -> Triple(item, if (item.primary.startsWith(first)) 0 else 1, sort.key(item.sortText)) }
            .sortedWith(compareBy<Triple<Item<T>, Int, SortKey>> { it.second }.thenBy { it.third })
            .take(limit)
            .map { it.first.value }
            .toList()
    }

    /** その人を開くときに使う段：アルバムアーティストとして登場するならアルバムアーティスト、なければアーティスト。 */
    fun roleOf(person: LibPerson): Int =
        if (lib.tracks.any { t -> t.effectiveAlbumArtists.any { it.id == person.id } }) Role.ALBUM_ARTIST
        else if (lib.tracks.any { t -> t.artists.any { it.id == person.id } }) Role.ARTIST
        else Role.COMPOSER
}
