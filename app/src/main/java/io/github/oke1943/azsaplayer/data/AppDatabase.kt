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
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun libraryDao(): LibraryDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun historyDao(): HistoryDao

    companion object {
        /** ver.0.6.2：再生履歴に「半分以上聞いたか」と再生時間を追加。それまでの記録は聞いたもの扱い。 */
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE play_history ADD COLUMN completed INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE play_history ADD COLUMN listenedMs INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun create(context: Context, name: String? = "library.db"): AppDatabase {
            val builder = if (name == null) {
                Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            } else {
                Room.databaseBuilder(context, AppDatabase::class.java, name).addMigrations(MIGRATION_1_2)
            }
            return builder.build()
        }
    }
}
