package nl.blauw.pipplayer

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PlayHistoryDao {
    @Insert
    suspend fun insert(history: PlayHistory): Long

    @Query("SELECT * FROM play_history ORDER BY playedAt DESC")
    fun getAll(): Flow<List<PlayHistory>>

    @Query("DELETE FROM play_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM play_history")
    suspend fun clearAll()
}
