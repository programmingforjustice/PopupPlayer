package nl.blauw.pipplayer

import android.content.Context
import kotlinx.coroutines.flow.Flow

class HistoryRepository(context: Context) {
    private val dao = AppDatabase.getInstance(context).playHistoryDao()

    fun getAll(): Flow<List<PlayHistory>> = dao.getAll()
    suspend fun insert(history: PlayHistory) = dao.insert(history)
    suspend fun deleteById(id: Long) = dao.deleteById(id)
    suspend fun clearAll() = dao.clearAll()
}
