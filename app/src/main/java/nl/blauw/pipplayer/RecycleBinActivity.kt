package nl.blauw.pipplayer

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
import androidx.annotation.RequiresApi
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

@RequiresApi(Build.VERSION_CODES.R)
class RecycleBinActivity : AppCompatActivity() {

    data class TrashedItem(
        val id: Long,
        val displayName: String,
        val duration: Long,       // ms
        val dateTrashed: Long,    // epoch ms
        val contentUri: android.net.Uri
    ) {
        /** 삭제 후 30일 기준 남은 날 수 */
        val daysLeft: Int get() {
            val elapsed = System.currentTimeMillis() - dateTrashed
            val days = TimeUnit.MILLISECONDS.toDays(elapsed)
            return maxOf(0, 30 - days.toInt())
        }
    }

    private lateinit var rvRecycleBin: RecyclerView
    private lateinit var layoutEmpty: LinearLayout
    private lateinit var adapter: RecycleBinAdapter

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
        loadTrashedFiles()
    }

    private fun setupRecyclerView() {
        adapter = RecycleBinAdapter(
            onRestoreClick  = { item -> restoreItem(item) },
            onDeleteClick   = { item -> permanentlyDelete(item) }
        )
        rvRecycleBin.layoutManager = LinearLayoutManager(this)
        rvRecycleBin.adapter       = adapter
    }

    // ── MediaStore 에서 휴지통 항목 조회 ──────────────────────
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
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.DATE_EXPIRES   // 만료 시각 (epoch s)
        )
        // IS_TRASHED = 1 로 휴지통 항목만 조회
        val selection = "${MediaStore.Video.Media.IS_TRASHED} = 1"

        val queryUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            .buildUpon()
            .appendQueryParameter(MediaStore.QUERY_ARG_MATCH_TRASHED, "1")
            .build()

        contentResolver.query(
            queryUri, projection, selection, null,
            "${MediaStore.Video.Media.DATE_EXPIRES} ASC"
        )?.use { cursor ->
            val idCol      = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameCol    = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val durCol     = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val expiresCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_EXPIRES)

            while (cursor.moveToNext()) {
                val id         = cursor.getLong(idCol)
                val name       = cursor.getString(nameCol) ?: "unknown"
                val duration   = cursor.getLong(durCol)
                val expiresAt  = cursor.getLong(expiresCol) * 1000L // s → ms
                // dateTrashed = expiresAt - 30일
                val dateTrashed = expiresAt - TimeUnit.DAYS.toMillis(30)
                val uri = ContentUris.withAppendedId(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id
                )
                result.add(TrashedItem(id, name, duration, dateTrashed, uri))
            }
        }
        return result
    }

    // ── 복원 ─────────────────────────────────────────────────
    private fun restoreItem(item: TrashedItem) {
        lifecycleScope.launch {
            val pi = MediaStore.createRestoreRequest(contentResolver, listOf(item.contentUri))
            restoreRequestLauncher.launch(
                androidx.activity.result.IntentSenderRequest.Builder(pi.intentSender).build()
            )
        }
    }

    // ── 영구 삭제 ─────────────────────────────────────────────
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

    private val restoreRequestLauncher =
        registerForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult()
        ) { loadTrashedFiles() }

    private val deleteRequestLauncher =
        registerForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult()
        ) { loadTrashedFiles() }

    // ── Adapter ───────────────────────────────────────────────
    inner class RecycleBinAdapter(
        private val onRestoreClick: (TrashedItem) -> Unit,
        private val onDeleteClick:  (TrashedItem) -> Unit
    ) : ListAdapter<TrashedItem, RecycleBinAdapter.VH>(DIFF) {

        private val THUMB_OPT = RequestOptions()
            .transform(CenterCrop())
            .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
            .placeholder(android.R.drawable.ic_menu_gallery)

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val ivThumb:    ImageView   = view.findViewById(R.id.ivThumb)
            val tvDuration: TextView    = view.findViewById(R.id.tvDuration)
            val tvFileName: TextView    = view.findViewById(R.id.tvFileName)
            val tvDaysLeft: TextView    = view.findViewById(R.id.tvDaysLeft)
            val btnMore:    ImageButton = view.findViewById(R.id.btnMore)
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

            // duration
            if (item.duration > 0) {
                val total = item.duration / 1000
                val m = (total % 3600) / 60
                val s = total % 60
                holder.tvDuration.visibility = View.VISIBLE
                holder.tvDuration.text = "%02d:%02d".format(m, s)
            } else {
                holder.tvDuration.visibility = View.GONE
            }

            // 썸네일 (휴지통 항목은 contentUri 로 Glide 로딩)
            Glide.with(holder.ivThumb)
                .asBitmap()
                .load(item.contentUri)
                .apply(THUMB_OPT)
                .into(holder.ivThumb)

            // 더보기 → 복원 / 영구삭제
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