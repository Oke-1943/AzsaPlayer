package io.github.oke1943.azsaplayer.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        TrackEntity::class,
        PersonEntity::class,
        TrackPersonEntity::class,
        FieldValueEntity::class,
        TrackFieldValueEntity::class,
        TrackTagEntity::class,
        AlbumEntity::class,
        PlaylistEntity::class,
        PlaylistTrackEntity::class,
        PlayHistoryEntity::class,
        ScanErrorEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun libraryDao(): LibraryDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun historyDao(): HistoryDao

    companion object {
        fun create(context: Context, name: String? = "library.db"): AppDatabase {
            val builder = if (name == null) {
                Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            } else {
                Room.databaseBuilder(context, AppDatabase::class.java, name)
            }
            return builder.build()
        }
    }
}
