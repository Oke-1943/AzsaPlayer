package io.github.oke1943.azsaplayer.library

import io.github.oke1943.azsaplayer.data.Role
import io.github.oke1943.azsaplayer.data.TrackEntity

/** 人物（分割後の1人）。 */
class LibPerson(val id: Long, val name: String, val sortKey: String)

/** メモリ上の曲。表示や並び替えはこのオブジェクトから行う。 */
class LibTrack(
    val e: TrackEntity,
    val artists: List<LibPerson>,
    val albumArtists: List<LibPerson>,
    val composers: List<LibPerson>,
    val genres: List<String>,
) {
    val id get() = e.id
    val title get() = e.title
    lateinit var album: LibAlbum
        internal set

    fun people(role: Int): List<LibPerson> = when (role) {
        Role.ARTIST -> artists
        Role.ALBUM_ARTIST -> albumArtists
        else -> composers
    }

    /** アルバムアーティストがない曲はアーティストで代用（アルバムの同一性と同じ規則）。 */
    val effectiveAlbumArtists: List<LibPerson> get() = albumArtists.ifEmpty { artists }
}

class LibAlbum(
    val key: String,
    val title: String,
    val titleSort: String?,
    val albumArtists: List<LibPerson>,
) {
    val tracks = ArrayList<LibTrack>()
    val year: Int? get() = tracks.mapNotNull { it.e.year }.minOrNull()
    val dateAdded: Long get() = tracks.maxOf { it.e.dateAdded }

    /** アートワークを取り出す代表曲（埋め込み画像を持つ曲を優先）。 */
    val artTrack: LibTrack get() = tracks.firstOrNull { it.e.hasEmbeddedArt } ?: tracks.first()
}

/** ライブラリ全体のスナップショット。スキャン完了ごとに作り直す。 */
class LibrarySnapshot(
    val tracks: List<LibTrack>,
    val albums: List<LibAlbum>,
    val persons: Map<Long, LibPerson>,
) {
    val tracksById: Map<Long, LibTrack> = tracks.associateBy { it.id }
    val albumsByKey: Map<String, LibAlbum> = albums.associateBy { it.key }

    companion object {
        val EMPTY = LibrarySnapshot(emptyList(), emptyList(), emptyMap())
    }
}
