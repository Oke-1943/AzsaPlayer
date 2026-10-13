package io.github.oke1943.azsaplayer.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** 曲（7章）。タグの値は表示用の通常タグ、並び順にはソートタグを使う（4章）。 */
@Entity(tableName = "tracks", indices = [Index(value = ["path"], unique = true), Index("albumKey")])
data class TrackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val path: String,
    val folder: String,
    val size: Long,
    val mtime: Long,
    val dateAdded: Long,
    val title: String,
    val titleSort: String?,
    val album: String,
    val albumSort: String?,
    /** アルバムの同一性キー（Album＋Album Artist、3.5）。 */
    val albumKey: String,
    /** 曲単位でアーティスト順に並べるときの文字列（4.2）。 */
    val artistSortText: String,
    val albumArtistSortText: String,
    val year: Int?,
    val date: String?,
    val trackNo: Int?,
    val trackTotal: Int?,
    val discNo: Int?,
    val discTotal: Int?,
    val durationMs: Long,
    val codec: String,
    val sampleRate: Int,
    val bitsPerSample: Int,
    val channels: Int,
    val bitrate: Int,
    val playable: Boolean,
    val hasEmbeddedArt: Boolean,
    val rgTrackGain: Float?,
    val rgTrackPeak: Float?,
    val rgAlbumGain: Float?,
    val rgAlbumPeak: Float?,
)

/** 人物（アーティスト・アルバムアーティスト・作曲者の共通実体）。 */
@Entity(tableName = "persons", indices = [Index(value = ["nameKey"], unique = true)])
data class PersonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val nameKey: String,
    val sortKey: String,
)

object Role {
    const val ARTIST = 0
    const val ALBUM_ARTIST = 1
    const val COMPOSER = 2
}

/** 曲と人物の結合表。役割と出現順、その曲で対応づいたソート値を持つ（3.5・4.2）。 */
@Entity(
    tableName = "track_persons",
    primaryKeys = ["trackId", "role", "position"],
    indices = [Index("personId")],
)
data class TrackPersonEntity(
    val trackId: Long,
    val role: Int,
    val position: Int,
    val personId: Long,
    val sortValue: String?,
)

/** ジャンル等の多値フィールド用の値表。 */
@Entity(tableName = "field_values", indices = [Index(value = ["field", "valueKey"], unique = true)])
data class FieldValueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val field: String,
    val value: String,
    val valueKey: String,
)

@Entity(
    tableName = "track_field_values",
    primaryKeys = ["trackId", "field", "position"],
    indices = [Index("valueId")],
)
data class TrackFieldValueEntity(
    val trackId: Long,
    val field: String,
    val position: Int,
    val valueId: Long,
)

/** TagLibが返した全フィールド（カスタムビュー・テンプレート用）。値は分割前の一次情報。 */
@Entity(tableName = "track_tags", primaryKeys = ["trackId", "key", "position"], indices = [Index("key")])
data class TrackTagEntity(
    val trackId: Long,
    val key: String,
    val position: Int,
    val value: String,
)

@Entity(tableName = "albums", indices = [Index(value = ["albumKey"], unique = true)])
data class AlbumEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val albumKey: String,
    val title: String,
    val titleSort: String?,
    val albumArtist: String,
    val year: Int?,
    val trackCount: Int,
    val artTrackId: Long?,
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "playlist_tracks", primaryKeys = ["playlistId", "position"])
data class PlaylistTrackEntity(
    val playlistId: Long,
    val position: Int,
    val trackId: Long,
)

@Entity(tableName = "play_history", indices = [Index("trackId")])
data class PlayHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackId: Long,
    val playedAt: Long,
)

@Entity(tableName = "scan_errors")
data class ScanErrorEntity(
    @PrimaryKey val path: String,
    val reason: String,
    @ColumnInfo(defaultValue = "0") val at: Long,
)

data class FileStamp(val id: Long, val path: String, val size: Long, val mtime: Long, val dateAdded: Long)

data class PersonSortCandidate(val personId: Long, val sortValue: String, val count: Int, val firstPath: String)
