package nl.blauw.pipplayer

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.blauw.pipplayer.Playlist
import java.io.File

/**
 * PlaylistBottomSheet
 * ─────────────────────────────────────────────────────────────
 * 역할: 플레이리스트 선택 + 신규 생성을 단일 BottomSheetDialogFragment 로 처리
 *
 * 사용법:
 *   PlaylistBottomSheet.newInstance(mediaPath).show(supportFragmentManager, TAG)
 *
 * 동작 흐름:
 *   1) viewSelectPlaylist → 기존 플레이리스트 목록 표시
 *   2) "Nieuwe playlist aanmaken" 클릭 → viewNewPlaylist 로 전환
 *   3) 이름 입력 후 "Aanmaken" 클릭 → DB 저장 → 미디어 추가 → Toast
 *   4) 기존 플레이리스트 행 클릭 → 미디어 추가 → Toast (중복 시 별도 메시지)
 */
class PlaylistBottomSheet : BottomSheetDialogFragment() {

    // ── 인자 키 ────────────────────────────────────────────────
    companion object {
        const val TAG = "PlaylistBottomSheet"
        private const val ARG_MEDIA_PATH = "media_path"

        fun newInstance(mediaPath: String): PlaylistBottomSheet =
            PlaylistBottomSheet().apply {
                arguments = Bundle().apply { putString(ARG_MEDIA_PATH, mediaPath) }
            }
    }

    // ── 상태 ───────────────────────────────────────────────────
    private val mediaPath: String by lazy {
        requireArguments().getString(ARG_MEDIA_PATH, "")
    }
    private lateinit var repo: PlaylistRepository

    // ── 플레이리스트 선택 화면 뷰 ─────────────────────────────
    private lateinit var viewSelectPlaylist: View
    private lateinit var tvTitle: TextView
    private lateinit var btnNewPlaylist: View
    private lateinit var rvPlaylists: RecyclerView
    private lateinit var tvPlaylistEmpty: TextView
    private lateinit var playlistAdapter: PlaylistRowAdapter

    // ── 신규 생성 화면 뷰 ─────────────────────────────────────
    private lateinit var viewNewPlaylist: View
    private lateinit var etName: EditText
    private lateinit var btnCreate: Button

    // ── 현재 화면 상태 ─────────────────────────────────────────
    private var showingNewPlaylist = false

    // ──────────────────────────────────────────────────────────
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // ── 루트: 두 화면을 FrameLayout 으로 겹쳐서 전환 ────────
        val root = inflater.inflate(R.layout.bottom_sheet_playlist_container, container, false)

        // 플레이리스트 선택 화면
        viewSelectPlaylist = root.findViewById(R.id.viewSelectPlaylist)
        tvTitle            = root.findViewById(R.id.tvPlaylistSheetTitle)
        btnNewPlaylist     = root.findViewById(R.id.btnNewPlaylist)
        rvPlaylists        = root.findViewById(R.id.rvPlaylists)
        tvPlaylistEmpty    = root.findViewById(R.id.tvPlaylistEmpty)

        // 신규 생성 화면
        viewNewPlaylist = root.findViewById(R.id.viewNewPlaylist)
        etName          = root.findViewById(R.id.etPlaylistName)
        btnCreate       = root.findViewById(R.id.btnCreatePlaylist)

        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        repo = PlaylistRepository(requireContext())

        setupSelectScreen()
        setupNewPlaylistScreen()

        // 소프트 키보드가 올라와도 Bottom Sheet 가 올라오도록
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }

    // ── 플레이리스트 선택 화면 초기화 ─────────────────────────
    private fun setupSelectScreen() {
        // 타이틀: 파일명 표시
        val fileName = File(mediaPath).name
        tvTitle.text = "\"${fileName}\" 을(를) 플레이리스트에 추가"

        // RecyclerView
        playlistAdapter = PlaylistRowAdapter { playlist ->
            addMediaToPlaylist(playlist)
        }
        rvPlaylists.layoutManager = LinearLayoutManager(requireContext())
        rvPlaylists.adapter       = playlistAdapter

        // 신규 생성 버튼
        btnNewPlaylist.setOnClickListener { showNewPlaylistScreen() }

        // 플레이리스트 목록 로드
        loadPlaylists()
    }

    /** DB 에서 플레이리스트 목록을 1회 조회하여 RecyclerView 에 표시 */
    private fun loadPlaylists() {
        lifecycleScope.launch {
            val playlists = withContext(Dispatchers.IO) {
                repo.getAllPlaylistsOnce()
            }
            if (playlists.isEmpty()) {
                rvPlaylists.visibility   = View.GONE
                tvPlaylistEmpty.visibility = View.VISIBLE
            } else {
                rvPlaylists.visibility   = View.VISIBLE
                tvPlaylistEmpty.visibility = View.GONE
                playlistAdapter.submitList(playlists)
            }
        }
    }

    // ── 신규 생성 화면 초기화 ─────────────────────────────────
    private fun setupNewPlaylistScreen() {
        // 뒤로가기 처리 — 신규 생성 화면에서 선택 화면으로 복귀
        // (BackPressedCallback 은 Fragment 에서 isCancelable 로 처리)

        // 입력 변화 감지 → 버튼 활성/비활성
        etName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                btnCreate.isEnabled = s?.toString()?.trim()?.isNotEmpty() == true
            }
        })

        // IME "완료" 키 → 생성 실행
        etName.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE && btnCreate.isEnabled) {
                createAndAdd()
                true
            } else false
        }

        // 생성 버튼
        btnCreate.setOnClickListener { createAndAdd() }
    }

    /** "Nieuwe playlist aanmaken" → 신규 생성 화면으로 전환 */
    private fun showNewPlaylistScreen() {
        showingNewPlaylist = true
        viewSelectPlaylist.visibility = View.GONE
        viewNewPlaylist.visibility    = View.VISIBLE
        // 키보드 자동 포커스
        etName.requestFocus()
    }

    /** 신규 생성 화면 → 다시 선택 화면으로 돌아가기 */
    fun showSelectScreen() {
        showingNewPlaylist = false
        viewNewPlaylist.visibility    = View.GONE
        viewSelectPlaylist.visibility = View.VISIBLE
        etName.text?.clear()
    }

    /** 외부에서 뒤로가기 처리를 위해 현재 화면 상태 노출 */
    fun isShowingNewPlaylist() = showingNewPlaylist

    // ── 핵심 로직 ─────────────────────────────────────────────

    /**
     * 신규 플레이리스트 생성 후 미디어 추가
     * 1) 이름 중복 검사
     * 2) DB 에 Playlist 삽입
     * 3) PlaylistItem 삽입
     * 4) Toast 표시 후 dismiss
     */
    private fun createAndAdd() {
        val name = etName.text?.toString()?.trim() ?: ""
        if (name.isEmpty()) {
            Toast.makeText(requireContext(), "플레이리스트 이름을 입력해주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            // 중복 검사
            val isDuplicate = withContext(Dispatchers.IO) { repo.isNameDuplicate(name) }
            if (isDuplicate) {
                Toast.makeText(requireContext(), "이미 존재하는 플레이리스트 이름입니다.", Toast.LENGTH_SHORT).show()
                return@launch
            }

            // 플레이리스트 생성
            val newId = withContext(Dispatchers.IO) { repo.createPlaylist(name) }
            if (newId <= 0L) {
                Toast.makeText(requireContext(), "플레이리스트 생성에 실패했습니다.", Toast.LENGTH_SHORT).show()
                return@launch
            }

            // 미디어 추가
            val result = withContext(Dispatchers.IO) {
                repo.addMediaToPlaylist(newId, mediaPath)
            }

            val msg = when (result) {
                PlaylistRepository.AddResult.ADDED     -> "\"$name\" 플레이리스트에 추가되었습니다. ✓"
                PlaylistRepository.AddResult.DUPLICATE -> "이미 해당 플레이리스트에 추가된 항목입니다."
                PlaylistRepository.AddResult.ERROR     -> "추가 중 오류가 발생했습니다."
            }
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
            dismiss()
        }
    }

    /**
     * 기존 플레이리스트에 미디어 추가
     * 1) PlaylistItem 삽입 시도
     * 2) ADDED / DUPLICATE / ERROR 에 따라 Toast
     * 3) dismiss
     */
    private fun addMediaToPlaylist(playlist: Playlist) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                repo.addMediaToPlaylist(playlist.id, mediaPath)
            }
            val msg = when (result) {
                PlaylistRepository.AddResult.ADDED ->
                    "\"${playlist.name}\" 플레이리스트에 추가되었습니다. ✓"
                PlaylistRepository.AddResult.DUPLICATE ->
                    "\"${playlist.name}\" 에 이미 추가된 항목입니다."
                PlaylistRepository.AddResult.ERROR ->
                    "추가 중 오류가 발생했습니다."
            }
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
            dismiss()
        }
    }

    // ──────────────────────────────────────────────────────────
    // 내부 RecyclerView Adapter — 플레이리스트 행
    // ──────────────────────────────────────────────────────────

    inner class PlaylistRowAdapter(
        private val onItemClick: (Playlist) -> Unit
    ) : ListAdapter<Playlist, PlaylistRowAdapter.VH>(PLAYLIST_DIFF) {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val ivThumb : ImageView = view.findViewById(R.id.ivPlaylistThumb)
            val tvName  : TextView  = view.findViewById(R.id.tvPlaylistName)
            val tvSub   : TextView  = view.findViewById(R.id.tvPlaylistSubtext)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_playlist_row, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val playlist = getItem(position)
            holder.tvName.text = playlist.name

            // 아이템 수 비동기 조회
            lifecycleScope.launch {
                val count = withContext(Dispatchers.IO) { repo.getItemCount(playlist.id) }
                holder.tvSub.text = "${count}개의 미디어"
            }

            // 썸네일: 기본 아이콘 (첫 번째 아이템 썸네일 로딩은 추후 확장)
            Glide.with(holder.ivThumb)
                .load(android.R.drawable.ic_menu_gallery)
                .into(holder.ivThumb)

            holder.itemView.setOnClickListener { onItemClick(playlist) }
        }
    }

    private val PLAYLIST_DIFF = object : DiffUtil.ItemCallback<Playlist>() {
        override fun areItemsTheSame(a: Playlist, b: Playlist) = a.id == b.id
        override fun areContentsTheSame(a: Playlist, b: Playlist) = a == b
    }
}
