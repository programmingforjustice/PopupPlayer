package nl.blauw.pipplayer

import android.Manifest
import android.content.pm.PackageManager
import android.content.ContentUris
import android.content.ContentResolver
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import android.view.Menu
import android.view.View
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.core.app.ActivityCompat
import androidx.core.view.GravityCompat
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
    private var currentSelectedItemId: Int = -1 // 초기 선택 항목 ID로 설정


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
        
        /*binding.navigationView.setOnClickListener{
            // START = left,  END = right
            binding.drawerLayout.openDrawer(GravityCompat.START)
        }*/

        // NavigationView 헤더의 TextView 가져오기
        val headerView = binding.navigationView.getHeaderView(0)
        val headerTitle = headerView.findViewById<android.widget.TextView>(R.id.header_title)

        // 네비게이션 드로어 메뉴 항목 선택 처리
        binding.navigationView.setNavigationItemSelectedListener { menuItem ->
                /*if (menuItem.itemId != currentSelectedItemId) {
                    currentSelectedItemId = menuItem.itemId*/
                    currentSelectedItemId = menuItem.itemId
                    headerTitle.text = "Selected Menu: ${menuItem.title}"
        
                    when (menuItem.itemId) {
                        R.id.nav_folders -> {
                            // MediaStore API를 사용하여 사진과 동영상 목록을 가져와 RecyclerView에 출력
                        loadMediaItems()
                            //debug("select - folders menu")
                        }
                        else -> {
                            binding.recyclerView.visibility = View.GONE
                        }
                    }
                //}
            //menuItem.setChecked(false)
            currentSelectedItemId = -1
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
                            
        //lifecycleScope.launch {
        if (currentSelectedItemId == R.id.nav_folders) {
            /*debug("start - query media items.")
            val mediaItems = withContext(Dispatchers.IO) { fetchMediaItems() }
            debug("finish - query media items.")*/
            debug("start - query media items.")
            val mediaItems = fetchMediaItems()
            debug("finish - query media items.")
            if (mediaItems.isNotEmpty()) {
                debug("mediaItems.size = ${mediaItems.size}")
                debug("start - recyclerview setup")
                //binding.recyclerView.visibility = View.VISIBLE
                debug("recyclerview - layoutManager")
                /*binding.recyclerView.layoutManager = LinearLayoutManager(this@NewMainActivity)*/
                
                //mediaAdapter = MediaAdapter(mediaItems)
                //debug("recyclerview - MediaAdapter")
                //binding.recyclerView.adapter = mediaAdapter
                //debug("end - recyclerview setup")
            } else {
                binding.recyclerView.visibility = View.GONE
            }
        }
        //}
    }
    
    private fun fetchMediaItems(): List<MediaItem> {
        val items = mutableListOf<MediaItem>()
    
        // MediaStore.Files를 사용하여 이미지와 동영상을 모두 쿼리
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.DATE_ADDED,
            MediaStore.Files.FileColumns.MEDIA_TYPE
        )
    
        // 이미지와 동영상만 선택 (SQLite 쿼리의 WHERE절처럼)
        val selection = ("${MediaStore.Files.FileColumns.MEDIA_TYPE}=? OR " +
                         "${MediaStore.Files.FileColumns.MEDIA_TYPE}=?")
        val selectionArgs = arrayOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString()
        )
    
        //val sortOrder = "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"
        val sortOrder: String? = null
    
        // 쿼리 URI (API 25 이하에서는 URI에 "limit" 파라미터를 추가)
        val queryUri = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            MediaStore.Files.getContentUri("external").buildUpon()
                .appendQueryParameter("limit", "20")
                .build()
        } else {
            MediaStore.Files.getContentUri("external")
        }
        //debug("start - obtain query results.")
        val cursor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // API 26 이상: Bundle을 사용하여 LIMIT과 정렬, selection 조건을 전달
            val queryArgs = Bundle().apply {
                putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgs)
                putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, sortOrder)
                putInt(ContentResolver.QUERY_ARG_LIMIT, 20)
            }
            contentResolver.query(queryUri, projection, queryArgs, null)
        } else {
            // API 25 이하: 기존 방식 (URI에 limit 파라미터가 이미 포함됨)
            contentResolver.query(queryUri, projection, selection, selectionArgs, sortOrder)
        }
        //debug("finish - obtain query results.")
        cursor?.use {
            val idColumn = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val nameColumn = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val mimeTypeColumn = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
            val mediaTypeColumn = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
            //debug("start - iterate query results.")
            while (it.moveToNext()) {
                val id = it.getLong(idColumn)
                val name = it.getString(nameColumn) ?: "Unknown"
                val mimeType = it.getString(mimeTypeColumn) ?: ""
                val mediaType = it.getInt(mediaTypeColumn)
                val contentUri = when (mediaType) {
                    MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE -> 
                        ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                    MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO -> 
                        ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                    else -> Uri.EMPTY
                }
                items.add(MediaItem(uri = contentUri, displayName = name, mimeType = mimeType))
            }
        }
        //debug("finish - iterate query results.")
    
        return items
    }

    // 툴바 액션 아이템(돋보기) 메뉴 구성
    /*override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }*/
    
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (requestCode == RequestCodes.PERMISSION_READ_MEDIA) {
            if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                // 권한이 허용됨
                //loadMediaItems()
                Toast.makeText(this, "permission is allowed.", Toast.LENGTH_SHORT).show()
            } else {
                // 권한이 거부됨
                Toast.makeText(this, "권한이 필요합니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun debug(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }
}