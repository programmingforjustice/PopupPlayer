package nl.blauw.pipplayer;

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import nl.blauw.pipplayer.R
import nl.blauw.pipplayer.FolderItem

// ── Data Model ────────────────────────────────────────────────
// res/model/FolderItem.kt 에 별도 파일로 분리해도 됩니다.
data class FolderItem(
    val name: String,           // 폴더 이름 (예: "DCIM")
    val path: String,           // 절대 경로  (예: "/storage/emulated/0/DCIM")
    val videoCount: Int,        // 포함된 동영상 수
    val subFolderCount: Int,    // 포함된 하위 폴더 수
    val thumbnailPath: String?, // 대표 썸네일 경로 (없으면 null)
    val badgeCount: Int = 0     // 뱃지 숫자 (새 항목 등, 0이면 숨김)
) {
    /** RecyclerView 서브텍스트에 표시할 문자열 */
    fun subtextLabel(): String = buildString {
        if (videoCount > 0) append("$videoCount video's")
        if (videoCount > 0 && subFolderCount > 0) append(", ")
        if (subFolderCount > 0) append("$subFolderCount mappen")
    }
}

// ── DiffUtil Callback ─────────────────────────────────────────
private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<FolderItem>() {
    override fun areItemsTheSame(old: FolderItem, new: FolderItem) = old.path == new.path
    override fun areContentsTheSame(old: FolderItem, new: FolderItem) = old == new
}

// ── Adapter ───────────────────────────────────────────────────
class FolderAdapter(
    private val onFolderClick: (FolderItem) -> Unit
) : ListAdapter<FolderItem, FolderAdapter.FolderViewHolder>(DIFF_CALLBACK) {

    inner class FolderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivThumbnail: ImageView = itemView.findViewById(R.id.ivFolderThumbnail)
        val tvBadge: TextView      = itemView.findViewById(R.id.tvBadge)
        val tvName: TextView       = itemView.findViewById(R.id.tvFolderName)
        val tvSubtext: TextView    = itemView.findViewById(R.id.tvFolderSubtext)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FolderViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_folder, parent, false)
        return FolderViewHolder(view)
    }

    override fun onBindViewHolder(holder: FolderViewHolder, position: Int) {
        val item = getItem(position)

        // 폴더 이름 & 서브텍스트
        holder.tvName.text    = item.name
        holder.tvSubtext.text = item.subtextLabel()

        // 뱃지 (0이면 숨김)
        if (item.badgeCount > 0) {
            holder.tvBadge.visibility = View.VISIBLE
            holder.tvBadge.text = item.badgeCount.toString()
        } else {
            holder.tvBadge.visibility = View.GONE
        }

        holder.ivThumbnail.scaleType = ImageView.ScaleType.CENTER_INSIDE
        holder.ivThumbnail.setImageResource(R.drawable.ic_folder_default)

        // 썸네일 로딩 — Glide 사용 권장
        // thumbnailPath 가 null 이면 기본 폴더 아이콘 표시
        /*if (item.thumbnailPath != null) {
            // TODO: Glide.with(holder.itemView.context)
            //           .load(item.thumbnailPath)
            //           .placeholder(R.drawable.ic_folder_default)
            //           .centerCrop()
            //           .into(holder.ivThumbnail)
        } else {
            holder.ivThumbnail.scaleType = ImageView.ScaleType.CENTER_INSIDE
            holder.ivThumbnail.setImageResource(R.drawable.ic_folder_default)
        }*/

        // 클릭 이벤트
        holder.itemView.setOnClickListener { onFolderClick(item) }
    }
}