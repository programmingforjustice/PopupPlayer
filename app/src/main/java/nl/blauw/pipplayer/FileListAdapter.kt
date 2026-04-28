package nl.blauw.pipplayer

import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.text.format.DateFormat
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.io.File
import java.util.Date

// ── 항목 타입 ─────────────────────────────────────────────────
enum class EntryType { DIRECTORY, VIDEO, IMAGE, GIF }

// ── Data Model ────────────────────────────────────────────────
data class FileEntry(
    val file: File,
    val type: EntryType,
    val duration: Long = 0L   // 동영상 재생시간 (ms), 비디오 외에는 0
) {
    val name: String get() = file.name
    val path: String get() = file.absolutePath

    val durationLabel: String get() {
        if (duration <= 0L) return ""
        val total = duration / 1000
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s)
        else              "%02d:%02d".format(m, s)
    }

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

// ── Adapter ───────────────────────────────────────────────────
class FileListAdapter(
    private val onDirectoryClick: (FileEntry) -> Unit,
    private val onFileClick:      (FileEntry) -> Unit,
    private val menuCallbacks:    FileMenuCallbacks? = null
) : ListAdapter<FileListAdapter.ListItem, RecyclerView.ViewHolder>(
    object : DiffUtil.ItemCallback<ListItem>() {
        override fun areItemsTheSame(a: ListItem, b: ListItem) = when {
            a is ListItem.Header && b is ListItem.Header -> a.title == b.title
            a is ListItem.Entry  && b is ListItem.Entry  -> a.entry.path == b.entry.path
            else -> false
        }
        override fun areContentsTheSame(a: ListItem, b: ListItem) = a == b
    }
) {

    // ── 내부 타입 ─────────────────────────────────────────────
    sealed class ListItem {
        data class Header(val title: String) : ListItem()
        data class Entry(val entry: FileEntry) : ListItem()
    }

    companion object {
        private const val VT_HEADER     = 0
        private const val VT_DIR_LIST   = 1
        private const val VT_MEDIA_LIST = 2
        private const val VT_DIR_GRID   = 3
        private const val VT_MEDIA_GRID = 4

        private val LIST_THUMB_OPT = RequestOptions()
            .transform(CenterCrop(), RoundedCorners(8))
            .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
            .placeholder(android.R.drawable.ic_menu_gallery)
            .error(android.R.drawable.ic_menu_gallery)

        private val LIST_VIDEO_OPT = LIST_THUMB_OPT.clone().frame(1_000_000L)

        private val GRID_THUMB_OPT = RequestOptions()
            .transform(CenterCrop())
            .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
            .placeholder(android.R.drawable.ic_menu_gallery)

        private val GRID_VIDEO_OPT = GRID_THUMB_OPT.clone().frame(1_000_000L)
    }

    // ── 상태 ─────────────────────────────────────────────────
    var isGrid: Boolean = false
        set(value) { if (field != value) { field = value; rebuildList() } }

    private var rawEntries: List<FileEntry> = emptyList()

    fun submitEntries(entries: List<FileEntry>) {
        rawEntries = entries
        rebuildList()
    }

    private fun rebuildList() {
        val items = mutableListOf<ListItem>()
        val dirs  = rawEntries.filter { it.type == EntryType.DIRECTORY }
        val media = rawEntries.filter { it.type != EntryType.DIRECTORY }
        if (isGrid) {
            if (dirs.isNotEmpty()) {
                items.add(ListItem.Header("Mappen"))
                dirs.forEach { items.add(ListItem.Entry(it)) }
            }
            if (media.isNotEmpty()) {
                items.add(ListItem.Header("Video's"))
                media.forEach { items.add(ListItem.Entry(it)) }
            }
        } else {
            dirs.forEach  { items.add(ListItem.Entry(it)) }
            media.forEach { items.add(ListItem.Entry(it)) }
        }
        submitList(items)
    }

    override fun getItemViewType(position: Int) = when (val item = getItem(position)) {
        is ListItem.Header -> VT_HEADER
        is ListItem.Entry  -> when (item.entry.type) {
            EntryType.DIRECTORY -> if (isGrid) VT_DIR_GRID   else VT_DIR_LIST
            else                -> if (isGrid) VT_MEDIA_GRID else VT_MEDIA_LIST
        }
    }

    // ── SpanSizeLookup ────────────────────────────────────────
    fun getSpanSizeLookup(spanCount: Int): GridLayoutManager.SpanSizeLookup =
        object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int) = when (getItemViewType(position)) {
                VT_DIR_GRID, VT_MEDIA_GRID -> 1
                else -> spanCount  // 헤더 + 리스트 모드 전체 너비
            }
        }

    // ── ViewHolders ───────────────────────────────────────────
    class HeaderVH(view: View) : RecyclerView.ViewHolder(view) {
        val tv: TextView = view as TextView
    }

    class ListDirVH(view: View) : RecyclerView.ViewHolder(view) {
        val ivThumb:   ImageView = view.findViewById(R.id.ivEntryThumbnail)
        val ivOverlay: ImageView = view.findViewById(R.id.ivPlayOverlay)
        val tvGif:     TextView  = view.findViewById(R.id.tvGifBadge)
        val tvName:    TextView  = view.findViewById(R.id.tvEntryName)
        val tvSub:     TextView  = view.findViewById(R.id.tvEntrySubtext)
        val ivAction:  ImageView = view.findViewById(R.id.ivEntryAction)
    }

    class ListMediaVH(view: View) : RecyclerView.ViewHolder(view) {
        val ivThumb:   ImageView = view.findViewById(R.id.ivEntryThumbnail)
        val ivOverlay: ImageView = view.findViewById(R.id.ivPlayOverlay)
        val tvDuration: TextView = view.findViewById(R.id.tvDuration)
        val tvGif:     TextView  = view.findViewById(R.id.tvGifBadge)
        val tvName:    TextView  = view.findViewById(R.id.tvEntryName)
        val tvSub:     TextView  = view.findViewById(R.id.tvEntrySubtext)
        val ivAction:  ImageView = view.findViewById(R.id.ivEntryAction)
    }

    class GridDirVH(view: View) : RecyclerView.ViewHolder(view) {
        val ivIcon:  ImageView = view.findViewById(R.id.ivFolderIcon)
        val tvName:  TextView  = view.findViewById(R.id.tvDirName)
        val tvSub:   TextView  = view.findViewById(R.id.tvDirSubtext)
    }

    class GridMediaVH(view: View) : RecyclerView.ViewHolder(view) {
        val ivThumb:   ImageView = view.findViewById(R.id.ivEntryThumbnail)
        val tvNew:     TextView  = view.findViewById(R.id.tvNewBadge)
        val tvDur:     TextView  = view.findViewById(R.id.tvDuration)
        val tvGif:     TextView  = view.findViewById(R.id.tvGifBadge)
        val tvName:    TextView  = view.findViewById(R.id.tvEntryName)
        val ivAction:  ImageView = view.findViewById(R.id.ivEntryAction)
    }

    // ── onCreateViewHolder ────────────────────────────────────
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        fun inflate(res: Int) = LayoutInflater.from(parent.context).inflate(res, parent, false)
        return when (viewType) {
            VT_HEADER     -> HeaderVH(inflate(R.layout.item_section_header))
            VT_DIR_LIST   -> ListDirVH(inflate(R.layout.item_file_entry))
            VT_MEDIA_LIST -> ListMediaVH(inflate(R.layout.item_file_entry))
            VT_DIR_GRID   -> GridDirVH(inflate(R.layout.item_directory_grid))
            else          -> GridMediaVH(inflate(R.layout.item_file_entry_grid))
        }
    }

    // ── onBindViewHolder ──────────────────────────────────────
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        when (holder) {

            is HeaderVH -> holder.tv.text = (item as ListItem.Header).title

            is ListDirVH -> {
                val entry = (item as ListItem.Entry).entry
                val ctx   = holder.itemView.context
                Glide.with(ctx).clear(holder.ivThumb)
                holder.ivThumb.setImageResource(R.drawable.ic_folder_default)
                holder.ivThumb.scaleType    = ImageView.ScaleType.CENTER_INSIDE
                holder.ivThumb.setBackgroundColor(0xFFE0E4EA.toInt())
                holder.ivOverlay.visibility = View.GONE
                holder.tvGif.visibility     = View.GONE
                holder.tvName.text          = entry.name
                holder.tvSub.text           = entry.subtextFor(ctx)
                holder.ivAction.visibility  = View.GONE
                holder.ivAction.setOnClickListener(null)
                holder.itemView.setOnClickListener { onDirectoryClick(entry) }
            }

            is ListMediaVH -> {
                val entry = (item as ListItem.Entry).entry
                val ctx   = holder.itemView.context
                holder.tvName.text = entry.name
                holder.tvSub.text  = entry.subtextFor(ctx)
                holder.ivAction.visibility = View.VISIBLE
                holder.ivAction.setImageResource(android.R.drawable.ic_menu_more)
                holder.ivAction.setOnClickListener { v -> showMenu(entry, v) }
                when (entry.type) {
                    EntryType.VIDEO -> {
                        holder.ivThumb.scaleType    = ImageView.ScaleType.CENTER_CROP
                        holder.ivOverlay.visibility = View.VISIBLE
                        holder.tvGif.visibility     = View.GONE
                        holder.ivThumb.setBackgroundColor(0xFFEEEEEE.toInt())
                        // duration 오버레이 표시
                        if (entry.durationLabel.isNotEmpty()) {
                            holder.tvDuration.visibility = View.VISIBLE
                            holder.tvDuration.text       = entry.durationLabel
                        } else {
                            holder.tvDuration.visibility = View.GONE
                        }
                        Glide.with(ctx).asBitmap().load(entry.file).apply(LIST_VIDEO_OPT).into(holder.ivThumb)
                    }
                    EntryType.IMAGE -> {
                        holder.ivThumb.scaleType     = ImageView.ScaleType.CENTER_CROP
                        holder.ivOverlay.visibility  = View.GONE
                        holder.tvGif.visibility      = View.GONE
                        holder.tvDuration.visibility = View.GONE
                        holder.ivThumb.setBackgroundColor(0xFFEEEEEE.toInt())
                        Glide.with(ctx).asBitmap().load(entry.file).apply(LIST_THUMB_OPT).into(holder.ivThumb)
                    }
                    EntryType.GIF -> {
                        holder.ivThumb.scaleType     = ImageView.ScaleType.CENTER_CROP
                        holder.ivOverlay.visibility  = View.GONE
                        holder.tvGif.visibility      = View.VISIBLE
                        holder.tvDuration.visibility = View.GONE
                        holder.ivThumb.setBackgroundColor(0xFFEEEEEE.toInt())
                        Glide.with(ctx).asGif().load(entry.file).apply(LIST_THUMB_OPT).into(holder.ivThumb)
                    }
                    else -> Unit
                }
                holder.itemView.setOnClickListener { onFileClick(entry) }
            }

            is GridDirVH -> {
                val entry = (item as ListItem.Entry).entry
                holder.ivIcon.setImageResource(R.drawable.ic_folder_default)
                holder.tvName.text = entry.name
                holder.tvSub.text  = entry.subtextFor(holder.itemView.context)
                holder.itemView.setOnClickListener { onDirectoryClick(entry) }
            }

            is GridMediaVH -> {
                val entry = (item as ListItem.Entry).entry
                val ctx   = holder.itemView.context
                holder.tvName.text = entry.name
                holder.tvNew.visibility = View.GONE
                holder.ivAction.setOnClickListener { v -> showMenu(entry, v) }
                when (entry.type) {
                    EntryType.VIDEO -> {
                        // duration 오버레이 표시
                        if (entry.durationLabel.isNotEmpty()) {
                            holder.tvDur.visibility = View.VISIBLE
                            holder.tvDur.text       = entry.durationLabel
                        } else {
                            holder.tvDur.visibility = View.GONE
                        }
                        holder.tvGif.visibility = View.GONE
                        holder.ivThumb.setBackgroundColor(0xFFEEEEEE.toInt())
                        Glide.with(ctx).asBitmap().load(entry.file).apply(GRID_VIDEO_OPT).into(holder.ivThumb)
                    }
                    EntryType.IMAGE -> {
                        holder.tvDur.visibility = View.GONE
                        holder.tvGif.visibility = View.GONE
                        holder.ivThumb.setBackgroundColor(0xFFEEEEEE.toInt())
                        Glide.with(ctx).asBitmap().load(entry.file).apply(GRID_THUMB_OPT).into(holder.ivThumb)
                    }
                    EntryType.GIF -> {
                        holder.tvDur.visibility = View.GONE
                        holder.tvGif.visibility = View.VISIBLE
                        holder.ivThumb.setBackgroundColor(0xFFEEEEEE.toInt())
                        Glide.with(ctx).asGif().load(entry.file).apply(GRID_THUMB_OPT).into(holder.ivThumb)
                    }
                    else -> Unit
                }
                holder.itemView.setOnClickListener { onFileClick(entry) }
            }
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        val iv = when (holder) {
            is ListDirVH   -> holder.ivThumb
            is ListMediaVH -> holder.ivThumb
            is GridMediaVH -> holder.ivThumb
            else           -> return
        }
        Glide.with(iv.context).clear(iv)
    }

    // ── Bottom Sheet 파일 메뉴 ────────────────────────────────
    private fun showMenu(entry: FileEntry, view: View) {
        val ctx       = view.context
        val dialog    = BottomSheetDialog(ctx)
        val sheetView = LayoutInflater.from(ctx).inflate(R.layout.bottom_sheet_file_menu, null)
        sheetView.findViewById<TextView>(R.id.tvMenuFileName).text = entry.name
        sheetView.findViewById<View>(R.id.menuFavorite).setOnClickListener {
            dialog.dismiss()
            menuCallbacks?.onFavorite?.invoke(entry)
                ?: Toast.makeText(ctx, "즐겨찾기에 추가되었습니다", Toast.LENGTH_SHORT).show()
        }
        sheetView.findViewById<View>(R.id.menuPlaylist).setOnClickListener {
            dialog.dismiss()
            if (menuCallbacks != null) menuCallbacks.onPlaylist(entry)
            else (view.context as? FragmentActivity)?.supportFragmentManager?.let {
                PlaylistBottomSheet.newInstance(entry.path).show(it, PlaylistBottomSheet.TAG)
            }
        }
        sheetView.findViewById<View>(R.id.menuShare).setOnClickListener {
            dialog.dismiss()
            if (menuCallbacks != null) {
                menuCallbacks.onShare(entry)
            } else {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = when (entry.type) {
                        EntryType.VIDEO -> "video/*"
                        EntryType.GIF   -> "image/gif"
                        else            -> "image/*"
                    }
                    putExtra(Intent.EXTRA_STREAM, Uri.fromFile(entry.file))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                ctx.startActivity(Intent.createChooser(shareIntent, "공유"))
            }
        }
        sheetView.findViewById<View>(R.id.menuRename).setOnClickListener {
            dialog.dismiss()
            menuCallbacks?.onRename?.invoke(entry)
                ?: Toast.makeText(ctx, "이름 변경: ${entry.name}", Toast.LENGTH_SHORT).show()
        }
        dialog.setContentView(sheetView)
        dialog.show()
    }
}