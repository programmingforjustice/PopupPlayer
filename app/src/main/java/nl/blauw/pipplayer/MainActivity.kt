package nl.blauw.pipplayer

import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.ImageButton
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
import android.provider.Settings
import androidx.recyclerview.widget.RecyclerView
import nl.blauw.pipplayer.EntryType
import nl.blauw.pipplayer.FileEntry
import nl.blauw.pipplayer.FileListAdapter
import nl.blauw.pipplayer.FolderAdapter
import nl.blauw.pipplayer.FolderItem
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : AppCompatActivity() {

    // ── 지원 확장자 ───────────────────────────────────────────
    private val VIDEO_EXT = setOf("mp4","mkv","avi","mov","wmv","flv","webm","3gp","m4v","ts")
    private val IMAGE_EXT = setOf("jpg","jpeg","png","webp","bmp","heic","heif")
    private val GIF_EXT   = setOf("gif")

    // ── 뷰 ───────────────────────────────────────────────────
    private lateinit var tvTitle: TextView
    private lateinit var tvBreadcrumb: TextView
    private lateinit var tvItemCount: TextView
    private lateinit var layoutBreadcrumb: View       // 브레드크럼 행 (탐색 중에만 보임)
    private lateinit var layoutEmpty: LinearLayout
    private lateinit var rvFolders: RecyclerView
    private lateinit var btnBack: ImageButton
    private lateinit var btnSort: ImageButton
    private lateinit var bottomNav: BottomNavigationView

    // ── Adapter ───────────────────────────────────────────────
    // 최상위(루트 폴더 목록)용 Adapter
    private lateinit var rootFolderAdapter: FolderAdapter
    // 탐색 중(파일/하위폴더 목록)용 Adapter
    private lateinit var fileListAdapter: FileListAdapter

    // ── 탐색 상태 ────────────────────────────────────────────
    // folderStack 이 비어있으면 → 루트 화면
    // 비어있지 않으면 → 파일 탐색기 화면 (stack.last() 가 현재 폴더)
    private val folderStack = ArrayDeque<File>()

    // 경로별 LayoutManager 상태 저장 (스크롤 위치 복원용)
    // 루트 화면은 KEY_ROOT 를 키로 사용
    private val layoutManagerStateMap = HashMap<String, android.os.Parcelable?>()
    private companion object { const val KEY_ROOT = "__root__" }

    // 정렬 상태
    private enum class SortOrder {
        NAME_ASC, NAME_DESC,
        DATE_NEWEST, DATE_OLDEST,
        SIZE_LARGEST, SIZE_SMALLEST
    }
    private var currentSort = SortOrder.NAME_ASC

    // ── 권한 요청 ─────────────────────────────────────────────
        private val permissionLauncher: ActivityResultLauncher<Array<String>> =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { grants: Map<String, Boolean> ->
            if (grants.values.all { it }) loadRootFolders()
            else Toast.makeText(this, "저장소 권한이 필요합니다.", Toast.LENGTH_LONG).show()
        }

    // ── onCreate ─────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bindViews()
        setupAdapters()
        setupToolbar()
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
        layoutEmpty      = findViewById(R.id.layoutEmpty)
        rvFolders        = findViewById(R.id.rvFolders)
        btnBack          = findViewById(R.id.btnBack)
        btnSort          = findViewById(R.id.btnSort)
        bottomNav        = findViewById(R.id.bottomNav)
    }

    // ── Adapter 초기화 ────────────────────────────────────────
    private fun setupAdapters() {
        // 루트 폴더 목록용
        rootFolderAdapter = FolderAdapter { folder ->
            saveScrollPosition()   // 루트 스크롤 위치 저장
            navigateTo(File(folder.path))
        }

        // 파일 탐색용
        fileListAdapter = FileListAdapter(
            onDirectoryClick = { entry -> navigateTo(entry.file) },
            onFileClick      = { entry -> openMediaFile(entry)   }
        )

        rvFolders.layoutManager = LinearLayoutManager(this)
        // 처음엔 루트 Adapter 연결
        rvFolders.adapter = rootFolderAdapter
    }

    // ── 툴바 버튼 ─────────────────────────────────────────────
    private fun setupToolbar() {
        btnBack.setOnClickListener { navigateUp() }

        btnSort.setOnClickListener {
            if (folderStack.isEmpty()) return@setOnClickListener
            // 6단계 순환
            currentSort = when (currentSort) {
                SortOrder.NAME_ASC     -> SortOrder.NAME_DESC
                SortOrder.NAME_DESC    -> SortOrder.DATE_NEWEST
                SortOrder.DATE_NEWEST  -> SortOrder.DATE_OLDEST
                SortOrder.DATE_OLDEST  -> SortOrder.SIZE_LARGEST
                SortOrder.SIZE_LARGEST -> SortOrder.SIZE_SMALLEST
                SortOrder.SIZE_SMALLEST -> SortOrder.NAME_ASC
            }
            updateSortButton()
            Toast.makeText(this, sortLabel(), Toast.LENGTH_SHORT).show()
            loadDirectory(folderStack.last(), restoreScroll = false)
        }

        // 초기 아이콘 설정
        updateSortButton()
    }

    /** 현재 정렬 상태에 맞는 아이콘을 btnSort 에 적용 */
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

    // ── 하단 네비 ─────────────────────────────────────────────
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
        folderStack.removeLast()
        if (folderStack.isEmpty()) {
            showRootScreen(restoreScroll = true)
        } else {
            loadDirectory(folderStack.last(), restoreScroll = true)
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
        if (allGranted) loadRootFolders() else permissionLauncher.launch(permissions)
    }

    // ─────────────────────────────────────────────────────────
    // 루트 화면 (기존 메인 폴더 목록)
    // ─────────────────────────────────────────────────────────
    private fun loadRootFolders() {
        lifecycleScope.launch {
            val folders = withContext(Dispatchers.IO) { scanMediaFolders() }
            rootFolderAdapter.submitList(folders.sortedBy { it.name.lowercase() })
        }
    }

    private fun showRootScreen(restoreScroll: Boolean = false) {
        // 타이틀 원복
        tvTitle.text = "Video's"
        // 브레드크럼 / 아이템 수 숨김
        layoutBreadcrumb.visibility = View.GONE
        tvItemCount.visibility      = View.GONE
        // 뒤로가기 버튼 숨김
        btnBack.visibility = View.GONE
        // Adapter 를 루트 전환
        rvFolders.adapter = rootFolderAdapter
        layoutEmpty.visibility = View.GONE
        rvFolders.visibility   = View.VISIBLE

        if (restoreScroll) {
            restoreScrollPosition(KEY_ROOT)
        }
    }

    // ─────────────────────────────────────────────────────────
    // 디렉토리 탐색 화면
    // ─────────────────────────────────────────────────────────

    /** 새 폴더로 이동 (스택에 push) — 이동 전 현재 스크롤 위치 저장 */
    private fun navigateTo(folder: File) {
        // 루트 → 하위폴더 진입은 rootFolderAdapter 클릭 시 이미 저장됨
        // 하위폴더 → 하위폴더 진입은 여기서 저장
        if (folderStack.isNotEmpty()) saveScrollPosition()
        folderStack.addLast(folder)
        loadDirectory(folder, restoreScroll = false)
    }

    /** 현재 폴더의 내용을 RecyclerView 에 표시 */
    private fun loadDirectory(folder: File, restoreScroll: Boolean = false) {
        // 탐색 중 UI 전환
        tvTitle.text = folder.name
        tvItemCount.text = "폴더 0개  •  파일 0개"

        btnBack.visibility = View.VISIBLE
        layoutBreadcrumb.visibility = View.VISIBLE
        tvItemCount.visibility = View.VISIBLE
        updateBreadcrumb()

        // Adapter 전환 (루트 → 파일 탐색)
        if (rvFolders.adapter !== fileListAdapter) {
            fileListAdapter.submitList(emptyList())
            rvFolders.adapter = fileListAdapter
        }

        lifecycleScope.launch {
            val entries = withContext(Dispatchers.IO) { scanDirectory(folder) }

            val dirCount  = entries.count { it.type == EntryType.DIRECTORY }
            val fileCount = entries.size - dirCount
            tvItemCount.text = "폴더 ${dirCount}개  •  파일 ${fileCount}개"

            val isEmpty = entries.isEmpty()
            layoutEmpty.visibility = if (isEmpty) View.VISIBLE else View.GONE
            rvFolders.visibility   = if (isEmpty) View.GONE    else View.VISIBLE

            if (restoreScroll) {
                // 뒤로가기: 데이터 교체 후 이전 스크롤 위치 복원
                fileListAdapter.submitList(entries) {
                    restoreScrollPosition(folder.absolutePath)
                }
            } else {
                // 새 폴더 진입: 목록 비우고 교체 후 상단으로
                fileListAdapter.submitList(entries) {
                    rvFolders.scrollToPosition(0)
                }
            }
        }
    }

    /** 디렉토리 내부 항목 스캔 + 정렬 */
    private fun scanDirectory(folder: File): List<FileEntry> {
        val raw = folder.listFiles() ?: return emptyList()
        val entries = raw.mapNotNull { file ->
            when {
                file.isDirectory -> FileEntry(file, EntryType.DIRECTORY)
                file.isVideo()   -> FileEntry(file, EntryType.VIDEO)
                file.isGif()     -> FileEntry(file, EntryType.GIF)
                file.isImage()   -> FileEntry(file, EntryType.IMAGE)
                else             -> null
            }
        }
        // 디렉토리 우선, 그 다음 currentSort 기준 정렬
        return entries.sortedWith(
            compareBy<FileEntry> { if (it.type == EntryType.DIRECTORY) 0 else 1 }
                .then(when (currentSort) {
                    SortOrder.NAME_ASC  -> compareBy { it.name.lowercase() }
                    SortOrder.NAME_DESC -> compareByDescending { it.name.lowercase() }
                    SortOrder.DATE_OLDEST -> compareBy { it.file.lastModified() }
                    SortOrder.DATE_NEWEST -> compareByDescending { it.file.lastModified() }
                    SortOrder.SIZE_SMALLEST -> compareBy { it.file.length() }
                    SortOrder.SIZE_LARGEST -> compareByDescending { it.file.length() }
                })
        )
    }

    /**
     * 루트 미디어 폴더 스캔 (재귀 DFS)
     *
     * 전략:
     * - 루트 직속 자식부터 시작해 모든 하위 디렉토리를 재귀 탐색
     * - 디렉토리 자신이 직접 보유한 미디어가 1개라도 있으면 FolderItem 으로 수집
     * - 숨김 폴더(.으로 시작)는 전부 스킵
     * - 스택 오버플로 방지를 위해 재귀 대신 명시적 스택(ArrayDeque) 사용
     */
    private fun scanMediaFolders(): List<FolderItem> {
        val root = Environment.getExternalStorageDirectory() ?: return emptyList()
        val result = mutableListOf<FolderItem>()
 
        // BFS/DFS 스택: 탐색할 디렉토리를 순서대로 담음
        val stack = ArrayDeque<File>()
 
        // 루트 직속 자식 디렉토리부터 시작 (루트 자체는 포함하지 않음)
        root.listFiles()
            ?.filter { it.isDirectory && !it.name.startsWith(".") }
            ?.forEach { stack.addLast(it) }
 
        while (stack.isNotEmpty()) {
            val dir = stack.removeLast()
 
            var videoCount  = 0
            var subDirCount = 0
            var thumbPath: String? = null
 
            dir.listFiles()?.forEach { f ->
                when {
                    // 숨김 하위 폴더 스킵, 일반 하위 폴더는 스택에 추가
                    f.isDirectory && !f.name.startsWith(".") -> {
                        subDirCount++
                        stack.addLast(f)   // 재귀 탐색 예약
                    }
                    f.isMedia() -> {
                        if (f.isVideo()) videoCount++
                        if (thumbPath == null) thumbPath = f.absolutePath
                    }
                }
            }
 
            // 직접 보유한 미디어가 하나라도 있는 폴더만 수집
            if (videoCount > 0 || thumbPath != null) {
                result.add(
                    FolderItem(
                        name           = dir.name,
                        path           = dir.absolutePath,
                        videoCount     = videoCount,
                        subFolderCount = subDirCount,
                        thumbnailPath  = thumbPath
                    )
                )
            }
        }
 
        return result.sortedBy { it.name.lowercase() }
    }

    // ── 스크롤 위치 저장 / 복원 ──────────────────────────────

    /**
     * 현재 RecyclerView 의 LayoutManager 상태를 저장.
     * 루트 화면이면 KEY_ROOT, 탐색 중이면 현재 폴더 절대경로를 키로 사용.
     */
    private fun saveScrollPosition() {
        val key = if (folderStack.isEmpty()) KEY_ROOT else folderStack.last().absolutePath
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
        tvBreadcrumb.text = folderStack.joinToString(" / ") { it.name }
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
}