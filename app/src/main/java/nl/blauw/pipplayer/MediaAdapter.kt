package nl.blauw.pipplayer

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import nl.blauw.pipplayer.databinding.ItemFileBinding

class MediaAdapter(private val items: List<MediaItem>) :
    RecyclerView.Adapter<MediaAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemFileBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(mediaItem: MediaItem) {
            binding.itemName.text = mediaItem.displayName
            // 필요시, 미디어 타입에 따라 아이콘이나 썸네일 로딩 등을 추가할 수 있습니다.
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemFileBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size
}