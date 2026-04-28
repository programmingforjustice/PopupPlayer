package nl.blauw.pipplayer

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.request.RequestOptions
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class PlaylistActivity : AppCompatActivity() {

    private lateinit var repo: PlaylistRepository
    private lateinit var adapter: PlaylistListAdapter
    private lateinit var rvPlaylists: RecyclerView
    private lateinit var layoutEmpty: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_playlist)

        repo         = PlaylistRepository(this)
        rvPlaylists  = findViewById(R.id.rvPlaylists)
        layoutEmpty  = findViewById(R.id.layoutEmpty)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        // 신규 플레이리스트 생성 버튼 → BottomSheet 표시
        findViewById<LinearLayout>(R.id.btnNewPlaylist).setOnClickListener {
            showNewPlaylistDialog()
        }

        setupRecyclerView()
        observePlaylists()
    }

    private fun setupRecyclerView() {
        adapter = PlaylistListAdapter(
            onItemClick  = { playlist -> openDetail(playlist) },
            onMoreClick  = { playlist -> showPlaylistMenu(playlist) },
            repo         = repo
        )
        rvPlaylists.layoutManager = LinearLayoutManager(this)
        rvPlaylists.adapter       = adapter
    }

    /** Room Flow 로 실시간 관찰 → 플레이리스트 추가/삭제 즉시 반영 */
    private fun observePlaylists() {
        lifecycleScope.launch {
            repo.getAllPlaylists().collectLatest { list ->
                val isEmpty = list.isEmpty()
                layoutEmpty.visibility = if (isEmpty) View.VISIBLE else View.GONE
                rvPlaylists.visibility = if (isEmpty) View.GONE    else View.VISIBLE
                adapter.submitList(list)
            }
        }
    }

    private fun openDetail(playlist: Playlist) {
        val intent = Intent(this, PlaylistDetailActivity::class.java).apply {
            putExtra(PlaylistDetailActivity.EXTRA_PLAYLIST_ID,   playlist.id)
            putExtra(PlaylistDetailActivity.EXTRA_PLAYLIST_NAME, playlist.name)
        }
        startActivity(intent)
    }

    private fun showNewPlaylistDialog() {
        // 빈 mediaPath 로 호출 → 생성만 하고 미디어 추가는 생략
        NewPlaylistDialog.show(supportFragmentManager)
    }

    private fun showPlaylistMenu(playlist: Playlist) {
        val dialog    = BottomSheetDialog(this)
        val sheetView = LayoutInflater.from(this)
            .inflate(R.layout.bottom_sheet_playlist_menu, null)

        // ── 상단 썸네일 + 플레이리스트명 ─────────────────────
        val ivThumb  = sheetView.findViewById<ImageView>(R.id.ivMenuThumb)
        val tvName   = sheetView.findViewById<TextView>(R.id.tvMenuPlaylistName)
        val tvMeta   = sheetView.findViewById<TextView>(R.id.tvMenuPlaylistMeta)

        tvName.text = playlist.name
        lifecycleScope.launch {
            val count     = withContext(Dispatchers.IO) { repo.getItemCount(playlist.id) }
            val thumbPath = withContext(Dispatchers.IO) { repo.getFirstMediaPath(playlist.id) }
            tvMeta.text = "${count}개의 미디어"
            if (thumbPath != null) {
                val file    = File(thumbPath)
                val isVideo = thumbPath.substringAfterLast('.', "").lowercase() in
                        setOf("mp4","mkv","avi","mov","wmv","flv","webm","3gp","m4v","ts")
                if (isVideo) {
                    Glide.with(ivThumb).asBitmap().load(file)
                        .apply(RequestOptions().frame(1_000_000L).transform(CenterCrop())
                            .diskCacheStrategy(DiskCacheStrategy.RESOURCE))
                        .into(ivThumb)
                } else {
                    Glide.with(ivThumb).load(file)
                        .apply(RequestOptions().transform(CenterCrop())
                            .diskCacheStrategy(DiskCacheStrategy.RESOURCE))
                        .into(ivThumb)
                }
            }
        }

        // ── Play: 첫 번째 아이템 재생 ─────────────────────────
        sheetView.findViewById<View>(R.id.menuPlay).setOnClickListener {
            dialog.dismiss()
            lifecycleScope.launch {
                val firstPath = withContext(Dispatchers.IO) { repo.getFirstMediaPath(playlist.id) }
                if (firstPath != null) {
                    val intent = Intent(this@PlaylistActivity, PlayerService::class.java).apply {
                        putExtra(PlayerService.COMMAND, PlayerService.ACTION_START_PIP)
                        putExtra("data", firstPath)
                    }
                    startForegroundService(intent)
                } else {
                    Toast.makeText(this@PlaylistActivity, "재생할 미디어가 없습니다.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // ── Rename: 이름 변경 다이얼로그 ─────────────────────
        sheetView.findViewById<View>(R.id.menuRename).setOnClickListener {
            dialog.dismiss()
            showRenameDialog(playlist)
        }

        // ── Remove: 플레이리스트 삭제 확인 ───────────────────
        sheetView.findViewById<View>(R.id.menuRemove).setOnClickListener {
            dialog.dismiss()
            AlertDialog.Builder(this)
                .setTitle("플레이리스트 삭제")
                .setMessage("\"${playlist.name}\" 를 삭제하시겠습니까?")
                .setPositiveButton("삭제") { _, _ ->
                    lifecycleScope.launch {
                        withContext(Dispatchers.IO) { repo.deletePlaylist(playlist.id) }
                        Toast.makeText(
                            this@PlaylistActivity,
                            "\"${playlist.name}\" 이(가) 삭제되었습니다.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                .setNegativeButton("취소", null)
                .show()
        }

        dialog.setContentView(sheetView)
        dialog.show()
    }

    /** 이름 변경 AlertDialog */
    private fun showRenameDialog(playlist: Playlist) {
        val etInput = EditText(this).apply {
            setText(playlist.name)
            selectAll()
            setPadding(48, 24, 48, 24)
        }

        AlertDialog.Builder(this)
            .setTitle("이름 변경")
            .setView(etInput)
            .setPositiveButton("확인") { _, _ ->
                val newName = etInput.text?.toString()?.trim() ?: ""
                if (newName.isEmpty()) {
                    Toast.makeText(this, "이름을 입력해주세요.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                lifecycleScope.launch {
                    val isDuplicate = withContext(Dispatchers.IO) { repo.isNameDuplicate(newName) }
                    if (isDuplicate && newName != playlist.name) {
                        Toast.makeText(this@PlaylistActivity, "이미 존재하는 이름입니다.", Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                    withContext(Dispatchers.IO) { repo.renamePlaylist(playlist.id, newName) }
                    Toast.makeText(this@PlaylistActivity, "이름이 변경되었습니다.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    // ── Adapter ───────────────────────────────────────────────
    class PlaylistListAdapter(
        private val onItemClick: (Playlist) -> Unit,
        private val onMoreClick: (Playlist) -> Unit,
        private val repo: PlaylistRepository
    ) : ListAdapter<Playlist, PlaylistListAdapter.VH>(DIFF) {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val ivThumb  : ImageView  = view.findViewById(R.id.ivThumb)
            val tvName   : TextView   = view.findViewById(R.id.tvName)
            val tvSubtext: TextView   = view.findViewById(R.id.tvSubtext)
            val btnMore  : ImageButton = view.findViewById(R.id.btnMore)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_playlist, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val playlist = getItem(position)
            holder.tvName.text = playlist.name

            // 아이템 수 비동기 조회
            val scope = (holder.itemView.context as? AppCompatActivity)?.lifecycleScope ?: return
            scope.launch {
                val count = withContext(Dispatchers.IO) { repo.getItemCount(playlist.id) }
                val first = withContext(Dispatchers.IO) { repo.getFirstMediaPath(playlist.id) }
                holder.tvSubtext.text = "${count}개의 미디어"

                // 썸네일: 첫 번째 미디어 파일
                if (first != null) {
                    val file = File(first)
                    val isVideo = first.substringAfterLast('.', "").lowercase() in
                            setOf("mp4","mkv","avi","mov","wmv","flv","webm","3gp","m4v","ts")
                    if (isVideo) {
                        Glide.with(holder.ivThumb)
                            .asBitmap()
                            .load(file)
                            .apply(RequestOptions().frame(1_000_000L).transform(CenterCrop())
                                .diskCacheStrategy(DiskCacheStrategy.RESOURCE))
                            .into(holder.ivThumb)
                    } else {
                        Glide.with(holder.ivThumb)
                            .load(file)
                            .apply(RequestOptions().transform(CenterCrop())
                                .diskCacheStrategy(DiskCacheStrategy.RESOURCE))
                            .into(holder.ivThumb)
                    }
                } else {
                    Glide.with(holder.ivThumb).clear(holder.ivThumb)
                    holder.ivThumb.setImageResource(R.drawable.ic_folder_default)
                }
            }

            holder.itemView.setOnClickListener { onItemClick(playlist) }
            holder.btnMore.setOnClickListener  { onMoreClick(playlist) }
        }

        override fun onViewRecycled(holder: VH) {
            super.onViewRecycled(holder)
            Glide.with(holder.ivThumb.context).clear(holder.ivThumb)
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Playlist>() {
            override fun areItemsTheSame(a: Playlist, b: Playlist) = a.id == b.id
            override fun areContentsTheSame(a: Playlist, b: Playlist) = a == b
        }
    }
}