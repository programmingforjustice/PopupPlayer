package nl.blauw.pipplayer

import android.content.ContentUris
import android.os.Bundle
import android.os.Build
import android.provider.MediaStore
import android.view.Menu
import android.view.View
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.core.app.ActivityCompat
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import nl.blauw.pipplayer.databinding.ActivityNewMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NewMainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNewMainBinding
    private lateinit var mediaAdapter: MediaAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // View Binding 초기화
        binding = ActivityNewMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 툴바 설정
        setSupportActionBar(binding.toolbar)

        // ActionBarDrawerToggle (햄버거 아이콘) 설정
        val toggle = ActionBarDrawerToggle(
            this,
            binding.drawerLayout,
            binding.toolbar,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )
        binding.drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        // NavigationView 헤더의 TextView 가져오기
        val headerView = binding.navigationView.getHeaderView(0)
        val headerTitle = headerView.findViewById<android.widget.TextView>(R.id.header_title)

        // 네비게이션 드로어 메뉴 항목 선택 처리
        binding.navigationView.setNavigationItemSelectedListener { menuItem ->
            headerTitle.text = "Selected Menu: ${menuItem.title}"

            when (menuItem.itemId) {
                R.id.nav_folders -> {
                    // MediaStore API를 사용하여 사진과 동영상 목록을 가져와 RecyclerView에 출력
                    loadMediaItems()
                }
                else -> {
                    binding.recyclerView.visibility = View.GONE
                }
            }
            binding.drawerLayout.closeDrawers()
            true
        }
    }

    // MediaStore를 통해 미디어 항목을 가져오는 함수
    private fun loadMediaItems() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13 이상에서는 각각의 권한을 요청해야 합니다.
            ActivityCompat.requestPermissions(this, arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO
            ), RequestCodes.PERMISSION_READ_MEDIA)
        } else {
            ActivityCompat.requestPermissions(this, 
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), RequestCodes.PERMISSION_READ_MEDIA)
        }
        
        lifecycleScope.launch {
            val mediaItems = withContext(Dispatchers.IO) { fetchMediaItems() }
            if (mediaItems.isNotEmpty()) {
                binding.recyclerView.visibility = View.VISIBLE
                binding.recyclerView.layoutManager = LinearLayoutManager(this@NewMainActivity)
                mediaAdapter = MediaAdapter(mediaItems)
                binding.recyclerView.adapter = mediaAdapter
            } else {
                binding.recyclerView.visibility = View.GONE
            }
        }
    }

    // 사진과 동영상 항목을 쿼리하여 List<MediaItem>으로 반환
    private fun fetchMediaItems(): List<MediaItem> {
        val items = mutableListOf<MediaItem>()

        // 사진 쿼리
        val imageProjection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.MIME_TYPE
        )
        contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            imageProjection,
            null,
            null,
            null
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val mimeTypeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn) ?: "Unknown"
                val mimeType = cursor.getString(mimeTypeColumn) ?: "image/*"
                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id
                )
                items.add(MediaItem(uri = contentUri, displayName = name, mimeType = mimeType))
            }
        }

        // 동영상 쿼리
        val videoProjection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.MIME_TYPE
        )
        contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            videoProjection,
            null,
            null,
            null
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val mimeTypeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn) ?: "Unknown"
                val mimeType = cursor.getString(mimeTypeColumn) ?: "video/*"
                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id
                )
                items.add(MediaItem(uri = contentUri, displayName = name, mimeType = mimeType))
            }
        }

        return items
    }

    // 툴바 액션 아이템(돋보기) 메뉴 구성
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }
    
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (requestCode == RequestCodes.PERMISSION_READ_MEDIA) {
            if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                // 권한이 허용됨
                loadMediaItems()
            } else {
                // 권한이 거부됨
                Toast.makeText(this, "권한이 필요합니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}