package nl.blauw.pipplayer

import android.content.Context
import kotlinx.coroutines.flow.Flow

/**
 * PlaylistRepository — DB 접근 계층
 *
 * ViewModel 또는 BottomSheet 에서 직접 DAO 를 호출하지 않고
 * 이 Repository 를 통해 접근한다.
 *
 * 반환 값 설명
 * ─────────────────────────────────────────────────────────
 * AddResult.ADDED     → 정상 추가
 * AddResult.DUPLICATE → 이미 해당 플레이리스트에 존재
 * AddResult.ERROR     → 기타 오류 (DB 삽입 실패 등)
 */
class PlaylistRepository(context: Context) {

    private val dao = AppDatabase.getInstance(context).playlistDao()

    // ── 플레이리스트 ──────────────────────────────────────────

    /** 플레이리스트 이름 변경. */
    suspend fun renamePlaylist(id: Long, newName: String) = dao.renamePlaylist(id, newName)

    /** 플레이리스트 삭제 (아이템 CASCADE 삭제). */
    suspend fun deletePlaylist(id: Long) = dao.deletePlaylist(id)

    /** 전체 플레이리스트 목록 Flow (실시간 관찰). */
    fun getAllPlaylists(): Flow<List<Playlist>> = dao.getAllPlaylists()

    /** 전체 플레이리스트 1회 조회 (suspend). */
    suspend fun getAllPlaylistsOnce(): List<Playlist> = dao.getAllPlaylistsOnce()

    /**
     * 신규 플레이리스트 생성.
     * @return 생성된 플레이리스트 id, 이름 중복 시 -1L
     */
    suspend fun createPlaylist(name: String): Long {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return -1L
        return dao.insertPlaylist(Playlist(name = trimmed))
    }

    /**
     * 이름 중복 여부 확인.
     * @return true = 이미 존재
     */
    suspend fun isNameDuplicate(name: String): Boolean =
        dao.getPlaylistByName(name.trim()) != null

    // ── 플레이리스트 아이템 ───────────────────────────────────────

    /**
     * 미디어를 플레이리스트에 추가.
     * @return AddResult
     */
    suspend fun addMediaToPlaylist(playlistId: Long, mediaPath: String): AddResult {
        val already = dao.existsItem(playlistId, mediaPath) > 0
        if (already) return AddResult.DUPLICATE

        val inserted = dao.insertItem(
            PlaylistItem(playlistId = playlistId, mediaPath = mediaPath)
        )
        return if (inserted > 0L) AddResult.ADDED else AddResult.ERROR
    }

    /** 플레이리스트에서 아이템 제거. */
    suspend fun removeItemFromPlaylist(itemId: Long) = dao.deleteItem(itemId)

    /** 특정 플레이리스트의 아이템 목록 Flow (실시간 관찰). */
    fun getItemsByPlaylist(playlistId: Long): kotlinx.coroutines.flow.Flow<List<PlaylistItem>> =
        dao.getItemsByPlaylist(playlistId)

    /** 특정 플레이리스트의 첫 번째 미디어 경로 (썸네일용). */
    suspend fun getFirstMediaPath(playlistId: Long): String? =
        dao.getFirstMediaPath(playlistId)

    /** 특정 플레이리스트의 아이템 수. */
    suspend fun getItemCount(playlistId: Long): Int = dao.getItemCount(playlistId)

    // ── 결과 열거형 ───────────────────────────────────────────────
    enum class AddResult { ADDED, DUPLICATE, ERROR }
}
