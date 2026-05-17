package nl.blauw.pipplayer

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "play_history")
data class PlayHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val playedAt: Long = System.currentTimeMillis(),
    val isNavigation: Boolean = false,
    val mediaPaths: String = "[]"
)
