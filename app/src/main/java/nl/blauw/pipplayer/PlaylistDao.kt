package nl.blauw.pipplayer

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {

    // ── 플레이리스트 CRUD ──────────────────────────────────────────

    /** 신규 플레이리스트 생성. 동일 이름 있으면 IGNORE (중복 방지). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPlaylist(playlist: Playlist): Long

    /** 전체 플레이리스트 목록 (생성일 내림차순). Flow 로 실시간 관찰. */
    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun getAllPlaylists(): Flow<List<Playlist>>

    /** 플레이리스트 목록 1회 조회 (suspend). */
    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    suspend fun getAllPlaylistsOnce(): List<Playlist>

    /** 이름으로 플레이리스트 조회 (중복 검사용). */
    @Query("SELECT * FROM playlists WHERE name = :name LIMIT 1")
    suspend fun getPlaylistByName(name: String): Playlist?

    // ── 플레이리스트 아이템 CRUD ───────────────────────────────────

    /**
     * 아이템 추가.
     * IGNORE → (playlistId, mediaPath) UNIQUE 위반 시 -1L 반환 (중복).
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertItem(item: PlaylistItem): Long

    /** 특정 아이템 삭제 (플레이리스트에서 제거). */
    @Query("DELETE FROM playlist_items WHERE id = :itemId")
    suspend fun deleteItem(itemId: Long)

    /** 특정 플레이리스트의 첫 번째 미디어 경로 (썸네일용). */
    @Query("SELECT mediaPath FROM playlist_items WHERE playlistId = :playlistId ORDER BY addedAt ASC LIMIT 1")
    suspend fun getFirstMediaPath(playlistId: Long): String?

    /** 특정 플레이리스트의 아이템 수 조회. */
    @Query("SELECT COUNT(*) FROM playlist_items WHERE playlistId = :playlistId")
    suspend fun getItemCount(playlistId: Long): Int

    /** 특정 플레이리스트에 해당 미디어가 이미 있는지 확인. */
    @Query("""
        SELECT COUNT(*) FROM playlist_items 
        WHERE playlistId = :playlistId AND mediaPath = :mediaPath
    """)
    suspend fun existsItem(playlistId: Long, mediaPath: String): Int

    /** 특정 플레이리스트의 아이템 목록 (추가일 순). */
    @Query("SELECT * FROM playlist_items WHERE playlistId = :playlistId ORDER BY addedAt ASC")
    fun getItemsByPlaylist(playlistId: Long): Flow<List<PlaylistItem>>
}
