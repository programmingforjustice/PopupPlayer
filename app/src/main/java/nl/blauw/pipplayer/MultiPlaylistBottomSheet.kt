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
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 멀티셀렉트에서 여러 미디어를 한 번에 플레이리스트에 추가.
 * PlaylistBottomSheet 와 동일한 레이아웃 사용, mediaPaths 만 List 로 확장.
 */
class MultiPlaylistBottomSheet : BottomSheetDialogFragment() {

    companion object {
        const val TAG = "MultiPlaylistBottomSheet"
        private const val ARG_MEDIA_PATHS = "media_paths"

        fun newInstance(paths: List<String>): MultiPlaylistBottomSheet =
            MultiPlaylistBottomSheet().apply {
                arguments = Bundle().apply {
                    putStringArrayList(ARG_MEDIA_PATHS, ArrayList(paths))
                }
            }

        fun show(fm: FragmentManager, paths: List<String>) {
            if (fm.findFragmentByTag(TAG) == null) {
                newInstance(paths).show(fm, TAG)
            }
        }
    }

    private val mediaPaths: List<String> by lazy {
        requireArguments().getStringArrayList(ARG_MEDIA_PATHS) ?: emptyList()
    }

    private lateinit var repo: PlaylistRepository

    private lateinit var viewSelectPlaylist: View
    private lateinit var tvTitle: TextView
    private lateinit var btnNewPlaylist: View
    private lateinit var rvPlaylists: RecyclerView
    private lateinit var tvPlaylistEmpty: TextView
    private lateinit var playlistAdapter: PlaylistRowAdapter

    private lateinit var viewNewPlaylist: View
    private lateinit var etName: EditText
    private lateinit var btnCreate: Button

    private var showingNewPlaylist = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // PlaylistBottomSheet 와 동일한 컨테이너 레이아웃 재사용
        val root = inflater.inflate(R.layout.bottom_sheet_playlist_container, container, false)

        viewSelectPlaylist = root.findViewById(R.id.viewSelectPlaylist)
        tvTitle            = root.findViewById(R.id.tvPlaylistSheetTitle)
        btnNewPlaylist     = root.findViewById(R.id.btnNewPlaylist)
        rvPlaylists        = root.findViewById(R.id.rvPlaylists)
        tvPlaylistEmpty    = root.findViewById(R.id.tvPlaylistEmpty)

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
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }

    private fun setupSelectScreen() {
        tvTitle.text = "${mediaPaths.size}개 항목을 플레이리스트에 추가"

        playlistAdapter = PlaylistRowAdapter { playlist -> addAllToPlaylist(playlist) }
        rvPlaylists.layoutManager = LinearLayoutManager(requireContext())
        rvPlaylists.adapter       = playlistAdapter
        btnNewPlaylist.setOnClickListener { showNewPlaylistScreen() }
        loadPlaylists()
    }

    private fun loadPlaylists() {
        lifecycleScope.launch {
            val playlists = withContext(Dispatchers.IO) { repo.getAllPlaylistsOnce() }
            if (playlists.isEmpty()) {
                rvPlaylists.visibility     = View.GONE
                tvPlaylistEmpty.visibility = View.VISIBLE
            } else {
                rvPlaylists.visibility     = View.VISIBLE
                tvPlaylistEmpty.visibility = View.GONE
                playlistAdapter.submitList(playlists)
            }
        }
    }

    private fun setupNewPlaylistScreen() {
        etName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                btnCreate.isEnabled = s?.toString()?.trim()?.isNotEmpty() == true
            }
        })
        etName.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE && btnCreate.isEnabled) {
                createAndAddAll(); true
            } else false
        }
        btnCreate.setOnClickListener { createAndAddAll() }
    }

    private fun showNewPlaylistScreen() {
        showingNewPlaylist            = true
        viewSelectPlaylist.visibility = View.GONE
        viewNewPlaylist.visibility    = View.VISIBLE
        etName.requestFocus()
    }

    // ── 핵심: 신규 플레이리스트 생성 후 전체 추가 ────────────
    private fun createAndAddAll() {
        val name = etName.text?.toString()?.trim() ?: ""
        if (name.isEmpty()) {
            Toast.makeText(requireContext(), "플레이리스트 이름을 입력해주세요.", Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            val isDuplicate = withContext(Dispatchers.IO) { repo.isNameDuplicate(name) }
            if (isDuplicate) {
                Toast.makeText(requireContext(), "이미 존재하는 플레이리스트 이름입니다.", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val newId = withContext(Dispatchers.IO) { repo.createPlaylist(name) }
            if (newId <= 0L) {
                Toast.makeText(requireContext(), "플레이리스트 생성에 실패했습니다.", Toast.LENGTH_SHORT).show()
                return@launch
            }
            addAllToPlaylistById(newId, name)
        }
    }

    // ── 핵심: 기존 플레이리스트에 전체 추가 ──────────────────
    private fun addAllToPlaylist(playlist: Playlist) {
        addAllToPlaylistById(playlist.id, playlist.name)
    }

    private fun addAllToPlaylistById(playlistId: Long, playlistName: String) {
        lifecycleScope.launch {
            var addedCount = 0
            var dupCount   = 0
            withContext(Dispatchers.IO) {
                mediaPaths.forEach { path ->
                    when (repo.addMediaToPlaylist(playlistId, path)) {
                        PlaylistRepository.AddResult.ADDED     -> addedCount++
                        PlaylistRepository.AddResult.DUPLICATE -> dupCount++
                        PlaylistRepository.AddResult.ERROR     -> Unit
                    }
                }
            }
            val msg = buildString {
                append("\"$playlistName\" 에 ${addedCount}개 추가되었습니다.")
                if (dupCount > 0) append(" (${dupCount}개 중복 제외)")
            }
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
            dismiss()
        }
    }

    // ── 플레이리스트 행 Adapter ───────────────────────────────
    inner class PlaylistRowAdapter(
        private val onItemClick: (Playlist) -> Unit
    ) : ListAdapter<Playlist, PlaylistRowAdapter.VH>(DIFF) {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView = view.findViewById(R.id.tvPlaylistName)
            val tvSub:  TextView = view.findViewById(R.id.tvPlaylistSubtext)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_playlist_row, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val playlist = getItem(position)
            holder.tvName.text = playlist.name
            lifecycleScope.launch {
                val count = withContext(Dispatchers.IO) { repo.getItemCount(playlist.id) }
                holder.tvSub.text = "${count}개의 미디어"
            }
            holder.itemView.setOnClickListener { onItemClick(playlist) }
        }
    }

    private val DIFF = object : DiffUtil.ItemCallback<Playlist>() {
        override fun areItemsTheSame(a: Playlist, b: Playlist) = a.id == b.id
        override fun areContentsTheSame(a: Playlist, b: Playlist) = a == b
    }
}