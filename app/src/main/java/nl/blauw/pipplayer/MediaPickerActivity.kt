package nl.blauw.pipplayer

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MediaPickerActivity : AppCompatActivity() {

    // ── 상수 ─────────────────────────────────────────────────
    companion object {
        const val EXTRA_PLAYLIST_ID = "playlist_id"
        private const val FIRST_CHUNK = 50
    }

    // ── Data Model ────────────────────────────────────────────
    data class MediaPickerItem(
        val path: String,
        val name: String,
        val width: Int,
        val height: Int,
        val duration: Long
    ) {
        val resolutionLabel: String get() = when {
            height >= 2160 -> "4K"
            height >= 1080 -> "1080P"
            height >= 720  -> "720P"
            height >= 480  -> "480P"
            else           -> "${height}P"
        }

        val durationLabel: String get() {
            val total = duration / 1000
            val h = total / 3600
            val m = (total % 3600) / 60
            val s = total % 60
            return if (h > 0) "%d:%02d:%02d".format(h, m, s)
            else              "%02d:%02d".format(m, s)
        }
    }

    // ── 필드 ─────────────────────────────────────────────────
    private lateinit var repo: PlaylistRepository
    private lateinit var adapter: MediaPickerAdapter

    private lateinit var tvTitle: TextView
    private lateinit var etSearch: EditText
    private lateinit var btnClearSearch: ImageButton
    private lateinit var rvMedia: RecyclerView
    private lateinit var btnConfirmAdd: Button

    private var playlistId: Long = -1L
    private val allItems = mutableListOf<MediaPickerItem>()

    // ── onCreate ─────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_media_picker)

        playlistId = intent.getLongExtra(EXTRA_PLAYLIST_ID, -1L)
        if (playlistId == -1L) { finish(); return }

        repo = PlaylistRepository(this)

        bindViews()
        setupAdapter()
        setupSearch()
        loadMedia()
    }

    private fun bindViews() {
        tvTitle        = findViewById(R.id.tvPickerTitle)
        etSearch       = findViewById(R.id.etSearch)
        btnClearSearch = findViewById(R.id.btnClearSearch)
        rvMedia        = findViewById(R.id.rvMedia)
        btnConfirmAdd  = findViewById(R.id.btnConfirmAdd)

        findViewById<ImageButton>(R.id.btnClose).setOnClickListener { finish() }
        btnConfirmAdd.setOnClickListener { confirmAdd() }
    }

    private fun setupAdapter() {
        adapter = MediaPickerAdapter { count ->
            btnConfirmAdd.isEnabled = count > 0
            btnConfirmAdd.text      = if (count > 0) "추가 ($count)" else "추가"
            tvTitle.text            = "Toevoegen aan playlist ($count video's)"
        }
        rvMedia.layoutManager = LinearLayoutManager(this)
        rvMedia.adapter       = adapter
    }

    // ── 검색 ─────────────────────────────────────────────────
    private fun setupSearch() {
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) = Unit
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString()?.trim() ?: ""
                btnClearSearch.visibility = if (query.isEmpty()) View.GONE else View.VISIBLE
                applyFilter(query)
            }
        })
        btnClearSearch.setOnClickListener { etSearch.text.clear() }
    }

    private fun applyFilter(query: String) {
        val filtered = if (query.isEmpty()) allItems
        else allItems.filter { it.name.contains(query, ignoreCase = true) }
        adapter.submitList(filtered.toList())
    }

    // ── 미디어 로딩 ───────────────────────────────────────────
    /**
     * Flow 기반 2단계 emit
     * 1차: 첫 50개 → 빠른 초기 표시
     * 2차: 전체
     * IO 스레드에서 collect, UI 스레드에서 submitList
     */
    private fun loadMedia() {
        lifecycleScope.launch {
            mediaFlow(this@MediaPickerActivity).collect { (isPartial, items) ->
                if (isPartial) {
                    // 1차: allItems 가 아직 비어있을 때만 추가
                    if (allItems.isEmpty()) allItems.addAll(items)
                } else {
                    // 2차: 전체 교체
                    allItems.clear()
                    allItems.addAll(items)
                }
                val query = etSearch.text?.toString()?.trim() ?: ""
                val filtered = if (query.isEmpty()) items
                else items.filter { it.name.contains(query, ignoreCase = true) }
                adapter.submitList(filtered.toList())
            }
        }
    }

    private fun mediaFlow(context: Context): Flow<Pair<Boolean, List<MediaPickerItem>>> = flow {
        val projection = arrayOf(
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.DURATION
        )
        val sortOrder = "${MediaStore.Video.Media.DATE_MODIFIED} DESC"
        val result    = mutableListOf<MediaPickerItem>()

        context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection, null, null, sortOrder
        )?.use { cursor ->
            val dataCol   = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
            val nameCol   = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val widthCol  = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
            val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
            val durCol    = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)

            while (cursor.moveToNext()) {
                val path = cursor.getString(dataCol) ?: continue
                result.add(
                    MediaPickerItem(
                        path     = path,
                        name     = cursor.getString(nameCol) ?: File(path).name,
                        width    = cursor.getInt(widthCol),
                        height   = cursor.getInt(heightCol),
                        duration = cursor.getLong(durCol)
                    )
                )
                // 1차 emit
                if (result.size == FIRST_CHUNK) emit(true to result.toList())
            }
        }
        // 2차 emit (전체)
        emit(false to result)

    }.flowOn(Dispatchers.IO)

    // ── 추가 확정 ─────────────────────────────────────────────
    private fun confirmAdd() {
        val selected = adapter.getSelectedItems()
        if (selected.isEmpty()) return

        lifecycleScope.launch {
            var addedCount = 0
            var dupCount   = 0
            selected.forEach { item ->
                val result = withContext(Dispatchers.IO) {
                    repo.addMediaToPlaylist(playlistId, item.path)
                }
                when (result) {
                    PlaylistRepository.AddResult.ADDED     -> addedCount++
                    PlaylistRepository.AddResult.DUPLICATE -> dupCount++
                    PlaylistRepository.AddResult.ERROR     -> Unit
                }
            }
            val msg = buildString {
                if (addedCount > 0) append("${addedCount}개 추가되었습니다.")
                if (dupCount > 0)   append(" (${dupCount}개 중복 제외)")
            }
            Toast.makeText(this@MediaPickerActivity, msg, Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    // ── Adapter ───────────────────────────────────────────────
    inner class MediaPickerAdapter(
        private val onSelectionChanged: (Int) -> Unit
    ) : ListAdapter<MediaPickerItem, MediaPickerAdapter.VH>(
        object : DiffUtil.ItemCallback<MediaPickerItem>() {
            override fun areItemsTheSame(a: MediaPickerItem, b: MediaPickerItem) = a.path == b.path
            override fun areContentsTheSame(a: MediaPickerItem, b: MediaPickerItem) = a == b
        }
    ) {
        // 선택 상태를 path 기준으로 별도 관리 → submitList 후에도 유지
        private val selectedPaths = mutableSetOf<String>()

        fun getSelectedItems(): List<MediaPickerItem> =
            currentList.filter { it.path in selectedPaths }

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val checkbox  : CheckBox  = view.findViewById(R.id.checkbox)
            val ivThumb   : ImageView = view.findViewById(R.id.ivThumb)
            val tvDuration: TextView  = view.findViewById(R.id.tvDuration)
            val tvTitle   : TextView  = view.findViewById(R.id.tvTitle)
            val tvMeta    : TextView  = view.findViewById(R.id.tvMeta)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_media_picker, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = getItem(position)

            holder.tvTitle.text    = item.name
            holder.tvMeta.text     = item.resolutionLabel
            holder.tvDuration.text = item.durationLabel
            holder.checkbox.isChecked = item.path in selectedPaths

            Glide.with(holder.ivThumb)
                .asBitmap()
                .load(File(item.path))
                .apply(
                    RequestOptions()
                        .frame(1_000_000L)
                        .transform(CenterCrop())
                        .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                        .placeholder(android.R.drawable.ic_media_play)
                )
                .into(holder.ivThumb)

            holder.itemView.setOnClickListener {
                if (item.path in selectedPaths) selectedPaths.remove(item.path)
                else selectedPaths.add(item.path)
                notifyItemChanged(position)
                onSelectionChanged(selectedPaths.size)
            }
        }

        override fun onViewRecycled(holder: VH) {
            super.onViewRecycled(holder)
            Glide.with(holder.ivThumb.context).clear(holder.ivThumb)
        }
    }
}