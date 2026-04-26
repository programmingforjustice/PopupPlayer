package nl.blauw.pipplayer

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 플레이리스트 아이템 엔티티
 * - playlistId: 소속 플레이리스트 FK (Playlist.id)
 * - mediaPath:  미디어 파일 절대 경로
 * - addedAt:    추가 시각 (epoch ms)
 *
 * UNIQUE (playlistId, mediaPath) → 동일 항목 중복 추가 방지
 */
@Entity(
    tableName = "playlist_items",
    foreignKeys = [
        ForeignKey(
            entity = Playlist::class,
            parentColumns = ["id"],
            childColumns  = ["playlistId"],
            onDelete = ForeignKey.CASCADE   // 플레이리스트 삭제 시 아이템도 삭제
        )
    ],
    indices = [
        Index(value = ["playlistId"]),
        Index(value = ["playlistId", "mediaPath"], unique = true)  // 중복 방지
    ]
)
data class PlaylistItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val playlistId: Long,
    val mediaPath: String,
    val addedAt: Long = System.currentTimeMillis()
)
