package nl.blauw.pipplayer

import android.content.Intent
import android.net.Uri
import android.text.format.DateFormat
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import nl.blauw.pipplayer.R
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.io.File
import java.util.Date

// ── 항목 타입 ─────────────────────────────────────────────────
enum class EntryType { DIRECTORY, VIDEO, IMAGE, GIF }

// ── Data Model ────────────────────────────────────────────────
data class FileEntry(
    val file: File,
    val type: EntryType
) {
    val name: String get() = file.name
    val path: String get() = file.absolutePath

    fun subtextFor(context: android.content.Context): String =
        if (type == EntryType.DIRECTORY) {
            val count = file.listFiles()?.size ?: 0
            "$count 항목"
        } else {
            val size = Formatter.formatShortFileSize(context, file.length())
            val date = DateFormat.format("yyyy-MM-dd", Date(file.lastModified()))
            "$size  •  $date"
        }
}

// ── 메뉴 액션 콜백 ────────────────────────────────────────────
data class FileMenuCallbacks(
    val onFavorite: (FileEntry) -> Unit,
    val onPlaylist: (FileEntry) -> Unit,
    val onShare:    (FileEntry) -> Unit,
    val onRename:   (FileEntry) -> Unit
)

// ── DiffUtil ──────────────────────────────────────────────────
private val DIFF = object : DiffUtil.ItemCallback<FileEntry>() {
    override fun areItemsTheSame(a: FileEntry, b: FileEntry) = a.path == b.path
    override fun areContentsTheSame(a: FileEntry, b: FileEntry) = a == b
}

// ── 공용 Glide RequestOptions ─────────────────────────────────
// 디스크 캐시 활성화 + 모서리 둥글게 (4dp)
private val THUMBNAIL_OPTIONS = RequestOptions()
    .transform(CenterCrop(), RoundedCorners(8))
    .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
    .placeholder(android.R.drawable.ic_menu_gallery)
    .error(android.R.drawable.ic_menu_gallery)

private val VIDEO_OPTIONS = THUMBNAIL_OPTIONS
    .clone()
    .frame(1_000_000L)   // 1초 지점 프레임 추출 (마이크로초 단위)

// ── Adapter ───────────────────────────────────────────────────
class FileListAdapter(
    private val onDirectoryClick: (FileEntry) -> Unit,
    private val onFileClick: (FileEntry) -> Unit,
    private val menuCallbacks: FileMenuCallbacks? = null
) : ListAdapter<FileEntry, FileListAdapter.EntryViewHolder>(DIFF) {

    inner class EntryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivThumbnail: ImageView   = itemView.findViewById(R.id.ivEntryThumbnail)
        val ivPlayOverlay: ImageView = itemView.findViewById(R.id.ivPlayOverlay)
        val tvGifBadge: TextView     = itemView.findViewById(R.id.tvGifBadge)
        val tvName: TextView         = itemView.findViewById(R.id.tvEntryName)
        val tvSubtext: TextView      = itemView.findViewById(R.id.tvEntrySubtext)
        val ivAction: ImageView      = itemView.findViewById(R.id.ivEntryAction)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EntryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_file_entry, parent, false)
        return EntryViewHolder(view)
    }

    override fun onBindViewHolder(holder: EntryViewHolder, position: Int) {
        val entry = getItem(position)
        val ctx   = holder.itemView.context

        holder.tvName.text    = entry.name
        holder.tvSubtext.text = entry.subtextFor(ctx)

        when (entry.type) {

            // ── 디렉토리: 기본 폴더 아이콘 ──────────────────────
            EntryType.DIRECTORY -> {
                // Glide 로딩 취소 후 기본 아이콘 표시
                Glide.with(ctx).clear(holder.ivThumbnail)
                holder.ivThumbnail.setImageResource(R.drawable.ic_folder_default)
                holder.ivThumbnail.scaleType = ImageView.ScaleType.CENTER_INSIDE
                holder.ivThumbnail.setBackgroundColor(0xFFE0E4EA.toInt())
                holder.ivPlayOverlay.visibility = View.GONE
                holder.tvGifBadge.visibility    = View.GONE
                //holder.ivAction.visibility = View.GONE
                holder.ivAction.setImageResource(android.R.drawable.ic_media_next)
                holder.ivAction.setOnClickListener(null)
            }

            // ── 동영상: 1초 지점 프레임 추출 ────────────────────
            EntryType.VIDEO -> {
                holder.ivThumbnail.scaleType    = ImageView.ScaleType.CENTER_CROP
                holder.ivPlayOverlay.visibility = View.VISIBLE
                holder.tvGifBadge.visibility    = View.GONE
                holder.ivAction.setImageResource(android.R.drawable.ic_menu_more)
                holder.ivThumbnail.setBackgroundColor(0xFFEEEEEE.toInt())

                Glide.with(ctx)
                    .asBitmap()
                    .load(entry.file)
                    .apply(VIDEO_OPTIONS)
                    .into(holder.ivThumbnail)

                holder.ivAction.setOnClickListener { v -> showFileMenuWithContext(entry, v) }
            }

            // ── 이미지: 파일 직접 로딩 ──────────────────────────
            EntryType.IMAGE -> {
                holder.ivThumbnail.scaleType = ImageView.ScaleType.CENTER_CROP
                holder.ivPlayOverlay.visibility = View.GONE
                holder.tvGifBadge.visibility    = View.GONE
                holder.ivAction.setImageResource(android.R.drawable.ic_menu_more)
                holder.ivThumbnail.setBackgroundColor(0xFFEEEEEE.toInt())

                Glide.with(ctx)
                    .asBitmap()
                    .load(entry.file)
                    .apply(THUMBNAIL_OPTIONS)
                    .into(holder.ivThumbnail)

                holder.ivAction.setOnClickListener { v -> showFileMenuWithContext(entry, v) }
            }
            EntryType.GIF -> {
                holder.ivThumbnail.scaleType = ImageView.ScaleType.CENTER_CROP
                holder.ivPlayOverlay.visibility = View.GONE
                holder.tvGifBadge.visibility    = View.VISIBLE
                holder.ivAction.setImageResource(android.R.drawable.ic_menu_more)
                holder.ivThumbnail.setBackgroundColor(0xFFEEEEEE.toInt())

                Glide.with(ctx)
                    .asGif()
                    .load(entry.file)
                    .apply(THUMBNAIL_OPTIONS)
                    .into(holder.ivThumbnail)

                holder.ivAction.setOnClickListener { v -> showFileMenuWithContext(entry, v) }
            }
        }

        holder.itemView.setOnClickListener {
            if (entry.type == EntryType.DIRECTORY) onDirectoryClick(entry)
            else onFileClick(entry)
        }
    }

    // ── Bottom Sheet 메뉴 ─────────────────────────────────────
    private fun showFileMenuWithContext(entry: FileEntry, view: View) {
        val ctx = view.context

        val dialog = BottomSheetDialog(ctx)
        val sheetView = LayoutInflater.from(ctx)
            .inflate(R.layout.bottom_sheet_file_menu, null)

        // 파일명 타이틀
        sheetView.findViewById<TextView>(R.id.tvMenuFileName).text = entry.name

        // ── 즐겨찾기 추가 ────────────────────────────────────
        sheetView.findViewById<View>(R.id.menuFavorite).setOnClickListener {
            dialog.dismiss()
            menuCallbacks?.onFavorite?.invoke(entry)
                ?: Toast.makeText(ctx, "즐겨찾기에 추가되었습니다", Toast.LENGTH_SHORT).show()
        }
        
        // ── 플레이리스트 추가 ─────────────────────────────────
        sheetView.findViewById<View>(R.id.menuPlaylist).setOnClickListener {
            dialog.dismiss()
            menuCallbacks?.onPlaylist?.invoke(entry)
                ?: Toast.makeText(ctx, "플레이리스트에 추가되었습니다", Toast.LENGTH_SHORT).show()
        }

        // ── 공유 ──────────────────────────────────────────────
        sheetView.findViewById<View>(R.id.menuShare).setOnClickListener {
            dialog.dismiss()
            if (menuCallbacks != null) {
                menuCallbacks.onShare(entry)
            } else {
                val uri = Uri.fromFile(entry.file)
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = when (entry.type) {
                        EntryType.VIDEO -> "video/*"
                        EntryType.GIF   -> "image/gif"
                        else            -> "image/*"
                    }
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                ctx.startActivity(Intent.createChooser(shareIntent, "공유"))
            }
        }

        // ── 이름 변경 ─────────────────────────────────────────
        sheetView.findViewById<View>(R.id.menuRename).setOnClickListener {
            dialog.dismiss()
            menuCallbacks?.onRename?.invoke(entry)
                ?: Toast.makeText(ctx, "이름 변경: ${entry.name}", Toast.LENGTH_SHORT).show()
        }

        dialog.setContentView(sheetView)
        dialog.show()
    }

    override fun onBindViewHolder(holder: EntryViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) {
            onBindViewHolder(holder, position)
            return
        }
        super.onBindViewHolder(holder, position, payloads)
    }

    // RecyclerView 에서 뷰가 재활용될 때 진행 중인 Glide 로딩 취소
    override fun onViewRecycled(holder: EntryViewHolder) {
        super.onViewRecycled(holder)
        Glide.with(holder.ivThumbnail.context).clear(holder.ivThumbnail)
    }

}