package nl.blauw.pipplayer

import android.Manifest
import android.content.ContentUris
import android.content.pm.PackageManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.webkit.MimeTypeMap
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

class MainActivity : AppCompatActivity() {

    // ── 지원 확장자 ───────────────────────────────────────────
    private val VIDEO_EXT = setOf("mp4","mkv","avi","mov","wmv","flv","webm","3gp","m4v","ts")
    private val IMAGE_EXT = setOf("jpg","jpeg","png","webp","bmp","heic","heif")
    private val GIF_EXT   = setOf("gif")

    // ── 뷰 ───────────────────────────────────────────────────
    private lateinit var tvTitle: TextView
    private lateinit var tvBreadcrumb: TextView
    private lateinit var tvItemCount: TextView
    private lateinit var layoutBreadcrumb: View
    private lateinit var layoutBreadcrumbDivider: View
    private lateinit var layoutFilterBar: View
    private lateinit var btnFilterAll: TextView
    private lateinit var btnFilterVideo: TextView
    private lateinit var btnFilterImage: TextView
    private lateinit var layoutFilterBarDivider: View
    private lateinit var layoutEmpty: LinearLayout
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var rvFolders: RecyclerView
    private lateinit var btnBack: ImageButton
    private lateinit var btnSort: ImageButton
    private lateinit var bottomNav: BottomNavigationView

    // ── 필터 상태 ─────────────────────────────────────────────
    private enum class FilterType { ALL, VIDEO, IMAGE }
    private var currentFilter = FilterType.ALL

    // ── Adapter ───────────────────────────────────────────────
    // 최상위(루트 폴더 목록)용 Adapter
    private lateinit var rootFolderAdapter: FolderAdapter
    // 탐색 중(파일/하위폴더 목록)용 Adapter
    private lateinit var fileListAdapter: FileListAdapter

    // ── 탐색 상태 ────────────────────────────────────────────
    // folderStack 이 비어있으면 → 루트 화면
    // 비어있지 않으면 → 파일 탐색기 화면 (stack.last() 가 현재 폴더)
    // folderStack: Pair<File, bucketId>  (-1L = 하위폴더 직접 진입, BUCKET_ID 미확보)
    private val folderStack = ArrayDeque<Pair<File, Long>>()

    // 경로별 LayoutManager 상태 저장 (스크롤 위치 복원용)
    // 루트 화면은 KEY_ROOT 를 키로 사용
    private val layoutManagerStateMap = HashMap<String, android.os.Parcelable?>()
    
    
    private companion object {
        const val KEY_ROOT   = "__root__"
        const val FIRST_CHUNK_SIZE = 50          // 미디어 파일 청크 단위
    }

    // 정렬 상태
    private enum class SortOrder {
        NAME_ASC, NAME_DESC,
        DATE_NEWEST, DATE_OLDEST,
        SIZE_LARGEST, SIZE_SMALLEST
    }
    private var currentSort = SortOrder.DATE_NEWEST

    // ── 삭제 요청 런처 ────────────────────────────────────────
    private val deleteRequestLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()
        ) {
            // 영구 삭제 완료 → 해당 항목만 즉시 제거
            if (pendingDeletePaths.isNotEmpty()) {
                fileListAdapter.removeEntries(pendingDeletePaths)
                pendingDeletePaths = emptySet()
            }
        }

    // ── 권한 요청 ─────────────────────────────────────────────
        private val permissionLauncher: ActivityResultLauncher<Array<String>> =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { grants: Map<String, Boolean> ->
            if (grants.values.all { it }) loadRoot()
            else Toast.makeText(this, "저장소 권한이 필요합니다.", Toast.LENGTH_LONG).show()
        }

    // ── onCreate ─────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bindViews()
        setupAdapters()
        setupMultiselectBar()
        setupSearchBar()
        setupToolbar()
        setupCategories()
        setupBottomNav()
        setupBackPressed()
        checkPermissionsAndLoad()
    }

    // ── 뷰 바인딩 ─────────────────────────────────────────────
    private fun bindViews() {
        tvTitle          = findViewById(R.id.tvTitle)
        tvBreadcrumb     = findViewById(R.id.tvBreadcrumb)
        tvItemCount      = findViewById(R.id.tvItemCount)
        layoutBreadcrumb = findViewById(R.id.layoutBreadcrumb)
        layoutBreadcrumbDivider = findViewById(R.id.layoutBreadcrumbDivider)
        layoutFilterBar  = findViewById(R.id.layoutFilterBar)
        layoutFilterBarDivider  = findViewById(R.id.layoutFilterBarDivider)
        btnFilterAll     = findViewById(R.id.btnFilterAll)
        btnFilterVideo   = findViewById(R.id.btnFilterVideo)
        btnFilterImage   = findViewById(R.id.btnFilterImage)
        layoutEmpty      = findViewById(R.id.layoutEmpty)
        swipeRefresh     = findViewById(R.id.swipeRefresh)
        rvFolders        = findViewById(R.id.rvFolders)
        btnBack          = findViewById(R.id.btnBack)
        btnSort          = findViewById(R.id.btnSort)
        btnFilterToggle  = findViewById(R.id.btnFilterToggle)
        bottomNav        = findViewById(R.id.bottomNav)
    }

    // ── Adapter 초기화 ────────────────────────────────────────
    private fun setupAdapters() {
        // 루트 폴더 목록용
        rootFolderAdapter = FolderAdapter { folder ->
            saveScrollPosition()
            navigateTo(File(folder.path), folder.bucketId)
        }

        // 파일 탐색용
        fileListAdapter = FileListAdapter(
            onDirectoryClick = { entry -> navigateTo(entry.file, -1L) },
            onFileClick      = { entry -> openMediaFile(entry) }
        )

        // 멀티셀렉트 선택 변경 콜백
        fileListAdapter.onSelectionChanged = { count, _ ->
            updateMultiselectBar(count)
        }

        rvFolders.layoutManager = LinearLayoutManager(this)
        // 처음엔 루트 Adapter 연결
        rvFolders.adapter = rootFolderAdapter

        swipeRefresh.setOnRefreshListener {
            if (folderStack.isEmpty()) {
                loadRoot()
            } else {
                val (dir, bucketId) = folderStack.last()
                loadDirectory(dir, bucketId, restoreScroll = false)
            }
        }
    }

    // ── 멀티셀렉트 바 ─────────────────────────────────────────
    private lateinit var multiselectBar: View
    private lateinit var tvMultiCount: TextView

    // ── 검색 바 ───────────────────────────────────────────────
    private lateinit var searchBar: View
    private lateinit var etSearch: EditText

    private fun setupMultiselectBar() {
        multiselectBar = findViewById(R.id.multiselectBar)
        tvMultiCount   = multiselectBar.findViewById(R.id.tvMultiCount)

        // 닫기
        multiselectBar.findViewById<View>(R.id.btnMultiClose).setOnClickListener {
            fileListAdapter.exitMultiSelectMode()
        }
        // Play
        multiselectBar.findViewById<View>(R.id.btnMultiPlay).setOnClickListener {
            val selected = fileListAdapter.getSelectedEntries()
            if (selected.isEmpty()) return@setOnClickListener
            val paths = ArrayList(selected.map { it.path })
            val intent = Intent(this, PlayerService::class.java).apply {
                putExtra(PlayerService.COMMAND, PlayerService.ACTION_START_PIP_PLAYLIST)
                putStringArrayListExtra(PlayerService.EXTRA_PATHS, paths)
            }
            startForegroundService(intent)
            fileListAdapter.exitMultiSelectMode()
        }
        // 즐겨찾기
        multiselectBar.findViewById<View>(R.id.btnMultiFavorite).setOnClickListener {
            Toast.makeText(this, "${fileListAdapter.getSelectedEntries().size}개 즐겨찾기 추가", Toast.LENGTH_SHORT).show()
            fileListAdapter.exitMultiSelectMode()
        }
        // 플레이리스트
        multiselectBar.findViewById<View>(R.id.btnMultiPlaylist).setOnClickListener {
            val selected = fileListAdapter.getSelectedEntries()
            if (selected.isEmpty()) return@setOnClickListener
            val paths = selected.map { it.path }
            MultiPlaylistBottomSheet.newInstance(paths)
                .show(supportFragmentManager, MultiPlaylistBottomSheet.TAG)
            fileListAdapter.exitMultiSelectMode()
        }
        // 공유
        multiselectBar.findViewById<View>(R.id.btnMultiShare).setOnClickListener {
            val selected = fileListAdapter.getSelectedEntries()
            if (selected.isEmpty()) return@setOnClickListener
            val uris = ArrayList(selected.map { android.net.Uri.fromFile(it.file) })
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND_MULTIPLE).apply {
                type = "video/*"
                putParcelableArrayListExtra(android.content.Intent.EXTRA_STREAM, uris)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(android.content.Intent.createChooser(intent, "공유"))
            fileListAdapter.exitMultiSelectMode()
        }
        // 삭제
        multiselectBar.findViewById<View>(R.id.btnMultiDelete).setOnClickListener {
            val selected = fileListAdapter.getSelectedEntries()
            if (selected.isEmpty()) return@setOnClickListener
            showDeleteConfirmDialog(selected)
        }
        // 추가 메뉴
        multiselectBar.findViewById<View>(R.id.btnMultiMore).setOnClickListener {
            showMultiselectMoreMenu()
        }
    }

    private fun showDeleteConfirmDialog(selected: List<FileEntry>) {
        val dialog    = android.app.Dialog(this)
        val sheetView = layoutInflater.inflate(R.layout.dialog_delete_confirm, null)
        dialog.setContentView(sheetView)
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.88).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                                    )

        // 타이틀 / 설명 — 단일 vs 다중
        val tvTitle = sheetView.findViewById<TextView>(R.id.tvDeleteTitle)
        val tvDesc  = sheetView.findViewById<TextView>(R.id.tvDeleteDesc)
        val tvName  = sheetView.findViewById<TextView>(R.id.tvPreviewName)
        val ivThumb = sheetView.findViewById<ImageView>(R.id.ivPreviewThumb)

        if (selected.size == 1) {
            tvTitle.text = "Delete the following item"
            tvDesc.text  = "This item will be moved to Recycle Bin & kept for 30 days before being permanently deleted from your device."
            tvName.text  = selected.first().name
            // 썸네일 로딩
            Glide.with(ivThumb)
                .asBitmap()
                .load(selected.first().file)
                .apply(RequestOptions().frame(1_000_000L).transform(
                    com.bumptech.glide.load.resource.bitmap.CenterCrop()))
                .into(ivThumb)
        } else {
            tvTitle.text = "Delete the following items"
            tvDesc.text  = "These items will be moved to Recycle Bin & kept for 30 days before being permanently deleted from your device."
            tvName.text  = "${selected.size} Items"
            ivThumb.setImageResource(android.R.drawable.ic_menu_slideshow)
        }

        sheetView.findViewById<View>(R.id.btnDialogClose).setOnClickListener { dialog.dismiss() }

        // 영구 삭제
        sheetView.findViewById<View>(R.id.btnPermanentDelete).setOnClickListener {
            dialog.dismiss()
            permanentlyDeleteFiles(selected)
        }

        // 휴지통으로 이동
        sheetView.findViewById<View>(R.id.btnMoveToRecycleBin).setOnClickListener {
            dialog.dismiss()
            moveToRecycleBin(selected)
        }

        dialog.show()
        fileListAdapter.exitMultiSelectMode()
    }

    /** 영구 삭제 — MediaStore.createDeleteRequest */
    private fun permanentlyDeleteFiles(selected: List<FileEntry>) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            lifecycleScope.launch {
                val uris = withContext(Dispatchers.IO) { resolveContentUris(selected) }
                if (uris.isEmpty()) return@launch
                pendingDeletePaths = selected.map { it.path }.toSet()
                val pi = android.provider.MediaStore.createDeleteRequest(contentResolver, uris)
                deleteRequestLauncher.launch(
                    androidx.activity.result.IntentSenderRequest.Builder(pi.intentSender).build()
                )
            }
        }
    }

    /** 휴지통으로 이동 — MediaStore.createTrashRequest */
    private fun moveToRecycleBin(selected: List<FileEntry>) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            lifecycleScope.launch {
                val uris = withContext(Dispatchers.IO) { resolveContentUris(selected) }
                if (uris.isEmpty()) return@launch
                pendingDeletePaths = selected.map { it.path }.toSet()
                val pi = android.provider.MediaStore.createTrashRequest(contentResolver, uris, true)
                trashRequestLauncher.launch(
                    androidx.activity.result.IntentSenderRequest.Builder(pi.intentSender).build()
                )
            }
        }
    }

    /** FileEntry → MediaStore contentUri 변환 */
    private fun resolveContentUris(entries: List<FileEntry>): List<android.net.Uri> =
        entries.mapNotNull { entry ->
            contentResolver.query(
                android.provider.MediaStore.Files.getContentUri("external"),
                arrayOf(android.provider.MediaStore.Files.FileColumns._ID,
                        android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE),
                "${android.provider.MediaStore.Files.FileColumns.DATA} = ?",
                arrayOf(entry.path), null
            )?.use { c ->
                if (c.moveToFirst()) {
                    val id   = c.getLong(0)
                    val type = c.getInt(1)
                    val base = when (type) {
                        android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO ->
                            android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                        android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE ->
                            android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                        else ->
                            android.provider.MediaStore.Files.getContentUri("external")
                    }
                    android.content.ContentUris.withAppendedId(base, id)
                } else null
            }
        }

    private var pendingDeletePaths  = emptySet<String>()
    private var pendingPickerMode   = ""
    private var pendingPickerPaths  = emptyList<String>()

    // ── SAF 폴더 선택 런처 ────────────────────────────────────
    private val folderPickerLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { treeUri ->
            if (treeUri == null) return@registerForActivityResult
            // 영속 권한 획득
            contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            performCopyMove(treeUri, pendingPickerPaths, pendingPickerMode)
        }

    private val trashRequestLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()
        ) {
            // 휴지통 이동 완료 → 해당 항목만 즉시 제거
            if (pendingDeletePaths.isNotEmpty()) {
                fileListAdapter.removeEntries(pendingDeletePaths)
                pendingDeletePaths = emptySet()
            }
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
        val sheetView = layoutInflater.inflate(R.layout.bottom_sheet_multiselect_menu, null)

        sheetView.findViewById<View>(R.id.menuSelectAll).setOnClickListener {
            dialog.dismiss()
            fileListAdapter.selectAll()
        }
        sheetView.findViewById<View>(R.id.menuMoveToFolder).setOnClickListener {
            dialog.dismiss()
            pendingPickerPaths = fileListAdapter.getSelectedEntries().map { it.path }
            pendingPickerMode  = "MOVE"
            fileListAdapter.exitMultiSelectMode()
            folderPickerLauncher.launch(null)
        }
        sheetView.findViewById<View>(R.id.menuCopyToFolder).setOnClickListener {
            dialog.dismiss()
            pendingPickerPaths = fileListAdapter.getSelectedEntries().map { it.path }
            pendingPickerMode  = "COPY"
            fileListAdapter.exitMultiSelectMode()
            folderPickerLauncher.launch(null)
        }

        dialog.setContentView(sheetView)
        dialog.show()
    }

    // ── SAF 복사/이동 실행 ────────────────────────────────────
    private fun performCopyMove(treeUri: Uri, paths: List<String>, mode: String) {
        val docId  = DocumentsContract.getTreeDocumentId(treeUri)
        val dirUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)

        lifecycleScope.launch {
            val succeeded = mutableListOf<String>()
            val failed    = mutableListOf<String>()

            withContext(Dispatchers.IO) {
                for (path in paths) {
                    val srcFile = File(path)
                    try {
                        val ext      = srcFile.extension.lowercase()
                        val mimeType = MimeTypeMap.getSingleton()
                            .getMimeTypeFromExtension(ext) ?: "application/octet-stream"

                        // SAF로 목적지 파일 생성 (이름은 확장자 없이 전달 — 공급자가 MIME로 확장자 추가)
                        val newDocUri = DocumentsContract.createDocument(
                            contentResolver, dirUri, mimeType, srcFile.nameWithoutExtension
                        ) ?: throw IOException("대상 파일 생성 실패")

                        // 소스 읽기 (scoped storage 대응: content URI 우선)
                        val srcUri = resolveSingleContentUri(path)
                        val input  = if (srcUri != null) contentResolver.openInputStream(srcUri)
                                     else srcFile.inputStream()
                        checkNotNull(input) { "소스를 열 수 없음" }

                        input.use { i ->
                            (contentResolver.openOutputStream(newDocUri)
                                ?: throw IOException("출력 스트림 열기 실패"))
                                .use { o -> i.copyTo(o) }
                        }

                        if (mode == "MOVE") {
                            if (srcUri != null) {
                                runCatching { contentResolver.delete(srcUri, null, null) }
                                    .onFailure { srcFile.delete() }
                            } else {
                                srcFile.delete()
                            }
                            fileListAdapter.removeEntries(setOf(path))
                        }

                        succeeded.add(srcFile.name)
                    } catch (e: Exception) {
                        failed.add(srcFile.name)
                        android.util.Log.e("MainActivity", "CopyMove 실패: ${srcFile.name}", e)
                    }
                }
            }

            if (succeeded.isNotEmpty()) {
                Toast.makeText(this@MainActivity, succeeded.joinToString(", "), Toast.LENGTH_LONG).show()
            }
            if (failed.isNotEmpty()) {
                Toast.makeText(this@MainActivity, "실패: ${failed.joinToString(", ")}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun resolveSingleContentUri(path: String): Uri? =
        contentResolver.query(
            MediaStore.Files.getContentUri("external"),
            arrayOf(MediaStore.Files.FileColumns._ID, MediaStore.Files.FileColumns.MEDIA_TYPE),
            "${MediaStore.Files.FileColumns.DATA} = ?",
            arrayOf(path), null
        )?.use { c ->
            if (!c.moveToFirst()) return@use null
            val id   = c.getLong(0)
            val type = c.getInt(1)
            val base = when (type) {
                MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                else -> MediaStore.Files.getContentUri("external")
            }
            ContentUris.withAppendedId(base, id)
        }

    // ── 검색 바 ───────────────────────────────────────────────

    private fun setupSearchBar() {
        searchBar = findViewById(R.id.searchBar)
        etSearch  = searchBar.findViewById(R.id.etSearch)

        findViewById<ImageButton>(R.id.btnSearch).setOnClickListener {
            searchBar.visibility = View.VISIBLE
            etSearch.requestFocus()
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(etSearch, InputMethodManager.SHOW_IMPLICIT)
        }

        searchBar.findViewById<View>(R.id.btnSearchClose).setOnClickListener {
            closeSearchBar()
        }

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: Editable?) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString() ?: ""
                if (folderStack.isEmpty()) rootFolderAdapter.setSearchQuery(query)
                else fileListAdapter.setSearchQuery(query)
            }
        })
    }

    private fun closeSearchBar() {
        searchBar.visibility = View.GONE
        etSearch.text.clear()
        rootFolderAdapter.setSearchQuery("")
        fileListAdapter.setSearchQuery("")
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(etSearch.windowToken, 0)
    }

    // ── 레이아웃 상태 ─────────────────────────────────────────
    private var isGridLayout = false
    private var showFoldersMode = true
    private lateinit var btnFilterToggle: ImageButton

    // ── 툴바 버튼 ─────────────────────────────────────────────
    private fun setupToolbar() {
        btnBack.setOnClickListener { navigateUp() }

        btnSort.setOnClickListener {
            currentSort = when (currentSort) {
                SortOrder.NAME_ASC      -> SortOrder.NAME_DESC
                SortOrder.NAME_DESC     -> SortOrder.DATE_NEWEST
                SortOrder.DATE_NEWEST   -> SortOrder.DATE_OLDEST
                SortOrder.DATE_OLDEST   -> SortOrder.SIZE_LARGEST
                SortOrder.SIZE_LARGEST  -> SortOrder.SIZE_SMALLEST
                SortOrder.SIZE_SMALLEST -> SortOrder.NAME_ASC
            }
            updateSortButton()
            Toast.makeText(this, sortLabel(), Toast.LENGTH_SHORT).show()
            if (!showFoldersMode) {
                loadAllMedia()
            } else if (folderStack.isNotEmpty()) {
                val (dir, bucketId) = folderStack.last()
                loadDirectory(dir, bucketId, restoreScroll = false)
            }
        }

        setupFilterButtons()

        btnFilterToggle.setOnClickListener {
            showFoldersMode = !showFoldersMode
            updateFilterToggleButton()
            folderStack.clear()
            showRootScreen()
            if (showFoldersMode) loadRootFolders()
        }

        findViewById<ImageButton>(R.id.btnLayoutToggle).setOnClickListener {
            isGridLayout = !isGridLayout
            applyLayoutMode()
        }

        updateSortButton()
        applyLayoutMode()
    }

    private fun setupFilterButtons() {
        btnFilterAll.setOnClickListener {
            currentFilter = FilterType.ALL
            updateFilterButtons()
            reapplyFilter()
        }
        btnFilterVideo.setOnClickListener {
            currentFilter = FilterType.VIDEO
            updateFilterButtons()
            reapplyFilter()
        }
        btnFilterImage.setOnClickListener {
            currentFilter = FilterType.IMAGE
            updateFilterButtons()
            reapplyFilter()
        }
    }

    /** 현재 필터 상태에 따라 버튼 UI 갱신 */
    private fun updateFilterButtons() {
        val selectedBg   = R.drawable.bg_filter_selected
        val unselectedBg = android.R.color.transparent
        val selectedColor   = 0xFF3F7FD8.toInt()
        val unselectedColor = 0xFF666666.toInt()

        btnFilterAll.setBackgroundResource(
            if (currentFilter == FilterType.ALL) selectedBg else unselectedBg)
        btnFilterAll.setTextColor(
            if (currentFilter == FilterType.ALL) selectedColor else unselectedColor)

        btnFilterVideo.setBackgroundResource(
            if (currentFilter == FilterType.VIDEO) selectedBg else unselectedBg)
        btnFilterVideo.setTextColor(
            if (currentFilter == FilterType.VIDEO) selectedColor else unselectedColor)

        btnFilterImage.setBackgroundResource(
            if (currentFilter == FilterType.IMAGE) selectedBg else unselectedBg)
        btnFilterImage.setTextColor(
            if (currentFilter == FilterType.IMAGE) selectedColor else unselectedColor)
    }

    /** 필터 변경 시 현재 목록에 필터 재적용 */
    private fun reapplyFilter() {
        val adapterFilter = when (currentFilter) {
            FilterType.VIDEO -> FileListAdapter.Filter.VIDEO
            FilterType.IMAGE -> FileListAdapter.Filter.IMAGE
            FilterType.ALL   -> FileListAdapter.Filter.ALL
        }
        fileListAdapter.setFilter(adapterFilter)
    }

    private fun applyLayoutMode() {
        val toggleBtn = findViewById<ImageButton>(R.id.btnLayoutToggle)
        // 기존 decoration 제거
        while (rvFolders.itemDecorationCount > 0) {
            rvFolders.removeItemDecorationAt(0)
        }

        if (isGridLayout) {
            val spanCount = 2
            val gridLM    = androidx.recyclerview.widget.GridLayoutManager(this, spanCount)
            gridLM.spanSizeLookup = fileListAdapter.getSpanSizeLookup(spanCount)
            rvFolders.layoutManager = gridLM
            fileListAdapter.isGrid  = true
            // 외부 좌우 패딩 + 아이템 간격
            val outerPx = (12 * resources.displayMetrics.density).toInt()
            rvFolders.setPadding(outerPx, 0, outerPx, 0)
            rvFolders.clipToPadding = false
            rvFolders.addItemDecoration(GridSpacingDecoration(spanCount, spacingDp = 10))
            toggleBtn.setImageResource(R.drawable.ic_layout_list)
        } else {
            rvFolders.layoutManager = LinearLayoutManager(this)
            fileListAdapter.isGrid  = false
            rvFolders.setPadding(0, 0, 0, 0)
            rvFolders.clipToPadding = true
            toggleBtn.setImageResource(R.drawable.ic_layout_grid)
        }
    }

    private fun updateSortButton() {
        val iconRes = when (currentSort) {
            SortOrder.NAME_ASC      -> R.drawable.ic_sort_name_asc
            SortOrder.NAME_DESC     -> R.drawable.ic_sort_name_desc
            SortOrder.DATE_NEWEST   -> R.drawable.ic_sort_date_newest
            SortOrder.DATE_OLDEST   -> R.drawable.ic_sort_date_oldest
            SortOrder.SIZE_LARGEST  -> R.drawable.ic_sort_size_largest
            SortOrder.SIZE_SMALLEST -> R.drawable.ic_sort_size_smallest
        }
        btnSort.setImageResource(iconRes)
    }

    private fun sortLabel() = when (currentSort) {
        SortOrder.NAME_ASC  -> "이름 오름차순"
        SortOrder.NAME_DESC -> "이름 내림차순"
        SortOrder.DATE_OLDEST -> "오래된 순"
        SortOrder.DATE_NEWEST -> "최신 순"
        SortOrder.SIZE_SMALLEST -> "크기 작은 순"
        SortOrder.SIZE_LARGEST -> "크기 큰 순"
    }

    // ── 카테고리 클릭 ─────────────────────────────────────────
    private fun setupCategories() {
        findViewById<View>(R.id.categoryPlaylists).setOnClickListener {
            startActivity(Intent(this, PlaylistActivity::class.java))
        }

        findViewById<View>(R.id.categoryPlayHistory).setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }

        findViewById<View>(R.id.categoryCleaner).setOnClickListener {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                startActivity(Intent(this, RecycleBinActivity::class.java))
            } else {
                Toast.makeText(this, "Android 11 이상에서 지원됩니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }
    private fun setupBottomNav() {
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_local -> { /* 현재 탭 */ true }
                R.id.nav_music -> { Toast.makeText(this, "Muziek", Toast.LENGTH_SHORT).show(); true }
                R.id.nav_me    -> { Toast.makeText(this, "Ik", Toast.LENGTH_SHORT).show(); true }
                else -> false
            }
        }
    }

    // ── 뒤로가기 처리 ─────────────────────────────────────────
    private fun setupBackPressed() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (fileListAdapter.isMultiSelectMode) {
                    fileListAdapter.exitMultiSelectMode()
                    return
                }
                if (searchBar.visibility == View.VISIBLE) {
                    closeSearchBar()
                    return
                }
                if (!navigateUp()) {
                    // 루트에서 뒤로가기 → 앱 종료
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    /**
     * 상위 폴더로 이동.
     * @return 이동 성공 여부 (false 면 이미 루트)
     */
    private fun navigateUp(): Boolean {
        if (folderStack.isEmpty()) return false
        clearSearchQuery()
        folderStack.removeLast()
        if (folderStack.isEmpty()) {
            showRootScreen(restoreScroll = true)
        } else {
            // ✅ Fix: Pair<File,Long>에서 file, bucketId 분리
            val (dir, bucketId) = folderStack.last()
            loadDirectory(dir, bucketId, restoreScroll = true)
        }
        return true
    }

    // ── 권한 체크 ─────────────────────────────────────────────

    /**
     * 권한 요청 순서:
     * 1) SYSTEM_ALERT_WINDOW (오버레이) — Settings 화면 이동 방식
     * 2) 저장소 권한 — 런타임 권한 요청
     * 두 권한이 모두 허용되어야 loadRootFolders() 실행
     */
    private fun checkPermissionsAndLoad() {
        if (!hasOverlayPermission()) {
            requestOverlayPermission()   // 오버레이 먼저 요청
        } else {
            checkStoragePermissionAndLoad()
        }
    }

    // ── 오버레이 권한 ─────────────────────────────────────────

    private fun hasOverlayPermission(): Boolean =
        Settings.canDrawOverlays(this)

    private fun requestOverlayPermission() {
        Toast.makeText(this, "팝업 표시를 위해 '다른 앱 위에 표시' 권한이 필요합니다.", Toast.LENGTH_LONG).show()
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        overlayPermissionLauncher.launch(intent)
    }

    // Settings 화면에서 돌아왔을 때 결과 확인
    private val overlayPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (hasOverlayPermission()) {
                checkStoragePermissionAndLoad()
            } else {
                Toast.makeText(this, "'다른 앱 위에 표시' 권한이 거부되었습니다.", Toast.LENGTH_LONG).show()
            }
        }

    // ── 저장소 권한 ───────────────────────────────────────────

    private fun checkStoragePermissionAndLoad() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        val allGranted = permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) loadRoot() else permissionLauncher.launch(permissions)
    }

    // ─────────────────────────────────────────────────────────
    // 루트 화면 (기존 메인 폴더 목록)
    // ─────────────────────────────────────────────────────────

    /**
     * 점진적 로딩:
     * 1) BUCKET 쿼리로 폴더 목록을 즉시 표시 (썸네일 없이)
     * 2) Flow 가 폴더별 썸네일을 emit 할 때마다 해당 항목만 갱신
     *
     * 사용자는 쿼리 완료를 기다리지 않고 즉시 폴더 목록을 볼 수 있음.
     */
    private fun loadRootFolders() {
        lifecycleScope.launch {
            val currentMap = LinkedHashMap<String, FolderItem>()

            scanMediaFoldersFlow().collect { updated ->
                currentMap[updated.path] = updated
                rootFolderAdapter.submitSearchableList(
                    currentMap.values.sortedBy { it.name.lowercase() }
                )
            }
            swipeRefresh.isRefreshing = false
        }
    }

    private fun showRootScreen(restoreScroll: Boolean = false) {
        tvTitle.text                = "Video's"
        layoutBreadcrumb.visibility = View.GONE
        layoutBreadcrumbDivider.visibility = View.GONE
        btnBack.visibility          = View.GONE
        currentFilter               = FilterType.ALL

        if (showFoldersMode) {
            layoutFilterBar.visibility = View.GONE
            layoutFilterBarDivider.visibility = View.GONE
            if (rvFolders.adapter !== rootFolderAdapter) rvFolders.adapter = rootFolderAdapter
            layoutEmpty.visibility = View.GONE
            rvFolders.visibility   = View.VISIBLE
            if (restoreScroll) restoreScrollPosition(KEY_ROOT)
        } else {
            layoutFilterBar.visibility = View.VISIBLE
            layoutFilterBarDivider.visibility = View.VISIBLE
            updateFilterButtons()
            loadAllMedia()
        }
    }

    // ─────────────────────────────────────────────────────────
    // 디렉토리 탐색 화면
    // ─────────────────────────────────────────────────────────

    /** 새 폴더로 이동 (스택에 push) */
    private fun navigateTo(folder: File, bucketId: Long) {
        if (folderStack.isNotEmpty()) saveScrollPosition()
        clearSearchQuery()
        folderStack.addLast(folder to bucketId)
        loadDirectory(folder, bucketId, restoreScroll = false)
    }

    private fun clearSearchQuery() {
        if (!::etSearch.isInitialized) return
        etSearch.text.clear()
        rootFolderAdapter.setSearchQuery("")
        fileListAdapter.setSearchQuery("")
    }

    /**
     * 디렉토리 내용을 RecyclerView 에 표시.
     *
     * ── 블로킹 원인 해결 전략 ──────────────────────────────────
     *
     * 기존 문제:
     *   CHUNK_SIZE(50)개마다 collect → 전체 currentMap 재정렬 → submitList()
     *   → 수백 개 파일 폴더에서 매 청크마다 메인 스레드에서 DiffUtil 실행
     *   → 누적 비용으로 메인 스레드 수십 초 블로킹
     *
     * 해결:
     *   1) 정렬 및 집계(currentMap 병합)를 IO 스레드에서 수행 (withContext(Dispatchers.IO))
     *   2) emit 을 2단계로 분리:
     *      - 1차 emit: 하위 디렉토리 + 첫 FIRST_CHUNK_SIZE 개 파일 → 빠르게 화면 표시
     *      - 2차 emit: 나머지 파일 전체 → 스캔 완료 후 1회만 submitList()
     *      → DiffUtil 실행 횟수를 최대 2회로 제한
     *   3) submitList() 를 withContext(Dispatchers.Main) 없이 직접 호출
     *      (collect 는 이미 메인 스레드에서 실행되므로 별도 전환 불필요)
     */
    private fun loadDirectory(folder: File, bucketId: Long, restoreScroll: Boolean = false) {
        tvTitle.text                = folder.name
        btnBack.visibility          = View.VISIBLE
        layoutBreadcrumb.visibility = View.VISIBLE
        layoutBreadcrumbDivider.visibility = View.VISIBLE
        layoutFilterBar.visibility = View.VISIBLE
        layoutFilterBarDivider.visibility = View.VISIBLE
        updateBreadcrumb()

        // 새 폴더 진입 시 필터 초기화
        if (!restoreScroll) {
            currentFilter = FilterType.ALL
            updateFilterButtons()
        }

        if (rvFolders.adapter !== fileListAdapter) rvFolders.adapter = fileListAdapter
        if (!restoreScroll) fileListAdapter.submitEntries(emptyList())

        lifecycleScope.launch {
            // ── IO 스레드에서 전체 항목 수집 및 정렬 수행 ──────────────
            //    메인 스레드에는 최종 정렬된 List 만 전달
            scanDirectoryFlow(folder, bucketId).collect { (isPartial, entries) ->
                val visible   = if (showFoldersMode) entries
                                else entries.filter { it.type != EntryType.DIRECTORY }
                val isEmpty   = visible.isEmpty()
                layoutEmpty.visibility = if (isEmpty) View.VISIBLE else View.GONE
                rvFolders.visibility   = if (isEmpty) View.GONE    else View.VISIBLE

                val dirCount  = visible.count { it.type == EntryType.DIRECTORY }
                val fileCount = visible.size - dirCount
                tvItemCount.text = if (isPartial) "폴더 ${dirCount}개  •  파일 ${fileCount}개+"
                                   else           "폴더 ${dirCount}개  •  파일 ${fileCount}개"

                if (restoreScroll) {
                    fileListAdapter.submitEntries(visible)
                    if (!isPartial) {
                        rvFolders.post { restoreScrollPosition(folder.absolutePath) }
                    }
                } else {
                    fileListAdapter.submitEntries(visible)
                }
            }

            if (!restoreScroll) rvFolders.scrollToPosition(0)
            swipeRefresh.isRefreshing = false
        }
    }

    /**
     * 디렉토리 내부 항목을 Flow 로 emit.
     *
     * emit 값: Pair<Boolean, List<FileEntry>>
     *   - first  = isPartial: true 면 아직 스캔 중 (1차 emit), false 면 완료 (최종 emit)
     *   - second = 현재까지 수집된 전체 목록 (정렬 완료 상태)
     *
     * ── 핵심 변경 ────────────────────────────────────────────────
     *   - 정렬(sortedWith)을 flowOn(Dispatchers.IO) 블록 안에서 수행
     *     → 메인 스레드는 이미 정렬된 List 를 받아서 submitList() 만 호출
     *   - emit 횟수를 최대 2회로 제한:
     *     1차) 하위 디렉토리 + 첫 FIRST_CHUNK_SIZE 개 파일
     *     2차) 전체 완료 후 1회
     *     → DiffUtil 실행 횟수 감소 → 메인 스레드 블로킹 해소
     */
    private fun scanDirectoryFlow(folder: File, bucketId: Long): Flow<Pair<Boolean, List<FileEntry>>> = flow {

        // ── 1단계: 하위 디렉토리 수집 (File.listFiles, 빠름) ─────────
        var t = System.currentTimeMillis()
        val dirs = folder.listFiles()
            ?.filter { it.isDirectory && !it.name.startsWith(".") }
            ?.map { FileEntry(it, EntryType.DIRECTORY) }
            ?: emptyList()
        android.util.Log.d("SCAN", "[1] listFiles dirs: ${dirs.size}개 ${System.currentTimeMillis() - t}ms")

        // ── 2단계: MediaStore 쿼리 ────────────────────────────────────
        t = System.currentTimeMillis()

        val projection = arrayOf(
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Video.Media.DURATION          // 동영상 재생시간
        )

        val (selection, selArgs) = if (bucketId != -1L) {
            // BUCKET_ID = ? 방식 (정확, 빠름)
            "${MediaStore.Files.FileColumns.BUCKET_ID} = ? AND " +
            "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (?,?)" to arrayOf(
                bucketId.toString(),
                MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
                MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString()
            )
        } else {
            // DATA LIKE 폴백 (하위 폴더 직접 진입 시)
            val prefix  = "${folder.absolutePath}/%"
            val exclude = "${folder.absolutePath}/%/%"
            "${MediaStore.Files.FileColumns.DATA} LIKE ? AND " +
            "${MediaStore.Files.FileColumns.DATA} NOT LIKE ? AND " +
            "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (?,?)" to arrayOf(
                prefix, exclude,
                MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
                MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString()
            )
        }

        val sortOrder = when (currentSort) {
            SortOrder.NAME_ASC      -> "${MediaStore.Files.FileColumns.DISPLAY_NAME} ASC"
            SortOrder.NAME_DESC     -> "${MediaStore.Files.FileColumns.DISPLAY_NAME} DESC"
            SortOrder.DATE_NEWEST   -> "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
            SortOrder.DATE_OLDEST   -> "${MediaStore.Files.FileColumns.DATE_MODIFIED} ASC"
            SortOrder.SIZE_LARGEST  -> "${MediaStore.Files.FileColumns.SIZE} DESC"
            SortOrder.SIZE_SMALLEST -> "${MediaStore.Files.FileColumns.SIZE} ASC"
        }

        val cursor = contentResolver.query(
            MediaStore.Files.getContentUri("external"),
            projection, selection, selArgs, sortOrder
        )
        android.util.Log.d("SCAN", "[2] query: ${cursor?.count ?: 0}건 ${System.currentTimeMillis() - t}ms")

        // 파일 전체를 IO 에서 수집
        val allFiles = mutableListOf<FileEntry>()
        var existsMs = 0L
        var rowCount = 0

        cursor?.use { c ->
            val dataCol  = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
            val typeCol  = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
            val mimeCol  = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
            val durCol   = c.getColumnIndex(MediaStore.Video.Media.DURATION)  // 없을 수 있으므로 getColumnIndex

            t = System.currentTimeMillis()
            while (c.moveToNext()) {
                val path      = c.getString(dataCol) ?: continue
                val mediaType = c.getInt(typeCol)
                val mime      = c.getString(mimeCol) ?: ""
                val duration  = if (durCol >= 0 && mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO)
                                    c.getLong(durCol) else 0L

                val te = System.currentTimeMillis()
                val file = File(path)
                if (!file.exists()) { existsMs += System.currentTimeMillis() - te; continue }
                existsMs += System.currentTimeMillis() - te
                rowCount++

                val entryType = when {
                    mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO -> EntryType.VIDEO
                    mime == "image/gif"                                         -> EntryType.GIF
                    else                                                        -> EntryType.IMAGE

                }
                allFiles.add(FileEntry(file, entryType, duration))

                // ── 1차 emit: 디렉토리 + 첫 FIRST_CHUNK_SIZE 개 파일 ──
                // IO 에서 정렬까지 완료한 뒤 emit → 메인 스레드 부담 최소화
                if (allFiles.size == FIRST_CHUNK_SIZE) {
                    val partial = (dirs + allFiles).sortedWith(sortComparator())
                    emit(true to partial)   // isPartial = true
                    android.util.Log.d("SCAN", "[3-partial] 1차 emit: ${partial.size}개")
                }
            }
            android.util.Log.d("SCAN", "[3] 커서 순회: ${System.currentTimeMillis() - t}ms 유효 $rowCount 건 exists누적 ${existsMs}ms")
        }

        // ── 2차(최종) emit: 전체 항목 정렬 후 1회 ────────────────────
        // IO 에서 정렬 완료 → 메인 스레드는 submitList() 만 실행
        t = System.currentTimeMillis()
        val finalList = (dirs + allFiles).sortedWith(sortComparator())
        android.util.Log.d("SCAN", "[4] 최종 정렬: ${finalList.size}개 ${System.currentTimeMillis() - t}ms")

        emit(false to finalList)   // isPartial = false (완료)

    }.flowOn(Dispatchers.IO)   // 커서 순회 + 정렬 모두 IO 스레드에서 실행

 
    /** 현재 정렬 기준에 맞는 Comparator 반환 */
    private fun sortComparator(): Comparator<FileEntry> =
        compareBy<FileEntry> { if (it.type == EntryType.DIRECTORY) 0 else 1 }
            .then(when (currentSort) {
                SortOrder.NAME_ASC      -> compareBy { it.name.lowercase() }
                SortOrder.NAME_DESC     -> compareByDescending { it.name.lowercase() }
                SortOrder.DATE_NEWEST   -> compareByDescending { it.file.lastModified() }
                SortOrder.DATE_OLDEST   -> compareBy { it.file.lastModified() }
                SortOrder.SIZE_LARGEST  -> compareByDescending { it.file.length() }
                SortOrder.SIZE_SMALLEST -> compareBy { it.file.length() }
            })
 
    /**
     * 루트 미디어 폴더 스캔 — 단일 쿼리로 폴더 집계 + 썸네일 동시 수집
     *
     * 핵심: 추가 쿼리 없이 1회 전체 스캔에서 폴더별 첫 번째 미디어 경로를
     * thumbPath 로 기록 → queryOneThumbnail() 반복 호출 완전 제거
     *
     * 점진적 emit 은 유지:
     * - 집계 완료 직후 thumbnailPath 가 채워진 FolderItem 을 한꺼번에 emit
     * - UI 는 쿼리 1회 완료 시점에 전체 목록(썸네일 포함)을 표시
     */
    // ── 필터 토글 헬퍼 ───────────────────────────────────────
    private fun loadRoot() {
        if (showFoldersMode) loadRootFolders() else loadAllMedia()
    }

    private fun updateFilterToggleButton() {
        btnFilterToggle.setImageResource(
            if (showFoldersMode) R.drawable.filter_toggle_normal
            else                 R.drawable.filter_toggle_only_media
        )
    }

    private fun loadAllMedia() {
        if (rvFolders.adapter !== fileListAdapter) rvFolders.adapter = fileListAdapter
        fileListAdapter.submitEntries(emptyList())
        lifecycleScope.launch {
            scanAllMediaFlow().collect { (isPartial, entries) ->
                val isEmpty = entries.isEmpty()
                layoutEmpty.visibility = if (isEmpty) View.VISIBLE else View.GONE
                rvFolders.visibility   = if (isEmpty) View.GONE    else View.VISIBLE
                tvItemCount.text = if (isPartial) "미디어 ${entries.size}개+"
                                   else           "미디어 ${entries.size}개"
                fileListAdapter.submitEntries(entries)
            }
            rvFolders.scrollToPosition(0)
            swipeRefresh.isRefreshing = false
        }
    }

    private fun scanAllMediaFlow(): Flow<Pair<Boolean, List<FileEntry>>> = flow {
        val projection = arrayOf(
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Video.Media.DURATION
        )
        val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (?,?)"
        val selArgs   = arrayOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString()
        )
        val sortOrder = when (currentSort) {
            SortOrder.NAME_ASC      -> "${MediaStore.Files.FileColumns.DISPLAY_NAME} ASC"
            SortOrder.NAME_DESC     -> "${MediaStore.Files.FileColumns.DISPLAY_NAME} DESC"
            SortOrder.DATE_NEWEST   -> "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
            SortOrder.DATE_OLDEST   -> "${MediaStore.Files.FileColumns.DATE_MODIFIED} ASC"
            SortOrder.SIZE_LARGEST  -> "${MediaStore.Files.FileColumns.SIZE} DESC"
            SortOrder.SIZE_SMALLEST -> "${MediaStore.Files.FileColumns.SIZE} ASC"
        }
        val all = mutableListOf<FileEntry>()
        contentResolver.query(
            MediaStore.Files.getContentUri("external"),
            projection, selection, selArgs, sortOrder
        )?.use { c ->
            val dataCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
            val typeCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
            val mimeCol = c.getColumnIndex(MediaStore.Files.FileColumns.MIME_TYPE)
            val durCol  = c.getColumnIndex(MediaStore.Video.Media.DURATION)
            while (c.moveToNext()) {
                val path  = c.getString(dataCol) ?: continue
                val mType = c.getInt(typeCol)
                val mime  = if (mimeCol >= 0) c.getString(mimeCol) ?: "" else ""
                val dur   = if (durCol >= 0 && mType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO)
                                c.getLong(durCol) else 0L
                val ext   = path.substringAfterLast('.', "").lowercase()
                val type  = when {
                    mType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO -> EntryType.VIDEO
                    ext in GIF_EXT || mime.contains("gif")                 -> EntryType.GIF
                    else                                                    -> EntryType.IMAGE
                }
                all.add(FileEntry(File(path), type, dur))
                if (all.size == FIRST_CHUNK_SIZE) emit(true to all.toList())
            }
        }
        emit(false to all)
    }.flowOn(Dispatchers.IO)

    private fun scanMediaFoldersFlow(): Flow<FolderItem> = flow {
 
        data class FolderAccum(
            var bucketId: Long = -1L,
            var videoCount: Int = 0,
            var thumbPath: String? = null
        )
        val folderMap = LinkedHashMap<String, FolderAccum>()
 
        // ── 단일 쿼리: BUCKET_ID 포함해서 수집 ───────────────────────
        val projection = arrayOf(
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.Files.FileColumns.BUCKET_ID      // 추가
        )
        val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (?,?)"
        val selArgs   = arrayOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString()
        )
 
        contentResolver.query(
            MediaStore.Files.getContentUri("external"),
            projection, selection, selArgs, null
        )?.use { cursor ->
            val dataCol   = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
            val typeCol   = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
            val bucketCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.BUCKET_ID)
 
            while (cursor.moveToNext()) {
                val path      = cursor.getString(dataCol)  ?: continue
                val mediaType = cursor.getInt(typeCol)
                val bucketId  = cursor.getLong(bucketCol)
                val folder    = File(path).parent          ?: continue
 
                val accum = folderMap.getOrPut(folder) { FolderAccum() }
                if (accum.bucketId == -1L) accum.bucketId = bucketId
 
                if (mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO) {
                    accum.videoCount++
                    if (accum.thumbPath == null || !accum.thumbPath!!.isVideoPath()) {
                        accum.thumbPath = path
                    }
                } else {
                    if (accum.thumbPath == null) accum.thumbPath = path
                }
            }
        }
 
        // ── FolderItem 변환 후 emit ───────────────────────────────────
        folderMap.entries.forEach { (folderPath, accum) ->
            val dir = File(folderPath)
            if (!dir.exists() || dir.name.startsWith(".")) return@forEach
            val subDirCount = dir.listFiles()?.count { it.isDirectory } ?: 0
            emit(
                FolderItem(
                    name           = dir.name,
                    path           = folderPath,
                    bucketId       = accum.bucketId,   // 추가
                    videoCount     = accum.videoCount,
                    subFolderCount = subDirCount,
                    thumbnailPath  = accum.thumbPath
                )
            )
        }
 
    }.flowOn(Dispatchers.IO)
 
    private val VIDEO_EXT_SET = setOf("mp4","mkv","avi","mov","wmv","flv","webm","3gp","m4v","ts")
    private fun String.isVideoPath() =
        substringAfterLast('.', "").lowercase() in VIDEO_EXT_SET
     
    // ── 스크롤 위치 저장 / 복원 ──────────────────────────────

    /**
     * 현재 RecyclerView 의 LayoutManager 상태를 저장.
     * 루트 화면이면 KEY_ROOT, 탐색 중이면 현재 폴더 절대경로를 키로 사용.
     */
    private fun saveScrollPosition() {
        // ✅ Fix: folderStack.last()는 Pair<File,Long> → .first.absolutePath
        val key = if (folderStack.isEmpty()) KEY_ROOT else folderStack.last().first.absolutePath
        layoutManagerStateMap[key] = rvFolders.layoutManager?.onSaveInstanceState()
    }

    /**
     * 저장된 LayoutManager 상태를 복원.
     * submitList 콜백 안에서 호출해야 아이템이 배치된 후 적용됨.
     */
    private fun restoreScrollPosition(key: String) {
        val state = layoutManagerStateMap[key] ?: return
        rvFolders.layoutManager?.onRestoreInstanceState(state)
    }

    // ── 브레드크럼 ───────────────────────────────────────────
    private fun updateBreadcrumb() {
        tvBreadcrumb.text = folderStack.joinToString(" / ") { it.first.name }
    }

    // ── 파일 열기 ─────────────────────────────────────────────
    private fun openMediaFile(entry: FileEntry) {
        // TODO: 팝업 플레이어 연결
        //Toast.makeText(this, "열기: ${entry.path}", Toast.LENGTH_SHORT).show()
        startPipPlayer(entry.path)
    }

    // ── Player 실행 ─────────────────────────────────────────────
    private fun startPipPlayer(url: String?) {
      val intent = Intent(this, PlayerService::class.java)
      intent.putExtra(PlayerService.COMMAND, PlayerService.ACTION_START_PIP)
      intent.putExtra("data", url)
      startForegroundService(intent)
  }

    // ── 확장 함수 ─────────────────────────────────────────────
    private fun File.ext()     = name.substringAfterLast('.', "").lowercase()
    private fun File.isVideo() = ext() in VIDEO_EXT
    private fun File.isImage() = ext() in IMAGE_EXT
    private fun File.isGif()   = ext() in GIF_EXT
    private fun File.isMedia() = ext() in (VIDEO_EXT + IMAGE_EXT + GIF_EXT)

    // ── 그리드 아이템 간격 Decoration ────────────────────────
    private inner class GridSpacingDecoration(
        private val spanCount: Int,
        private val spacingDp: Int
    ) : RecyclerView.ItemDecoration() {

        private val sp: Int get() =
            (spacingDp * resources.displayMetrics.density).toInt()

        override fun getItemOffsets(
            outRect: android.graphics.Rect,
            view: View,
            parent: RecyclerView,
            state: RecyclerView.State
        ) {
            val position = parent.getChildAdapterPosition(view)
            if (position == RecyclerView.NO_ID.toInt()) return

            // 헤더(VT_HEADER=0)는 간격 없음
            if (fileListAdapter.getItemViewType(position) == 0) {
                outRect.set(0, 0, 0, sp)
                return
            }

            // 그리드 아이템: 좌우 모두 half 적용
            // RecyclerView 자체에 paddingStart/End 가 있으므로
            // 외부 여백은 이미 확보됨 → 내부 간격만 half/half 로 처리
            val half = sp / 2
            outRect.left   = half
            outRect.right  = half
            outRect.top    = 0
            outRect.bottom = sp
        }
    }
}