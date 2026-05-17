package nl.blauw.pipplayer

import android.content.Context
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import org.json.JSONArray
import java.io.File
import java.util.Date

// ── History Entry ─────────────────────────────────────────────────────────────

data class HistoryEntry(
    val id: Long,
    val playedAt: Long,
    val isNavigation: Boolean,
    val paths: List<String>
) {
    companion object {
        private val VIDEO_EXT = setOf("mp4","mkv","avi","mov","wmv","flv","webm","3gp","m4v","ts")
        private val GIF_EXT   = setOf("gif")

        fun fromPlayHistory(h: PlayHistory): HistoryEntry {
            val paths = mutableListOf<String>()
            runCatching {
                val arr = JSONArray(h.mediaPaths)
                repeat(arr.length()) { i -> paths.add(arr.getString(i)) }
            }
            return HistoryEntry(h.id, h.playedAt, h.isNavigation, paths)
        }

        fun List<String>.toHistoryJson(): String =
            JSONArray().also { arr -> forEach { arr.put(it) } }.toString()
    }

    val primaryPath: String get() = paths.firstOrNull() ?: ""

    val primaryType: EntryType
        get() {
            val ext = primaryPath.substringAfterLast('.', "").lowercase()
            return when {
                ext in VIDEO_EXT -> EntryType.VIDEO
                ext in GIF_EXT   -> EntryType.GIF
                else             -> EntryType.IMAGE
            }
        }

    val name: String get() {
        val first = if (primaryPath.isEmpty()) "Unknown" else File(primaryPath).name
        return if (isNavigation && paths.size > 1)
            "$first and ${paths.size - 1} other media"
        else
            first
    }

    fun subtextFor(context: Context): String {
        val date = DateFormat.format("yyyy-MM-dd HH:mm", Date(playedAt))
        return if (isNavigation) "${paths.size} files  •  $date" else date.toString()
    }
}

// ── Adapter ───────────────────────────────────────────────────────────────────

class HistoryAdapter(
    private val onEntryClick: (HistoryEntry) -> Unit
) : ListAdapter<HistoryAdapter.ListItem, RecyclerView.ViewHolder>(
    object : DiffUtil.ItemCallback<ListItem>() {
        override fun areItemsTheSame(a: ListItem, b: ListItem) = when {
            a is ListItem.Header && b is ListItem.Header -> a.title == b.title
            a is ListItem.Entry  && b is ListItem.Entry  -> a.entry.id == b.entry.id
            else -> false
        }
        override fun areContentsTheSame(a: ListItem, b: ListItem) = a == b
    }
) {

    sealed class ListItem {
        data class Header(val title: String) : ListItem()
        data class Entry(val entry: HistoryEntry) : ListItem()
    }

    companion object {
        private const val VT_HEADER = 0
        private const val VT_LIST   = 1
        private const val VT_GRID   = 2

        private val VIDEO_EXT = setOf("mp4","mkv","avi","mov","wmv","flv","webm","3gp","m4v","ts")

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

    // ── Multi-select ──────────────────────────────────────────────────────────
    private val selectedIds = mutableSetOf<Long>()
    var isMultiSelectMode = false
        private set

    var onSelectionChanged: ((count: Int, ids: Set<Long>) -> Unit)? = null

    fun enterMultiSelectMode(id: Long) {
        isMultiSelectMode = true
        selectedIds.clear()
        selectedIds.add(id)
        notifyDataSetChanged()
        onSelectionChanged?.invoke(selectedIds.size, selectedIds.toSet())
    }

    fun exitMultiSelectMode() {
        isMultiSelectMode = false
        selectedIds.clear()
        notifyDataSetChanged()
        onSelectionChanged?.invoke(0, emptySet())
    }

    fun selectAll() {
        rawEntries.forEach { selectedIds.add(it.id) }
        notifyDataSetChanged()
        onSelectionChanged?.invoke(selectedIds.size, selectedIds.toSet())
    }

    fun getSelectedEntries(): List<HistoryEntry> = rawEntries.filter { it.id in selectedIds }

    private fun toggleSelection(id: Long) {
        if (id in selectedIds) {
            selectedIds.remove(id)
            if (selectedIds.isEmpty()) { exitMultiSelectMode(); return }
        } else {
            selectedIds.add(id)
        }
        onSelectionChanged?.invoke(selectedIds.size, selectedIds.toSet())
    }

    // ── Filter & Search ───────────────────────────────────────────────────────
    enum class Filter { ALL, VIDEO, IMAGE }
    private var currentFilter = Filter.ALL

    fun setFilter(f: Filter) { currentFilter = f; rebuildList() }

    private var searchQuery = ""
    fun setSearchQuery(q: String) { searchQuery = q; rebuildList() }

    // ── Grid toggle ───────────────────────────────────────────────────────────
    var isGrid = false
        set(value) { if (field != value) { field = value; rebuildList() } }

    // ── Data ─────────────────────────────────────────────────────────────────
    private var rawEntries: List<HistoryEntry> = emptyList()

    fun submitEntries(entries: List<HistoryEntry>) {
        rawEntries = entries
        rebuildList()
    }

    fun removeEntries(ids: Set<Long>) {
        rawEntries = rawEntries.filter { it.id !in ids }
        rebuildList()
    }

    private fun filteredEntries(): List<HistoryEntry> {
        val byType = when (currentFilter) {
            Filter.VIDEO -> rawEntries.filter { it.primaryType == EntryType.VIDEO }
            Filter.IMAGE -> rawEntries.filter { it.primaryType == EntryType.IMAGE || it.primaryType == EntryType.GIF }
            Filter.ALL   -> rawEntries
        }
        return if (searchQuery.isBlank()) byType
        else byType.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    private fun rebuildList() {
        val items    = mutableListOf<ListItem>()
        val filtered = filteredEntries()
        if (isGrid && filtered.isNotEmpty()) {
            items.add(ListItem.Header("History"))
            filtered.forEach { items.add(ListItem.Entry(it)) }
        } else {
            filtered.forEach { items.add(ListItem.Entry(it)) }
        }
        submitList(items)
    }

    override fun getItemViewType(position: Int) = when (getItem(position)) {
        is ListItem.Header -> VT_HEADER
        is ListItem.Entry  -> if (isGrid) VT_GRID else VT_LIST
    }

    fun getSpanSizeLookup(spanCount: Int): GridLayoutManager.SpanSizeLookup =
        object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int) =
                if (getItemViewType(position) == VT_HEADER) spanCount else 1
        }

    // ── ViewHolders ───────────────────────────────────────────────────────────
    class HeaderVH(view: View) : RecyclerView.ViewHolder(view) {
        val tv: TextView = view as TextView
    }

    class ListVH(view: View) : RecyclerView.ViewHolder(view) {
        val ivThumb:           ImageView = view.findViewById(R.id.ivEntryThumbnail)
        val layoutGridThumb:   View      = view.findViewById(R.id.layoutGridThumb)
        val ivThumb1:          ImageView = view.findViewById(R.id.ivThumb1)
        val ivThumb2:          ImageView = view.findViewById(R.id.ivThumb2)
        val ivThumb3:          ImageView = view.findViewById(R.id.ivThumb3)
        val ivThumb4:          ImageView = view.findViewById(R.id.ivThumb4)
        val ivOverlay:         ImageView = view.findViewById(R.id.ivPlayOverlay)
        val tvDuration:        TextView  = view.findViewById(R.id.tvDuration)
        val tvGif:             TextView  = view.findViewById(R.id.tvGifBadge)
        val tvName:            TextView  = view.findViewById(R.id.tvEntryName)
        val tvSub:             TextView  = view.findViewById(R.id.tvEntrySubtext)
        val ivAction:          ImageView = view.findViewById(R.id.ivEntryAction)
        val viewSelectOverlay: View      = view.findViewById(R.id.viewSelectOverlay)
        val ivCheckMark:       ImageView = view.findViewById(R.id.ivCheckMark)
    }

    class GridVH(view: View) : RecyclerView.ViewHolder(view) {
        val ivThumb:           ImageView = view.findViewById(R.id.ivEntryThumbnail)
        val layoutGridThumb:   View      = view.findViewById(R.id.layoutGridThumb)
        val ivThumb1:          ImageView = view.findViewById(R.id.ivThumb1)
        val ivThumb2:          ImageView = view.findViewById(R.id.ivThumb2)
        val ivThumb3:          ImageView = view.findViewById(R.id.ivThumb3)
        val ivThumb4:          ImageView = view.findViewById(R.id.ivThumb4)
        val tvDur:             TextView  = view.findViewById(R.id.tvDuration)
        val tvGif:             TextView  = view.findViewById(R.id.tvGifBadge)
        val tvName:            TextView  = view.findViewById(R.id.tvEntryName)
        val ivAction:          ImageView = view.findViewById(R.id.ivEntryAction)
        val viewSelectOverlay: View      = view.findViewById(R.id.viewSelectOverlay)
        val ivCheckMark:       ImageView = view.findViewById(R.id.ivCheckMark)
    }

    // ── onCreateViewHolder ────────────────────────────────────────────────────
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        fun inflate(res: Int) = LayoutInflater.from(parent.context).inflate(res, parent, false)
        return when (viewType) {
            VT_HEADER -> HeaderVH(inflate(R.layout.item_section_header))
            VT_LIST   -> ListVH(inflate(R.layout.item_history_entry))
            else      -> GridVH(inflate(R.layout.item_history_entry_grid))
        }
    }

    // ── onBindViewHolder ──────────────────────────────────────────────────────
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        when (holder) {

            is HeaderVH -> holder.tv.text = (item as ListItem.Header).title

            is ListVH -> {
                val entry = (item as ListItem.Entry).entry
                val ctx   = holder.itemView.context
                holder.tvName.text = entry.name
                holder.tvSub.text  = entry.subtextFor(ctx)
                holder.ivAction.visibility = if (isMultiSelectMode) View.GONE else View.VISIBLE
                holder.ivAction.setImageResource(android.R.drawable.ic_menu_more)
                holder.ivAction.setOnClickListener(null)

                val isSelected = entry.id in selectedIds
                holder.itemView.setBackgroundColor(
                    if (isMultiSelectMode && isSelected) 0xFFF1F1F1.toInt() else 0x00FFFFFF
                )
                holder.viewSelectOverlay.visibility = if (isMultiSelectMode && isSelected) View.VISIBLE else View.GONE
                holder.ivCheckMark.visibility       = if (isMultiSelectMode && isSelected) View.VISIBLE else View.GONE

                if (entry.isNavigation) {
                    holder.ivThumb.visibility         = View.GONE
                    holder.layoutGridThumb.visibility = View.VISIBLE
                    holder.ivOverlay.visibility       = View.GONE
                    holder.tvDuration.visibility      = View.GONE
                    holder.tvGif.visibility           = View.GONE
                    loadGridThumbnails(ctx, entry.paths,
                        listOf(holder.ivThumb1, holder.ivThumb2, holder.ivThumb3, holder.ivThumb4))
                } else {
                    holder.ivThumb.visibility         = View.VISIBLE
                    holder.layoutGridThumb.visibility = View.GONE
                    holder.tvDuration.visibility      = View.GONE
                    holder.ivThumb.setBackgroundColor(0xFFEEEEEE.toInt())
                    when (entry.primaryType) {
                        EntryType.VIDEO -> {
                            holder.ivOverlay.visibility = View.VISIBLE
                            holder.tvGif.visibility     = View.GONE
                            Glide.with(ctx).asBitmap().load(File(entry.primaryPath))
                                .apply(LIST_VIDEO_OPT).into(holder.ivThumb)
                        }
                        EntryType.IMAGE -> {
                            holder.ivOverlay.visibility = View.GONE
                            holder.tvGif.visibility     = View.GONE
                            Glide.with(ctx).asBitmap().load(File(entry.primaryPath))
                                .apply(LIST_THUMB_OPT).into(holder.ivThumb)
                        }
                        EntryType.GIF -> {
                            holder.ivOverlay.visibility = View.GONE
                            holder.tvGif.visibility     = View.VISIBLE
                            Glide.with(ctx).asGif().load(File(entry.primaryPath))
                                .apply(LIST_THUMB_OPT).into(holder.ivThumb)
                        }
                        else -> {}
                    }
                }

                holder.itemView.setOnClickListener {
                    if (isMultiSelectMode) { toggleSelection(entry.id); notifyItemChanged(position) }
                    else onEntryClick(entry)
                }
                holder.itemView.setOnLongClickListener {
                    if (!isMultiSelectMode) { enterMultiSelectMode(entry.id); notifyDataSetChanged() }
                    true
                }
            }

            is GridVH -> {
                val entry = (item as ListItem.Entry).entry
                val ctx   = holder.itemView.context
                holder.tvName.text = entry.name
                holder.ivAction.visibility = if (isMultiSelectMode) View.GONE else View.VISIBLE
                holder.ivAction.setOnClickListener(null)

                val isSelected = entry.id in selectedIds
                holder.itemView.setBackgroundColor(
                    if (isMultiSelectMode && isSelected) 0xFFF1F1F1.toInt() else 0x00FFFFFF
                )
                holder.viewSelectOverlay.visibility = if (isMultiSelectMode && isSelected) View.VISIBLE else View.GONE
                holder.ivCheckMark.visibility       = if (isMultiSelectMode && isSelected) View.VISIBLE else View.GONE

                if (entry.isNavigation) {
                    holder.ivThumb.visibility         = View.GONE
                    holder.layoutGridThumb.visibility = View.VISIBLE
                    holder.tvDur.visibility           = View.GONE
                    holder.tvGif.visibility           = View.GONE
                    loadGridThumbnails(ctx, entry.paths,
                        listOf(holder.ivThumb1, holder.ivThumb2, holder.ivThumb3, holder.ivThumb4))
                } else {
                    holder.ivThumb.visibility         = View.VISIBLE
                    holder.layoutGridThumb.visibility = View.GONE
                    holder.ivThumb.setBackgroundColor(0xFFEEEEEE.toInt())
                    holder.tvDur.visibility           = View.GONE
                    when (entry.primaryType) {
                        EntryType.VIDEO -> {
                            holder.tvGif.visibility = View.GONE
                            Glide.with(ctx).asBitmap().load(File(entry.primaryPath))
                                .apply(GRID_VIDEO_OPT).into(holder.ivThumb)
                        }
                        EntryType.IMAGE -> {
                            holder.tvGif.visibility = View.GONE
                            Glide.with(ctx).asBitmap().load(File(entry.primaryPath))
                                .apply(GRID_THUMB_OPT).into(holder.ivThumb)
                        }
                        EntryType.GIF -> {
                            holder.tvGif.visibility = View.VISIBLE
                            Glide.with(ctx).asGif().load(File(entry.primaryPath))
                                .apply(GRID_THUMB_OPT).into(holder.ivThumb)
                        }
                        else -> {}
                    }
                }

                holder.itemView.setOnClickListener {
                    if (isMultiSelectMode) { toggleSelection(entry.id); notifyItemChanged(position) }
                    else onEntryClick(entry)
                }
                holder.itemView.setOnLongClickListener {
                    if (!isMultiSelectMode) { enterMultiSelectMode(entry.id); notifyDataSetChanged() }
                    true
                }
            }
        }
    }

    // ── Grid thumbnail loader ─────────────────────────────────────────────────
    private fun loadGridThumbnails(ctx: Context, paths: List<String>, targets: List<ImageView>) {
        targets.forEachIndexed { i, iv ->
            val path = paths.getOrNull(i)
            if (path != null) {
                val ext = path.substringAfterLast('.', "").lowercase()
                if (ext in VIDEO_EXT) {
                    Glide.with(ctx).asBitmap().load(File(path)).apply(GRID_VIDEO_OPT).into(iv)
                } else {
                    Glide.with(ctx).asBitmap().load(File(path)).apply(GRID_THUMB_OPT).into(iv)
                }
            } else {
                Glide.with(ctx).clear(iv)
                iv.setBackgroundColor(0xFFDDDDDD.toInt())
            }
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        when (holder) {
            is ListVH -> {
                Glide.with(holder.ivThumb.context).clear(holder.ivThumb)
                listOf(holder.ivThumb1, holder.ivThumb2, holder.ivThumb3, holder.ivThumb4)
                    .forEach { Glide.with(it.context).clear(it) }
            }
            is GridVH -> {
                Glide.with(holder.ivThumb.context).clear(holder.ivThumb)
                listOf(holder.ivThumb1, holder.ivThumb2, holder.ivThumb3, holder.ivThumb4)
                    .forEach { Glide.with(it.context).clear(it) }
            }
        }
    }
}
