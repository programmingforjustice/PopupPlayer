package nl.blauw.pipplayer

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 플레이리스트 엔티티
 * - id: 자동 생성 PK
 * - name: 플레이리스트 이름 (고유)
 * - createdAt: 생성 시각 (epoch ms)
 */
@Entity(tableName = "playlists")
data class Playlist(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)
