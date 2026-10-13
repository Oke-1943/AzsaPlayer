package io.github.oke1943.azsaplayer.library

import io.github.oke1943.azsaplayer.data.Role
import io.github.oke1943.azsaplayer.settings.ListStyle
import io.github.oke1943.azsaplayer.settings.SortBy
import io.github.oke1943.azsaplayer.settings.ViewConfig
import io.github.oke1943.azsaplayer.settings.ViewKind
import io.github.oke1943.azsaplayer.tag.TagRules
import java.io.File

/** 一覧の1行。グループ（アーティストやアルバムなど）か曲。 */
sealed interface BrowseEntry {
    val key: String
    val title: String
    val sortText: String
}

class GroupEntry(
    val kind: ViewKind,
    override val key: String,
    override val title: String,
    override val sortText: String,
    val tracks: List<LibTrack>,
    val album: LibAlbum? = null,
    val person: LibPerson? = null,
    val subtitle: String = "",
) : BrowseEntry {
    val year: Int? get() = album?.year ?: tracks.mapNotNull { it.e.year }.minOrNull()
    val dateAdded: Long get() = tracks.maxOfOrNull { it.e.dateAdded } ?: 0
    val artTrack: LibTrack? get() = album?.artTrack ?: tracks.firstOrNull { it.e.hasEmbeddedArt } ?: tracks.firstOrNull()
}

class TrackEntry(val track: LibTrack, val position: Int) : BrowseEntry {
    override val key: String get() = "t${track.id}#$position"
    override val title: String get() = track.title
    override val sortText: String get() = track.e.titleSort ?: track.title
}

/** 一覧に出す言語依存の文言。既定値は英語（単体テスト用）。 */
data class BrowseLabels(
    val noAlbum: String = "(No album title)",
    val noYear: String = "(No year)",
    val decade: (Int) -> String = { "${it}s" },
    val tracks: (Int) -> String = { "$it tracks" },
)

/** 段の選択（例：アルバムアーティスト「米津玄師」を開いた）。 */
data class Selection(val kind: ViewKind, val key: String, val title: String)

data class PlaylistInfo(val id: Long, val name: String, val trackIds: List<Long>)

/**
 * ビューと選択の並びから一覧を組み立てる（6.1〜6.3）。
 * ライブラリはメモリ上にあるため、どの段の組み合わせでもSQLを書き足さずに扱える。
 */
class BrowseEngine(
    private val lib: LibrarySnapshot,
    private val sort: SortRules,
    private val playlists: List<PlaylistInfo>,
    /** カスタムビュー用：タグ名 → (曲ID → 分割済みの値)。 */
    private val customValues: (String) -> Map<Long, List<String>>,
    /** 表示言語に合わせた文言（画面側で用意する）。 */
    private val labels: BrowseLabels = BrowseLabels(),
) {

    /** [path] の選択をすべて満たす曲（順序つき）。プレイリストを選んだ場合はその並び。 */
    fun tracksFor(view: ViewConfig, path: List<Selection>): List<LibTrack> {
        var tracks: List<LibTrack> = lib.tracks
        for (sel in path) {
            tracks = when (sel.kind) {
                ViewKind.PLAYLIST -> {
                    val p = playlists.firstOrNull { it.id.toString() == sel.key }
                    p?.trackIds?.mapNotNull { lib.tracksById[it] } ?: emptyList()
                }
                else -> tracks.filter { matches(it, sel, view) }
            }
        }
        return tracks
    }

    private fun matches(t: LibTrack, sel: Selection, view: ViewConfig): Boolean = when (sel.kind) {
        ViewKind.ALBUM_ARTIST -> t.effectiveAlbumArtists.any { it.id.toString() == sel.key }
        ViewKind.ARTIST -> t.artists.any { it.id.toString() == sel.key }
        ViewKind.COMPOSER -> t.composers.any { it.id.toString() == sel.key }
        ViewKind.ALBUM -> t.album.key == sel.key
        ViewKind.GENRE -> t.genres.any { TagRules.identityKey(it) == sel.key }
        ViewKind.YEAR -> (t.e.year?.toString() ?: "") == sel.key
        ViewKind.DECADE -> (t.e.year?.let { (it / 10 * 10).toString() } ?: "") == sel.key
        ViewKind.FOLDER -> t.e.folder == sel.key
        ViewKind.CUSTOM -> customValues(view.customTag)[t.id].orEmpty().any { TagRules.identityKey(it) == sel.key }
        else -> true
    }

    fun entries(view: ViewConfig, level: Int, path: List<Selection>): List<BrowseEntry> {
        val kind = view.levels.getOrElse(level) { ViewKind.TRACK }
        val style = view.styleAt(level)
        if (kind == ViewKind.PLAYLIST) return sortGroups(playlistGroups(), style)
        val tracks = tracksFor(view, path)
        val inPlaylist = path.any { it.kind == ViewKind.PLAYLIST }
        return when (kind) {
            ViewKind.TRACK, ViewKind.RECENT -> {
                val entries = tracks.mapIndexed { i, t -> TrackEntry(t, i) }
                if (inPlaylist && style.sortBy == SortBy.TRACK_NUMBER) entries else sortTracks(entries, style, kind)
            }
            else -> sortGroups(groups(kind, tracks, view), style)
        }
    }

    private fun playlistGroups(): List<GroupEntry> = playlists.map { p ->
        GroupEntry(ViewKind.PLAYLIST, p.id.toString(), p.name, p.name, p.trackIds.mapNotNull { lib.tracksById[it] })
    }

    private fun groups(kind: ViewKind, tracks: List<LibTrack>, view: ViewConfig): List<GroupEntry> {
        fun byPerson(role: Int, effective: Boolean = false): List<GroupEntry> {
            val map = LinkedHashMap<Long, MutableList<LibTrack>>()
            for (t in tracks) {
                val people = if (effective) t.effectiveAlbumArtists else t.people(role)
                for (p in people) map.getOrPut(p.id) { ArrayList() } += t
            }
            return map.mapNotNull { (id, list) ->
                val p = lib.persons[id] ?: return@mapNotNull null
                GroupEntry(kind, id.toString(), p.name, p.sortKey, list, person = p)
            }
        }

        fun byValue(values: (LibTrack) -> List<String>): List<GroupEntry> {
            val map = LinkedHashMap<String, Pair<String, MutableList<LibTrack>>>()
            for (t in tracks) for (v in values(t)) {
                map.getOrPut(TagRules.identityKey(v)) { v to ArrayList() }.second += t
            }
            return map.map { (k, pair) -> GroupEntry(kind, k, pair.first, pair.first, pair.second) }
        }

        return when (kind) {
            ViewKind.ALBUM_ARTIST -> byPerson(Role.ALBUM_ARTIST, effective = true)
            ViewKind.ARTIST -> byPerson(Role.ARTIST)
            ViewKind.COMPOSER -> byPerson(Role.COMPOSER)
            ViewKind.ALBUM -> tracks.groupBy { it.album }.map { (album, list) ->
                GroupEntry(
                    kind, album.key, album.title.ifEmpty { labels.noAlbum }, album.titleSort ?: album.title, list,
                    album = album,
                )
            }
            ViewKind.GENRE -> byValue { it.genres }
            ViewKind.YEAR -> tracks.groupBy { it.e.year }.map { (y, list) ->
                GroupEntry(kind, y?.toString() ?: "", y?.toString() ?: labels.noYear, y?.toString() ?: "", list)
            }
            ViewKind.DECADE -> tracks.groupBy { it.e.year?.let { y -> y / 10 * 10 } }.map { (d, list) ->
                GroupEntry(kind, d?.toString() ?: "", d?.let { labels.decade(it) } ?: labels.noYear, d?.toString() ?: "", list)
            }
            ViewKind.FOLDER -> tracks.groupBy { it.e.folder }.map { (f, list) ->
                GroupEntry(kind, f, File(f).name, f, list, subtitle = f)
            }
            ViewKind.CUSTOM -> {
                val values = customValues(view.customTag)
                byValue { values[it.id].orEmpty() }
            }
            else -> emptyList()
        }
    }

    private fun sortGroups(groups: List<GroupEntry>, style: ListStyle): List<GroupEntry> {
        val keyed = groups.map { it to sort.key(it.sortText) }
        val byKey = Comparator<Pair<GroupEntry, SortKey>> { a, b -> a.second.compareTo(b.second) }
        val primary: Comparator<Pair<GroupEntry, SortKey>>? = when (style.sortBy) {
            SortBy.NAME -> Comparator { a, b -> sort.compare(a.first.title, b.first.title) }
            SortBy.YEAR -> compareBy(nullsLast()) { it.first.year }
            SortBy.DATE_ADDED -> compareBy { it.first.dateAdded }
            SortBy.COUNT -> compareBy { it.first.tracks.size }
            // アルバムをアルバムアーティストの並び順で（同じアーティストの中は年順）
            SortBy.ARTIST -> Comparator<Pair<GroupEntry, SortKey>> { a, b ->
                sort.compare(artistSortText(a.first), artistSortText(b.first))
            }.then(compareBy(nullsLast()) { it.first.year })
            else -> null
        }
        val cmp = when {
            primary == null -> if (style.descending) byKey.reversed() else byKey
            style.descending -> primary.reversed().then(byKey)
            else -> primary.then(byKey)
        }
        return keyed.sortedWith(cmp).map { it.first }
    }

    private fun artistSortText(g: GroupEntry): String =
        (g.album?.albumArtists ?: g.tracks.firstOrNull()?.artists).orEmpty().joinToString("; ") { it.sortKey }

    private fun sortTracks(entries: List<TrackEntry>, style: ListStyle, kind: ViewKind): List<TrackEntry> {
        val byNumber = compareBy<TrackEntry> { it.track.e.discNo ?: 1 }
            .thenBy { it.track.e.trackNo ?: Int.MAX_VALUE }
        val keyed = entries.map { it to sort.key(it.sortText) }
        val titleKey = Comparator<Pair<TrackEntry, SortKey>> { a, b -> a.second.compareTo(b.second) }
        val primary: Comparator<Pair<TrackEntry, SortKey>>? = when (style.sortBy) {
            SortBy.TRACK_NUMBER -> Comparator { a, b ->
                sort.compare(a.first.track.album.titleSort ?: a.first.track.album.title, b.first.track.album.titleSort ?: b.first.track.album.title)
                    .takeIf { it != 0 } ?: byNumber.compare(a.first, b.first)
            }
            SortBy.ARTIST -> Comparator { a, b -> sort.compare(a.first.track.e.artistSortText, b.first.track.e.artistSortText) }
            SortBy.ALBUM -> Comparator { a, b ->
                sort.compare(a.first.track.album.titleSort ?: a.first.track.album.title, b.first.track.album.titleSort ?: b.first.track.album.title)
                    .takeIf { it != 0 } ?: byNumber.compare(a.first, b.first)
            }
            SortBy.NAME -> Comparator { a, b -> sort.compare(a.first.title, b.first.title) }
            SortBy.YEAR -> compareBy(nullsLast()) { it.first.track.e.year }
            SortBy.DATE_ADDED -> compareBy { it.first.track.e.dateAdded }
            else -> null
        }
        val effectivePrimary = if (kind == ViewKind.RECENT && style.sortBy == SortBy.SORT_KEY) null else primary
        val cmp = when {
            effectivePrimary == null -> if (style.descending) titleKey.reversed() else titleKey
            style.descending -> effectivePrimary.reversed().then(titleKey)
            else -> effectivePrimary.then(titleKey)
        }
        return keyed.sortedWith(cmp).map { it.first }
    }
}
