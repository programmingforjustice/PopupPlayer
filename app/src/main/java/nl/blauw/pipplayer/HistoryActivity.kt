package nl.blauw.pipplayer

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class HistoryActivity : AppCompatActivity() {

    private lateinit var rvHistory:       RecyclerView
    private lateinit var layoutEmpty:     LinearLayout
    private lateinit var tvItemCount:     TextView
    private lateinit var layoutFilterBar: View
    private lateinit var btnFilterAll:    TextView
    private lateinit var btnFilterVideo:  TextView
    private lateinit var btnFilterImage:  TextView
    private lateinit var multiselectBar:  View
    private lateinit var tvMultiCount:    TextView
    private lateinit var searchBar:       View
    private lateinit var etSearch:        EditText
    private lateinit var historyAdapter:  HistoryAdapter

    private val repo by lazy { HistoryRepository(applicationContext) }

    private var isGridLayout = false

    private enum class FilterType { ALL, VIDEO, IMAGE }
    private var currentFilter = FilterType.ALL

    // ── Lifecycle ─────────────────────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)
        bindViews()
        setupAdapter()
        setupToolbar()
        setupFilterButtons()
        setupMultiselectBar()
        setupSearchBar()
        setupBackPressed()
        loadHistory()
    }

    // ── View binding ──────────────────────────────────────────────────────────
    private fun bindViews() {
        rvHistory       = findViewById(R.id.rvHistory)
        layoutEmpty     = findViewById(R.id.layoutEmpty)
        tvItemCount     = findViewById(R.id.tvItemCount)
        layoutFilterBar = findViewById(R.id.layoutFilterBar)
        btnFilterAll    = findViewById(R.id.btnFilterAll)
        btnFilterVideo  = findViewById(R.id.btnFilterVideo)
        btnFilterImage  = findViewById(R.id.btnFilterImage)
        multiselectBar  = findViewById(R.id.multiselectBar)
        tvMultiCount    = multiselectBar.findViewById(R.id.tvMultiCount)
        searchBar       = findViewById(R.id.searchBar)
        etSearch        = searchBar.findViewById(R.id.etSearch)
    }

    // ── Adapter ───────────────────────────────────────────────────────────────
    private fun setupAdapter() {
        historyAdapter = HistoryAdapter { entry -> playEntry(entry) }
        historyAdapter.onSelectionChanged = { count, _ -> updateMultiselectBar(count) }
        rvHistory.layoutManager = LinearLayoutManager(this)
        rvHistory.adapter = historyAdapter
    }

    // ── Toolbar ───────────────────────────────────────────────────────────────
    private fun setupToolbar() {
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        findViewById<ImageButton>(R.id.btnSearch).setOnClickListener {
            searchBar.visibility = View.VISIBLE
            etSearch.requestFocus()
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                .showSoftInput(etSearch, InputMethodManager.SHOW_IMPLICIT)
        }

        findViewById<ImageButton>(R.id.btnLayoutToggle).setOnClickListener {
            isGridLayout = !isGridLayout
            applyLayoutMode()
        }

        applyLayoutMode()
    }

    // ── Filter ────────────────────────────────────────────────────────────────
    private fun setupFilterButtons() {
        btnFilterAll.setOnClickListener   { currentFilter = FilterType.ALL;   updateFilterButtons(); reapplyFilter() }
        btnFilterVideo.setOnClickListener { currentFilter = FilterType.VIDEO; updateFilterButtons(); reapplyFilter() }
        btnFilterImage.setOnClickListener { currentFilter = FilterType.IMAGE; updateFilterButtons(); reapplyFilter() }
        updateFilterButtons()
    }

    private fun updateFilterButtons() {
        val selBg    = R.drawable.bg_filter_selected
        val unselBg  = android.R.color.transparent
        val selCol   = 0xFFFFFFFF.toInt()
        val unselCol = 0xFF666666.toInt()

        btnFilterAll.setBackgroundResource(if (currentFilter == FilterType.ALL) selBg else unselBg)
        btnFilterAll.setTextColor(if (currentFilter == FilterType.ALL) selCol else unselCol)

        btnFilterVideo.setBackgroundResource(if (currentFilter == FilterType.VIDEO) selBg else unselBg)
        btnFilterVideo.setTextColor(if (currentFilter == FilterType.VIDEO) selCol else unselCol)

        btnFilterImage.setBackgroundResource(if (currentFilter == FilterType.IMAGE) selBg else unselBg)
        btnFilterImage.setTextColor(if (currentFilter == FilterType.IMAGE) selCol else unselCol)
    }

    private fun reapplyFilter() {
        historyAdapter.setFilter(when (currentFilter) {
            FilterType.VIDEO -> HistoryAdapter.Filter.VIDEO
            FilterType.IMAGE -> HistoryAdapter.Filter.IMAGE
            FilterType.ALL   -> HistoryAdapter.Filter.ALL
        })
    }

    // ── Layout mode ───────────────────────────────────────────────────────────
    private fun applyLayoutMode() {
        while (rvHistory.itemDecorationCount > 0) rvHistory.removeItemDecorationAt(0)
        val toggleBtn = findViewById<ImageButton>(R.id.btnLayoutToggle)
        if (isGridLayout) {
            val spanCount = 2
            val gridLM = GridLayoutManager(this, spanCount)
            gridLM.spanSizeLookup = historyAdapter.getSpanSizeLookup(spanCount)
            rvHistory.layoutManager = gridLM
            historyAdapter.isGrid = true
            val outer = (12 * resources.displayMetrics.density).toInt()
            rvHistory.setPadding(outer, 0, outer, 0)
            rvHistory.clipToPadding = false
            rvHistory.addItemDecoration(GridSpacingDecoration(spanCount, 10))
            toggleBtn.setImageResource(R.drawable.ic_layout_list)
        } else {
            rvHistory.layoutManager = LinearLayoutManager(this)
            historyAdapter.isGrid = false
            rvHistory.setPadding(0, 0, 0, 0)
            rvHistory.clipToPadding = true
            toggleBtn.setImageResource(R.drawable.ic_layout_grid)
        }
    }

    // ── Multi-select bar ──────────────────────────────────────────────────────
    private fun setupMultiselectBar() {
        multiselectBar.visibility = View.GONE

        multiselectBar.findViewById<View>(R.id.btnMultiClose).setOnClickListener {
            historyAdapter.exitMultiSelectMode()
        }
        multiselectBar.findViewById<View>(R.id.btnMultiPlay).setOnClickListener {
            historyAdapter.getSelectedEntries().forEach { playEntry(it) }
            historyAdapter.exitMultiSelectMode()
        }
        multiselectBar.findViewById<View>(R.id.btnMultiFavorite).setOnClickListener {
            Toast.makeText(this, "Not available in History", Toast.LENGTH_SHORT).show()
        }
        multiselectBar.findViewById<View>(R.id.btnMultiPlaylist).setOnClickListener {
            Toast.makeText(this, "Not available in History", Toast.LENGTH_SHORT).show()
        }
        multiselectBar.findViewById<View>(R.id.btnMultiShare).setOnClickListener {
            Toast.makeText(this, "Not available in History", Toast.LENGTH_SHORT).show()
        }
        multiselectBar.findViewById<View>(R.id.btnMultiDelete).setOnClickListener {
            val ids = historyAdapter.getSelectedEntries().map { it.id }.toSet()
            historyAdapter.exitMultiSelectMode()
            historyAdapter.removeEntries(ids)
            lifecycleScope.launch(Dispatchers.IO) { ids.forEach { repo.deleteById(it) } }
        }
        multiselectBar.findViewById<View>(R.id.btnMultiMore).setOnClickListener {
            showMoreMenu()
        }
    }

    private fun updateMultiselectBar(count: Int) {
        multiselectBar.visibility = if (count > 0) View.VISIBLE else View.GONE
        if (count > 0) tvMultiCount.text = count.toString()
    }

    private fun showMoreMenu() {
        val dialog    = BottomSheetDialog(this)
        val sheetView = layoutInflater.inflate(R.layout.bottom_sheet_multiselect_menu, null)
        sheetView.findViewById<View>(R.id.menuSelectAll).setOnClickListener {
            dialog.dismiss()
            historyAdapter.selectAll()
        }
        sheetView.findViewById<View>(R.id.menuMoveToFolder).visibility = View.GONE
        sheetView.findViewById<View>(R.id.menuCopyToFolder).visibility = View.GONE
        dialog.setContentView(sheetView)
        dialog.show()
    }

    // ── Search bar ────────────────────────────────────────────────────────────
    private fun setupSearchBar() {
        searchBar.visibility = View.GONE
        searchBar.findViewById<View>(R.id.btnSearchClose).setOnClickListener { closeSearchBar() }
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: Editable?) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                historyAdapter.setSearchQuery(s?.toString() ?: "")
            }
        })
    }

    private fun closeSearchBar() {
        searchBar.visibility = View.GONE
        etSearch.text.clear()
        historyAdapter.setSearchQuery("")
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(etSearch.windowToken, 0)
    }

    // ── Back press ────────────────────────────────────────────────────────────
    private fun setupBackPressed() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = when {
                historyAdapter.isMultiSelectMode     -> historyAdapter.exitMultiSelectMode()
                searchBar.visibility == View.VISIBLE -> closeSearchBar()
                else -> { isEnabled = false; onBackPressedDispatcher.onBackPressed() }
            }
        })
    }

    // ── Data loading ──────────────────────────────────────────────────────────
    private fun loadHistory() {
        lifecycleScope.launch {
            repo.getAll().collect { list ->
                val entries = list.map { HistoryEntry.fromPlayHistory(it) }
                val isEmpty = entries.isEmpty()
                layoutEmpty.visibility = if (isEmpty) View.VISIBLE else View.GONE
                rvHistory.visibility   = if (isEmpty) View.GONE    else View.VISIBLE
                tvItemCount.text       = "${entries.size} items"
                historyAdapter.submitEntries(entries)
            }
        }
    }

    // ── Playback ──────────────────────────────────────────────────────────────
    private fun playEntry(entry: HistoryEntry) {
        if (entry.primaryPath.isEmpty()) return
        val intent = Intent(this, PlayerService::class.java)
        if (entry.isNavigation) {
            intent.putExtra(PlayerService.COMMAND, PlayerService.ACTION_START_PIP_PLAYLIST)
            intent.putStringArrayListExtra(PlayerService.EXTRA_PATHS, ArrayList(entry.paths))
        } else {
            intent.putExtra(PlayerService.COMMAND, PlayerService.ACTION_START_PIP)
            intent.putExtra("data", entry.primaryPath)
        }
        startForegroundService(intent)
    }

    // ── Grid spacing decoration ───────────────────────────────────────────────
    private inner class GridSpacingDecoration(
        private val spanCount: Int,
        private val spacingDp: Int
    ) : RecyclerView.ItemDecoration() {

        private val sp: Int get() = (spacingDp * resources.displayMetrics.density).toInt()

        override fun getItemOffsets(
            outRect: android.graphics.Rect,
            view: View,
            parent: RecyclerView,
            state: RecyclerView.State
        ) {
            val pos = parent.getChildAdapterPosition(view)
            if (pos == RecyclerView.NO_ID.toInt()) return
            if (historyAdapter.getItemViewType(pos) == 0) { outRect.set(0, 0, 0, sp); return }
            val half = sp / 2
            outRect.left = half; outRect.right = half; outRect.top = 0; outRect.bottom = sp
        }
    }
}
