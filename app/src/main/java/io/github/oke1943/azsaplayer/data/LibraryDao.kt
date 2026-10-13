package io.github.oke1943.azsaplayer.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface LibraryDao {

    // ---- 曲 ----
    @Query("SELECT id, path, size, mtime, dateAdded FROM tracks")
    suspend fun fileStamps(): List<FileStamp>

    @Upsert
    suspend fun upsertTrack(track: TrackEntity): Long

    @Query("SELECT * FROM tracks")
    suspend fun allTracks(): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE id = :id")
    suspend fun track(id: Long): TrackEntity?

    @Query("SELECT * FROM tracks WHERE id IN (:ids)")
    suspend fun tracks(ids: List<Long>): List<TrackEntity>

    @Query("DELETE FROM tracks WHERE id IN (:ids)")
    suspend fun deleteTracks(ids: List<Long>)

    @Query("SELECT COUNT(*) FROM tracks")
    suspend fun trackCount(): Int

    // ---- 人物 ----
    @Query("SELECT * FROM persons")
    suspend fun allPersons(): List<PersonEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPerson(person: PersonEntity): Long

    @Query("UPDATE persons SET sortKey = :sortKey WHERE id = :id")
    suspend fun setPersonSortKey(id: Long, sortKey: String)

    @Query("DELETE FROM persons WHERE id NOT IN (SELECT DISTINCT personId FROM track_persons)")
    suspend fun deleteOrphanPersons()

    @Query("SELECT * FROM track_persons")
    suspend fun allTrackPersons(): List<TrackPersonEntity>

    @Query("DELETE FROM track_persons WHERE trackId IN (:trackIds)")
    suspend fun deleteTrackPersons(trackIds: List<Long>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrackPersons(rows: List<TrackPersonEntity>)

    /** 人物ごとのソート値の候補と出現数（4.2）。同数のときはパス順で先の曲を採るため最小パスも返す。 */
    @Query(
        """
        SELECT tp.personId AS personId, tp.sortValue AS sortValue, COUNT(*) AS count, MIN(t.path) AS firstPath
        FROM track_persons tp JOIN tracks t ON t.id = tp.trackId
        WHERE tp.sortValue IS NOT NULL AND tp.sortValue != ''
        GROUP BY tp.personId, tp.sortValue
        """,
    )
    suspend fun personSortCandidates(): List<PersonSortCandidate>

    // ---- 多値フィールド ----
    @Query("SELECT * FROM field_values")
    suspend fun allFieldValues(): List<FieldValueEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertFieldValue(value: FieldValueEntity): Long

    @Query("SELECT * FROM track_field_values")
    suspend fun allTrackFieldValues(): List<TrackFieldValueEntity>

    @Query("DELETE FROM track_field_values WHERE trackId IN (:trackIds)")
    suspend fun deleteTrackFieldValues(trackIds: List<Long>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrackFieldValues(rows: List<TrackFieldValueEntity>)

    @Query("DELETE FROM field_values WHERE id NOT IN (SELECT DISTINCT valueId FROM track_field_values)")
    suspend fun deleteOrphanFieldValues()

    // ---- 全タグ ----
    @Query("DELETE FROM track_tags WHERE trackId IN (:trackIds)")
    suspend fun deleteTrackTags(trackIds: List<Long>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrackTags(rows: List<TrackTagEntity>)

    @Query("SELECT * FROM track_tags WHERE key = :key")
    suspend fun tagsByKey(key: String): List<TrackTagEntity>

    @Query("SELECT * FROM track_tags WHERE trackId = :trackId")
    suspend fun tagsOfTrack(trackId: Long): List<TrackTagEntity>

    @Query("SELECT DISTINCT key FROM track_tags ORDER BY key")
    suspend fun allTagKeys(): List<String>

    // ---- アルバム ----
    @Query("DELETE FROM albums")
    suspend fun clearAlbums()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlbums(rows: List<AlbumEntity>)

    @Query("SELECT * FROM albums")
    suspend fun allAlbums(): List<AlbumEntity>

    // ---- 読み取りエラー ----
    @Query("SELECT * FROM scan_errors ORDER BY path")
    fun scanErrors(): Flow<List<ScanErrorEntity>>

    @Query("DELETE FROM scan_errors")
    suspend fun clearScanErrors()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScanErrors(rows: List<ScanErrorEntity>)

    @Transaction
    suspend fun deleteTracksFully(ids: List<Long>) {
        ids.chunked(500).forEach { chunk ->
            deleteTrackPersons(chunk)
            deleteTrackFieldValues(chunk)
            deleteTrackTags(chunk)
            deleteTracks(chunk)
        }
    }
}

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY name COLLATE NOCASE")
    fun playlists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists")
    suspend fun allPlaylists(): List<PlaylistEntity>

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun playlist(id: Long): PlaylistEntity?

    @Insert
    suspend fun insertPlaylist(p: PlaylistEntity): Long

    @Query("UPDATE playlists SET name = :name, updatedAt = :now WHERE id = :id")
    suspend fun rename(id: Long, name: String, now: Long)

    @Query("UPDATE playlists SET updatedAt = :now WHERE id = :id")
    suspend fun touch(id: Long, now: Long)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylistRow(id: Long)

    @Query("SELECT trackId FROM playlist_tracks WHERE playlistId = :id ORDER BY position")
    suspend fun trackIds(id: Long): List<Long>

    @Query("SELECT trackId FROM playlist_tracks WHERE playlistId = :id ORDER BY position")
    fun trackIdsFlow(id: Long): Flow<List<Long>>

    @Query("SELECT * FROM playlist_tracks")
    fun allEntries(): Flow<List<PlaylistTrackEntity>>

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :id")
    suspend fun clearTracks(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTracks(rows: List<PlaylistTrackEntity>)

    @Transaction
    suspend fun replaceTracks(id: Long, trackIds: List<Long>, now: Long) {
        clearTracks(id)
        insertTracks(trackIds.mapIndexed { i, t -> PlaylistTrackEntity(id, i, t) })
        touch(id, now)
    }

    @Transaction
    suspend fun deletePlaylist(id: Long) {
        clearTracks(id)
        deletePlaylistRow(id)
    }
}

@Dao
interface HistoryDao {
    @Insert
    suspend fun insert(row: PlayHistoryEntity)

    @Query("SELECT * FROM play_history ORDER BY playedAt DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<PlayHistoryEntity>
}
