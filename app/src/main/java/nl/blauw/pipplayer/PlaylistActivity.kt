package nl.blauw.pipplayer

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
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
        // TODO: 플레이리스트 삭제 / 이름 변경 메뉴
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