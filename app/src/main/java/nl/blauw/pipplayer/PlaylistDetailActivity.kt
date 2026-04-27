package nl.blauw.pipplayer

import android.content.Intent
import android.os.Bundle
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
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

class PlaylistDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PLAYLIST_ID   = "playlist_id"
        const val EXTRA_PLAYLIST_NAME = "playlist_name"
    }

    private lateinit var repo: PlaylistRepository
    private lateinit var adapter: PlaylistDetailAdapter

    private var playlistId: Long   = -1L
    private var playlistName: String = ""

    // ── 뷰 ───────────────────────────────────────────────────
    private lateinit var ivHeaderBg: ImageView
    private lateinit var tvName: TextView
    private lateinit var tvMeta: TextView
    private lateinit var rvItems: RecyclerView
    private lateinit var layoutEmpty: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_playlist_detail)

        playlistId   = intent.getLongExtra(EXTRA_PLAYLIST_ID, -1L)
        playlistName = intent.getStringExtra(EXTRA_PLAYLIST_NAME) ?: ""

        if (playlistId == -1L) { finish(); return }

        repo = PlaylistRepository(this)

        bindViews()
        setupToolbar()
        setupRecyclerView()
        observeItems()
    }

    private fun bindViews() {
        ivHeaderBg   = findViewById(R.id.ivHeaderBg)
        tvName       = findViewById(R.id.tvPlaylistName)
        tvMeta       = findViewById(R.id.tvPlaylistMeta)
        rvItems      = findViewById(R.id.rvItems)
        layoutEmpty  = findViewById(R.id.layoutEmpty)

        tvName.text  = playlistName

        // 빈 상태에서 "VIDEO'S TOEVOEGEN" 버튼 클릭 → MediaPickerActivity
        findViewById<Button>(R.id.btnAddVideos).setOnClickListener {
            openMediaPicker()
        }
    }

    private fun openMediaPicker() {
        val intent = android.content.Intent(this, MediaPickerActivity::class.java).apply {
            putExtra(MediaPickerActivity.EXTRA_PLAYLIST_ID, playlistId)
        }
        startActivity(intent)
    }

    private fun setupToolbar() {
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.btnMore).setOnClickListener {
            // TODO: 플레이리스트 삭제 / 이름 변경
        }
        findViewById<Button>(R.id.btnPlayAll).setOnClickListener {
            playAll()
        }
        findViewById<ImageButton>(R.id.btnRepeat).setOnClickListener {
            Toast.makeText(this, "반복 재생", Toast.LENGTH_SHORT).show()
        }
        findViewById<ImageButton>(R.id.btnShuffle).setOnClickListener {
            Toast.makeText(this, "셔플 재생", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupRecyclerView() {
        adapter = PlaylistDetailAdapter(
            onItemClick = { item -> playItem(item) },
            onMoreClick = { item -> showItemMenu(item) }
        )
        rvItems.layoutManager = LinearLayoutManager(this)
        rvItems.adapter       = adapter
    }

    /** Room Flow 로 아이템 목록 실시간 관찰 */
    private fun observeItems() {
        lifecycleScope.launch {
            repo.getItemsByPlaylist(playlistId).collectLatest { items ->
                val isEmpty = items.isEmpty()
                layoutEmpty.visibility = if (isEmpty) View.VISIBLE else View.GONE
                rvItems.visibility     = if (isEmpty) View.GONE    else View.VISIBLE

                // 메타 정보 업데이트
                tvMeta.text = "${items.size}개의 미디어"

                adapter.submitList(items)

                // 헤더 배경: 첫 번째 아이템 썸네일을 블러 처리
                val firstPath = items.firstOrNull()?.mediaPath
                updateHeaderBg(firstPath)
            }
        }
    }

    /** 헤더 배경 이미지 — 첫 번째 아이템 썸네일 적용 */
    private fun updateHeaderBg(mediaPath: String?) {
        if (mediaPath == null) {
            ivHeaderBg.setImageResource(R.drawable.ic_folder_default)
            return
        }
        val file    = File(mediaPath)
        val isVideo = mediaPath.substringAfterLast('.', "").lowercase() in
                setOf("mp4","mkv","avi","mov","wmv","flv","webm","3gp","m4v","ts")

        if (isVideo) {
            Glide.with(this)
                .asBitmap()
                .load(file)
                .apply(RequestOptions()
                    .frame(1_000_000L)
                    .transform(CenterCrop())
                    .diskCacheStrategy(DiskCacheStrategy.RESOURCE))
                .into(ivHeaderBg)
        } else {
            Glide.with(this)
                .load(file)
                .apply(RequestOptions()
                    .transform(CenterCrop())
                    .diskCacheStrategy(DiskCacheStrategy.RESOURCE))
                .into(ivHeaderBg)
        }
    }

    // ── 재생 ─────────────────────────────────────────────────
    private fun playAll() {
        val first = adapter.currentList.firstOrNull() ?: return
        playItem(first)
    }

    private fun playItem(item: PlaylistItem) {
        val intent = Intent(this, PlayerService::class.java).apply {
            putExtra(PlayerService.COMMAND, PlayerService.ACTION_START_PIP)
            putExtra("data", item.mediaPath)
        }
        startForegroundService(intent)
    }

    private fun showItemMenu(item: PlaylistItem) {
        val file    = File(item.mediaPath)
        val isVideo = item.mediaPath.substringAfterLast('.', "").lowercase() in
                setOf("mp4","mkv","avi","mov","wmv","flv","webm","3gp","m4v","ts")

        val dialog    = BottomSheetDialog(this)
        val sheetView = LayoutInflater.from(this)
            .inflate(R.layout.bottom_sheet_playlist_item_menu, null)

        // ── 상단 썸네일 ────────────────────────────────────────
        val ivThumb   = sheetView.findViewById<ImageView>(R.id.ivMenuThumb)
        val tvDuration = sheetView.findViewById<TextView>(R.id.tvMenuDuration)
        val tvFileName = sheetView.findViewById<TextView>(R.id.tvMenuFileName)
        val tvFileMeta = sheetView.findViewById<TextView>(R.id.tvMenuFileMeta)

        tvFileName.text = file.name
        tvFileMeta.text = Formatter.formatShortFileSize(this, file.length())
        tvDuration.visibility = if (isVideo) View.VISIBLE else View.GONE

        if (isVideo) {
            Glide.with(this)
                .asBitmap()
                .load(file)
                .apply(RequestOptions()
                    .frame(1_000_000L)
                    .transform(CenterCrop())
                    .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                    .placeholder(android.R.drawable.ic_media_play))
                .into(ivThumb)
        } else {
            Glide.with(this)
                .load(file)
                .apply(RequestOptions()
                    .transform(CenterCrop())
                    .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                    .placeholder(android.R.drawable.ic_menu_gallery))
                .into(ivThumb)
        }

        // ── Play ──────────────────────────────────────────────
        sheetView.findViewById<View>(R.id.menuPlay).setOnClickListener {
            dialog.dismiss()
            playItem(item)
        }

        // ── Remove (플레이리스트에서 제거) ────────────────────
        sheetView.findViewById<View>(R.id.menuRemove).setOnClickListener {
            dialog.dismiss()
            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    repo.removeItemFromPlaylist(item.id)
                }
                Toast.makeText(this@PlaylistDetailActivity,
                    "목록에서 제거되었습니다.", Toast.LENGTH_SHORT).show()
            }
        }

        // ── 속성 ──────────────────────────────────────────────
        sheetView.findViewById<View>(R.id.menuProperties).setOnClickListener {
            dialog.dismiss()
            showProperties(file)
        }

        dialog.setContentView(sheetView)
        dialog.show()
    }

    /** 파일 속성 다이얼로그 */
    private fun showProperties(file: File) {
        val sizeStr     = Formatter.formatShortFileSize(this, file.length())
        val dateStr     = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date(file.lastModified()))
        val message     = """
            이름: ${file.name}
            크기: $sizeStr
            경로: ${file.parent}
            수정일: $dateStr
        """.trimIndent()

        android.app.AlertDialog.Builder(this)
            .setTitle("Eigenschappen")
            .setMessage(message)
            .setPositiveButton("확인", null)
            .show()
    }

    // ── Adapter ───────────────────────────────────────────────
    inner class PlaylistDetailAdapter(
        private val onItemClick: (PlaylistItem) -> Unit,
        private val onMoreClick: (PlaylistItem) -> Unit
    ) : ListAdapter<PlaylistItem, PlaylistDetailAdapter.VH>(DIFF) {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val ivThumb   : ImageView   = view.findViewById(R.id.ivThumb)
            val tvDuration: TextView    = view.findViewById(R.id.tvDuration)
            val tvTitle   : TextView    = view.findViewById(R.id.tvTitle)
            val tvMeta    : TextView    = view.findViewById(R.id.tvMeta)
            val btnMore   : ImageButton = view.findViewById(R.id.btnMore)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_playlist_detail, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item  = getItem(position)
            val file  = File(item.mediaPath)
            val isVideo = item.mediaPath.substringAfterLast('.', "").lowercase() in
                    setOf("mp4","mkv","avi","mov","wmv","flv","webm","3gp","m4v","ts")

            holder.tvTitle.text   = file.name
            holder.tvMeta.text    = Formatter.formatShortFileSize(holder.itemView.context, file.length())
            holder.tvDuration.visibility = if (isVideo) View.VISIBLE else View.GONE

            // 썸네일
            if (isVideo) {
                Glide.with(holder.ivThumb)
                    .asBitmap().load(file)
                    .apply(RequestOptions().frame(1_000_000L).transform(CenterCrop())
                        .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                        .placeholder(android.R.drawable.ic_media_play))
                    .into(holder.ivThumb)
            } else {
                Glide.with(holder.ivThumb)
                    .load(file)
                    .apply(RequestOptions().transform(CenterCrop())
                        .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                        .placeholder(android.R.drawable.ic_menu_gallery))
                    .into(holder.ivThumb)
            }

            holder.itemView.setOnClickListener { onItemClick(item) }
            holder.btnMore.setOnClickListener  { onMoreClick(item) }
        }

        override fun onViewRecycled(holder: VH) {
            super.onViewRecycled(holder)
            Glide.with(holder.ivThumb.context).clear(holder.ivThumb)
        }
    }

    private val DIFF = object : DiffUtil.ItemCallback<PlaylistItem>() {
        override fun areItemsTheSame(a: PlaylistItem, b: PlaylistItem) = a.id == b.id
        override fun areContentsTheSame(a: PlaylistItem, b: PlaylistItem) = a == b
    }
}