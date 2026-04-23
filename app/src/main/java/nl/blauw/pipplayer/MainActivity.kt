package nl.blauw.pipplayer;

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import nl.blauw.pipplayer.FolderAdapter
import nl.blauw.pipplayer.FolderItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var folderAdapter: FolderAdapter
    private lateinit var rvFolders: RecyclerView

    // 지원하는 미디어 확장자
    private val VIDEO_EXTENSIONS = setOf(
        "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "3gp", "m4v", "ts"
    )
    private val IMAGE_EXTENSIONS = setOf(
        "jpg", "jpeg", "png", "webp", "bmp", "heic", "heif"
    )
    private val GIF_EXTENSIONS = setOf("gif")

    // ── 권한 요청 런처 ────────────────────────────────────────
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            if (grants.values.all { it }) {
                loadFolders()
            } else {
                Toast.makeText(this, "저장소 권한이 필요합니다.", Toast.LENGTH_LONG).show()
            }
        }

    // ── onCreate ─────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupRecyclerView()
        checkPermissionsAndLoad()
    }

    // ── RecyclerView 초기화 ───────────────────────────────────
    private fun setupRecyclerView() {
        rvFolders = findViewById(R.id.rvFolders)
        folderAdapter = FolderAdapter { folder ->
            // 폴더 클릭 시 해당 폴더 내부로 이동하거나 파일 목록 화면 열기
            openFolder(folder)
        }
        rvFolders.layoutManager = LinearLayoutManager(this)
        rvFolders.adapter = folderAdapter
    }

    // ── 권한 체크 ─────────────────────────────────────────────
    private fun checkPermissionsAndLoad() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+: 세분화된 미디어 권한
            arrayOf(
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_IMAGES
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val allGranted = permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        if (allGranted) {
            loadFolders()
        } else {
            permissionLauncher.launch(permissions)
        }
    }

    // ── 파일 시스템 스캔 (IO 스레드) ─────────────────────────
    private fun loadFolders() {
        lifecycleScope.launch {
            val folders = withContext(Dispatchers.IO) {
                scanMediaFolders()
            }
            // 알파벳 순 정렬 후 Adapter에 전달
            folderAdapter.submitList(folders.sortedBy { it.name.lowercase() })
        }
    }

    /**
     * 외부 저장소 루트를 탐색하여 미디어(동영상/이미지/GIF)가 포함된
     * 1-depth 폴더 목록을 반환합니다.
     */
    private fun scanMediaFolders(): List<FolderItem> {
        val root = Environment.getExternalStorageDirectory() ?: return emptyList()
        val result = mutableListOf<FolderItem>()

        root.listFiles()?.forEach { dir ->
            if (!dir.isDirectory || dir.name.startsWith(".")) return@forEach

            var videoCount   = 0
            var subDirCount  = 0
            var thumbnailPath: String? = null

            dir.listFiles()?.forEach { file ->
                when {
                    file.isDirectory -> subDirCount++
                    file.isMediaFile() -> {
                        if (file.isVideoFile()) videoCount++
                        // 첫 번째 미디어 파일을 대표 썸네일로 사용
                        if (thumbnailPath == null) thumbnailPath = file.absolutePath
                    }
                }
            }

            // 미디어가 하나도 없는 폴더는 제외
            if (videoCount == 0 && thumbnailPath == null) return@forEach

            result.add(
                FolderItem(
                    name          = dir.name,
                    path          = dir.absolutePath,
                    videoCount    = videoCount,
                    subFolderCount = subDirCount,
                    thumbnailPath = thumbnailPath,
                    badgeCount    = 0   // 필요 시 "새 파일" 로직으로 교체
                )
            )
        }

        return result
    }

    // ── 확장 함수 ─────────────────────────────────────────────
    private fun File.extension() = name.substringAfterLast('.', "").lowercase()

    private fun File.isVideoFile() = extension() in VIDEO_EXTENSIONS

    private fun File.isMediaFile() =
        extension() in (VIDEO_EXTENSIONS + IMAGE_EXTENSIONS + GIF_EXTENSIONS)

    // ── 폴더 열기 ─────────────────────────────────────────────
    private fun openFolder(folder: FolderItem) {
        // TODO: 파일 목록 Activity / Fragment로 이동
        // val intent = Intent(this, FileListActivity::class.java)
        //     .putExtra("folderPath", folder.path)
        //     .putExtra("folderName", folder.name)
        // startActivity(intent)
        Toast.makeText(this, "열기: ${folder.name}", Toast.LENGTH_SHORT).show()
    }
}