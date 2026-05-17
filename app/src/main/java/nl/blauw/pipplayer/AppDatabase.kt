package nl.blauw.pipplayer

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Room 데이터베이스 싱글톤
 * - entities: Playlist, PlaylistItem
 * - version : 1 (스키마 변경 시 증가 + Migration 추가)
 */
@Database(
    entities = [Playlist::class, PlaylistItem::class, PlayHistory::class],
    version  = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun playlistDao(): PlaylistDao
    abstract fun playHistoryDao(): PlayHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pip_player.db"
                )
                    .fallbackToDestructiveMigration()   // 개발 중 스키마 변경 대응
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
