package nl.blauw.pipplayer

import android.content.ContentResolver
import android.content.ContentUris
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

@RequiresApi(Build.VERSION_CODES.R)
class RecycleBinActivity : AppCompatActivity() {

    data class TrashedItem(
        val id: Long,
        val displayName: String,
        val duration: Long,
        val dateTrashed: Long,
        val contentUri: android.net.Uri
    ) {
        val daysLeft: Int get() {
            val elapsed = System.currentTimeMillis() - dateTrashed
            val days = TimeUnit.MILLISECONDS.toDays(elapsed)
            return maxOf(0, 30 - days.toInt())
        }
    }

    private lateinit var rvRecycleBin: RecyclerView
    private lateinit var layoutEmpty: LinearLayout
    private lateinit var adapter: RecycleBinAdapter

    private lateinit var multiselectBar: View
    private lateinit var tvMultiCount: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recycle_bin)

        rvRecycleBin = findViewById(R.id.rvRecycleBin)
        layoutEmpty  = findViewById(R.id.layoutEmpty)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.btnInfo).setOnClickListener {
            Toast.makeText(this, "파일은 30일간 보관 후 영구 삭제됩니다.", Toast.LENGTH_LONG).show()
        }

        setupRecyclerView()
        setupMultiselectBar()
        setupBackPressed()
        loadTrashedFiles()
    }

    private fun setupRecyclerView() {
        adapter = RecycleBinAdapter(
            onRestoreClick = { item -> restoreItem(item) },
            onDeleteClick  = { item -> permanentlyDelete(item) }
        )
        adapter.onSelectionChanged = { count -> updateMultiselectBar(count) }

        rvRecycleBin.layoutManager = LinearLayoutManager(this)
        rvRecycleBin.adapter = adapter
    }

    private fun setupMultiselectBar() {
        multiselectBar = findViewById(R.id.rbMultiselectBar)
        tvMultiCount   = multiselectBar.findViewById(R.id.tvRbMultiCount)

        multiselectBar.findViewById<View>(R.id.btnRbMultiClose).setOnClickListener {
            adapter.exitMultiSelectMode()
        }
        multiselectBar.findViewById<View>(R.id.btnRbMultiRestore).setOnClickListener {
            val selected = adapter.getSelectedItems()
            if (selected.isEmpty()) return@setOnClickListener
            restoreItems(selected)
        }
        multiselectBar.findViewById<View>(R.id.btnRbMultiDelete).setOnClickListener {
            val selected = adapter.getSelectedItems()
            if (selected.isEmpty()) return@setOnClickListener
            showBulkDeleteConfirmDialog(selected)
        }
        multiselectBar.findViewById<View>(R.id.btnRbMultiMore).setOnClickListener {
            showMultiselectMoreMenu()
        }
    }

    private fun setupBackPressed() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (adapter.isMultiSelectMode) {
                    adapter.exitMultiSelectMode()
                    return
                }
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        })
    }

    private fun updateMultiselectBar(count: Int) {
        if (count > 0) {
            multiselectBar.visibility = View.VISIBLE
            tvMultiCount.text = count.toString()
        } else {
            multiselectBar.visibility = View.GONE
        }
    }

    private fun showMultiselectMoreMenu() {
        val dialog    = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val sheetView = layoutInflater.inflate(R.layout.bottom_sheet_rb_multiselect_menu, null)
        sheetView.findViewById<View>(R.id.menuRbSelectAll).setOnClickListener {
            dialog.dismiss()
            adapter.selectAll()
        }
        dialog.setContentView(sheetView)
        dialog.show()
    }

    // ── MediaStore 쿼리 ───────────────────────────────────────
    private fun loadTrashedFiles() {
        lifecycleScope.launch {
            val items = withContext(Dispatchers.IO) { queryTrashedFiles() }
            val isEmpty = items.isEmpty()
            layoutEmpty.visibility  = if (isEmpty) View.VISIBLE else View.GONE
            rvRecycleBin.visibility = if (isEmpty) View.GONE    else View.VISIBLE
            adapter.submitList(items)
        }
    }

    private fun queryTrashedFiles(): List<TrashedItem> {
        val result = mutableListOf<TrashedItem>()
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.Video.Media.DURATION,
            MediaStore.Files.FileColumns.DATE_EXPIRES
        )

        val queryArgs = android.os.Bundle().apply {
            putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_ONLY)
            putString(
                ContentResolver.QUERY_ARG_SQL_SELECTION,
                "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO}, ${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE})"
            )
            putString(
                ContentResolver.QUERY_ARG_SORT_COLUMNS,
                MediaStore.Files.FileColumns.DATE_EXPIRES
            )
            putInt(
                ContentResolver.QUERY_ARG_SORT_DIRECTION,
                ContentResolver.QUERY_SORT_DIRECTION_ASCENDING
            )
        }

        contentResolver.query(
            MediaStore.Files.getContentUri("external"),
            projection,
            queryArgs,
            null
        )?.use { cursor ->
            val idCol        = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val nameCol      = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val mediaTypeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
            val durCol       = cursor.getColumnIndex(MediaStore.Video.Media.DURATION)
            val expiresCol   = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_EXPIRES)

            while (cursor.moveToNext()) {
                val id        = cursor.getLong(idCol)
                val name      = cursor.getString(nameCol) ?: "unknown"
                val mediaType = cursor.getInt(mediaTypeCol)
                val duration  = if (durCol >= 0 && mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO)
                                    cursor.getLong(durCol) else 0L
                val expiresAt   = cursor.getLong(expiresCol) * 1000L
                val dateTrashed = expiresAt - TimeUnit.DAYS.toMillis(30)

                val baseUri = when (mediaType) {
                    MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO ->
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE ->
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    else ->
                        MediaStore.Files.getContentUri("external")
                }
                val uri = ContentUris.withAppendedId(baseUri, id)
                result.add(TrashedItem(id, name, duration, dateTrashed, uri))
            }
        }
        return result
    }

    // ── 단일 복원 ─────────────────────────────────────────────
    private fun restoreItem(item: TrashedItem) {
        lifecycleScope.launch {
            val pi = MediaStore.createTrashRequest(
                contentResolver, listOf(item.contentUri), false
            )
            restoreRequestLauncher.launch(
                androidx.activity.result.IntentSenderRequest.Builder(pi.intentSender).build()
            )
        }
    }

    // ── 멀티 복원 ─────────────────────────────────────────────
    private fun restoreItems(items: List<TrashedItem>) {
        lifecycleScope.launch {
            val uris = items.map { it.contentUri }
            val pi = MediaStore.createTrashRequest(contentResolver, uris, false)
            adapter.exitMultiSelectMode()
            restoreRequestLauncher.launch(
                androidx.activity.result.IntentSenderRequest.Builder(pi.intentSender).build()
            )
        }
    }

    // ── 단일 영구 삭제 ────────────────────────────────────────
    private fun permanentlyDelete(item: TrashedItem) {
        android.app.AlertDialog.Builder(this)
            .setTitle("영구 삭제")
            .setMessage("\"${item.displayName}\" 을(를) 영구 삭제하시겠습니까?\n복구할 수 없습니다.")
            .setPositiveButton("삭제") { _, _ ->
                lifecycleScope.launch {
                    val pi = MediaStore.createDeleteRequest(
                        contentResolver, listOf(item.contentUri)
                    )
                    deleteRequestLauncher.launch(
                        androidx.activity.result.IntentSenderRequest.Builder(pi.intentSender).build()
                    )
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    // ── 멀티 영구 삭제 확인 다이얼로그 ───────────────────────
    private fun showBulkDeleteConfirmDialog(items: List<TrashedItem>) {
        val count = items.size
        val message = if (count == 1)
            "\"${items.first().displayName}\" 을(를) 영구 삭제하시겠습니까?\n복구할 수 없습니다."
        else
            "${count}개 항목을 영구 삭제하시겠습니까?\n복구할 수 없습니다."

        android.app.AlertDialog.Builder(this)
            .setTitle("영구 삭제")
            .setMessage(message)
            .setPositiveButton("삭제") { _, _ ->
                lifecycleScope.launch {
                    val uris = items.map { it.contentUri }
                    val pi = MediaStore.createDeleteRequest(contentResolver, uris)
                    adapter.exitMultiSelectMode()
                    deleteRequestLauncher.launch(
                        androidx.activity.result.IntentSenderRequest.Builder(pi.intentSender).build()
                    )
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private val restoreRequestLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()
        ) { loadTrashedFiles() }

    private val deleteRequestLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()
        ) { loadTrashedFiles() }

    // ── Adapter ───────────────────────────────────────────────
    inner class RecycleBinAdapter(
        private val onRestoreClick: (TrashedItem) -> Unit,
        private val onDeleteClick:  (TrashedItem) -> Unit
    ) : ListAdapter<TrashedItem, RecycleBinAdapter.VH>(DIFF) {

        private val selectedIds = mutableSetOf<Long>()
        var isMultiSelectMode = false
            private set

        var onSelectionChanged: ((Int) -> Unit)? = null

        fun enterMultiSelectMode(id: Long) {
            isMultiSelectMode = true
            selectedIds.clear()
            selectedIds.add(id)
            notifyDataSetChanged()
            onSelectionChanged?.invoke(selectedIds.size)
        }

        fun exitMultiSelectMode() {
            isMultiSelectMode = false
            selectedIds.clear()
            notifyDataSetChanged()
            onSelectionChanged?.invoke(0)
        }

        fun selectAll() {
            for (i in 0 until itemCount) selectedIds.add(getItem(i).id)
            notifyDataSetChanged()
            onSelectionChanged?.invoke(selectedIds.size)
        }

        fun getSelectedItems(): List<TrashedItem> =
            (0 until itemCount).map { getItem(it) }.filter { it.id in selectedIds }

        private fun toggleSelection(id: Long) {
            if (id in selectedIds) {
                selectedIds.remove(id)
                if (selectedIds.isEmpty()) {
                    exitMultiSelectMode()
                    return
                }
            } else {
                selectedIds.add(id)
            }
            onSelectionChanged?.invoke(selectedIds.size)
        }

        private val THUMB_OPT = RequestOptions()
            .transform(CenterCrop())
            .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
            .placeholder(android.R.drawable.ic_menu_gallery)

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val ivThumb:           ImageView   = view.findViewById(R.id.ivThumb)
            val tvDuration:        TextView    = view.findViewById(R.id.tvDuration)
            val tvFileName:        TextView    = view.findViewById(R.id.tvFileName)
            val tvDaysLeft:        TextView    = view.findViewById(R.id.tvDaysLeft)
            val btnMore:           ImageButton = view.findViewById(R.id.btnMore)
            val viewSelectOverlay: View        = view.findViewById(R.id.viewSelectOverlay)
            val ivCheckMark:       ImageView   = view.findViewById(R.id.ivCheckMark)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_recycle_bin, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = getItem(position)
            holder.tvFileName.text = item.displayName
            holder.tvDaysLeft.text = "${item.daysLeft} dagen"

            if (item.duration > 0) {
                val total = item.duration / 1000
                val m = (total % 3600) / 60
                val s = total % 60
                holder.tvDuration.visibility = View.VISIBLE
                holder.tvDuration.text = "%02d:%02d".format(m, s)
            } else {
                holder.tvDuration.visibility = View.GONE
            }

            Glide.with(holder.ivThumb)
                .asBitmap()
                .load(item.contentUri)
                .apply(THUMB_OPT)
                .into(holder.ivThumb)

            // 선택 상태 오버레이
            val isSelected = item.id in selectedIds
            holder.viewSelectOverlay.visibility = if (isMultiSelectMode && isSelected) View.VISIBLE else View.GONE
            holder.ivCheckMark.visibility       = if (isMultiSelectMode && isSelected) View.VISIBLE else View.GONE
            holder.itemView.setBackgroundColor(
                if (isMultiSelectMode && isSelected) 0xFFF1F1F1.toInt() else 0x00FFFFFF
            )

            // 더보기 버튼: 멀티셀렉트 모드에서는 숨김
            holder.btnMore.visibility = if (isMultiSelectMode) View.GONE else View.VISIBLE
            holder.btnMore.setOnClickListener {
                com.google.android.material.bottomsheet.BottomSheetDialog(holder.itemView.context).also { dialog ->
                    val v = LayoutInflater.from(holder.itemView.context)
                        .inflate(R.layout.bottom_sheet_recycle_bin_menu, null)
                    v.findViewById<View>(R.id.menuRestore).setOnClickListener {
                        dialog.dismiss()
                        onRestoreClick(item)
                    }
                    v.findViewById<View>(R.id.menuPermanentDelete).setOnClickListener {
                        dialog.dismiss()
                        onDeleteClick(item)
                    }
                    dialog.setContentView(v)
                    dialog.show()
                }
            }

            holder.itemView.setOnClickListener {
                if (isMultiSelectMode) {
                    toggleSelection(item.id)
                    notifyItemChanged(position)
                }
            }
            holder.itemView.setOnLongClickListener {
                if (!isMultiSelectMode) {
                    enterMultiSelectMode(item.id)
                }
                true
            }
        }

        override fun onViewRecycled(holder: VH) {
            super.onViewRecycled(holder)
            Glide.with(holder.ivThumb.context).clear(holder.ivThumb)
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<TrashedItem>() {
            override fun areItemsTheSame(a: TrashedItem, b: TrashedItem) = a.id == b.id
            override fun areContentsTheSame(a: TrashedItem, b: TrashedItem) = a == b
        }
    }
}
