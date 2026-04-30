package nl.blauw.pipplayer

import android.app.Dialog
import android.content.ContentUris
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.media.MediaScannerConnection
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class FolderPickerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_MODE  = "mode"
        const val EXTRA_PATHS = "paths"
        const val MODE_COPY   = "COPY"
        const val MODE_MOVE   = "MOVE"
    }

    private lateinit var tvTitle:         TextView
    private lateinit var tvSubtitle:      TextView
    private lateinit var layoutBreadcrumb: LinearLayout
    private lateinit var rvFolders:       RecyclerView
    private lateinit var btnCreate:       View
    private lateinit var btnAction:       Button

    private lateinit var mode:        String
    private lateinit var sourcePaths: List<String>

    private val root = Environment.getExternalStorageDirectory()
    private val folderStack = ArrayDeque<File>()
    private val currentDir get() = folderStack.lastOrNull() ?: root

    private lateinit var adapter: FolderAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_folder_picker)

        mode        = intent.getStringExtra(EXTRA_MODE) ?: MODE_COPY
        sourcePaths = intent.getStringArrayListExtra(EXTRA_PATHS) ?: emptyList()

        tvTitle          = findViewById(R.id.tvTitle)
        tvSubtitle       = findViewById(R.id.tvSubtitle)
        layoutBreadcrumb = findViewById(R.id.layoutBreadcrumb)
        rvFolders        = findViewById(R.id.rvFolders)
        btnCreate        = findViewById(R.id.btnCreate)
        btnAction        = findViewById(R.id.btnAction)

        tvTitle.text    = if (mode == MODE_COPY) "Kopiëren" else "Verplaatsen"
        tvSubtitle.text = "${sourcePaths.size} items selected"
        btnAction.text  = if (mode == MODE_COPY) "NAAR HIER KOPIËREN" else "NAAR HIER VERPLAATSEN"

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { navigateUp() }
        findViewById<ImageButton>(R.id.btnClose).setOnClickListener { finish() }
        btnCreate.setOnClickListener { showCreateFolderDialog() }
        btnAction.setOnClickListener { performAction() }

        setupBackPressed()

        adapter = FolderAdapter { folder ->
            folderStack.addLast(folder)
            refresh()
        }
        rvFolders.layoutManager = LinearLayoutManager(this)
        rvFolders.adapter = adapter

        refresh()
    }

    private fun setupBackPressed() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (folderStack.isNotEmpty()) {
                    navigateUp()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    private fun navigateUp() {
        if (folderStack.isNotEmpty()) {
            folderStack.removeLast()
            refresh()
        } else {
            finish()
        }
    }

    private fun refresh() {
        updateBreadcrumb()
        val dirs = (currentDir.listFiles { f -> f.isDirectory && !f.name.startsWith(".") }
            ?: emptyArray())
            .sortedBy { it.name.lowercase() }
        adapter.submitList(dirs)
    }

    private fun updateBreadcrumb() {
        layoutBreadcrumb.removeAllViews()

        val rootLabel = makeBreadcrumbText("Phone Storage", clickable = true) {
            folderStack.clear()
            refresh()
        }
        layoutBreadcrumb.addView(rootLabel)

        for (i in folderStack.indices) {
            val sep = TextView(this).apply {
                text = " › "
                textSize = 13f
                setTextColor(0xFF888888.toInt())
            }
            layoutBreadcrumb.addView(sep)

            val folder = folderStack[i]
            val isLast = i == folderStack.lastIndex
            val label = makeBreadcrumbText(folder.name, clickable = !isLast) {
                while (folderStack.size > i + 1) folderStack.removeLast()
                refresh()
            }
            layoutBreadcrumb.addView(label)
        }
    }

    private fun makeBreadcrumbText(text: String, clickable: Boolean, onClick: () -> Unit): TextView =
        TextView(this).apply {
            this.text = text
            textSize  = 13f
            if (clickable) {
                setTextColor(0xFF1565C0.toInt())
                setOnClickListener { onClick() }
            } else {
                setTextColor(0xFF333333.toInt())
            }
        }

    private fun showCreateFolderDialog() {
        val dialog    = Dialog(this)
        val view      = layoutInflater.inflate(R.layout.dialog_create_folder, null)
        dialog.setContentView(view)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.88).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val etName   = view.findViewById<EditText>(R.id.etFolderName)
        val btnClear = view.findViewById<ImageButton>(R.id.btnClearName)

        etName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {
                btnClear.visibility = if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        btnClear.setOnClickListener { etName.setText("") }

        view.findViewById<Button>(R.id.btnCancel).setOnClickListener { dialog.dismiss() }
        view.findViewById<Button>(R.id.btnConfirm).setOnClickListener {
            val name = etName.text.toString().trim()
            if (name.isEmpty()) {
                etName.error = "이름을 입력하세요"
                return@setOnClickListener
            }
            val newDir = File(currentDir, name)
            if (newDir.exists()) {
                Toast.makeText(this, "이미 존재하는 폴더입니다.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (newDir.mkdirs()) {
                dialog.dismiss()
                folderStack.addLast(newDir)
                refresh()
            } else {
                Toast.makeText(this, "폴더를 만들 수 없습니다.", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
    }

    private fun performAction() {
        val dest = currentDir
        lifecycleScope.launch {
            val succeeded = mutableListOf<String>()
            val failed    = mutableListOf<String>()
            withContext(Dispatchers.IO) {
                for (srcPath in sourcePaths) {
                    val srcFile = File(srcPath)
                    val target  = File(dest, srcFile.name)
                    try {
                        // Open source via content URI if available (handles scoped storage)
                        val srcUri    = resolveContentUri(srcPath)
                        val inputStream = if (srcUri != null)
                            contentResolver.openInputStream(srcUri)
                        else
                            srcFile.inputStream()

                        checkNotNull(inputStream) { "Cannot open source: ${srcFile.name}" }

                        inputStream.use { input ->
                            target.outputStream().use { output -> input.copyTo(output) }
                        }

                        // Let MediaStore know about the new file
                        MediaScannerConnection.scanFile(
                            applicationContext, arrayOf(target.absolutePath), null, null
                        )

                        if (mode == MODE_MOVE) {
                            // Delete source — try contentResolver first, fall back to File
                            if (srcUri != null) {
                                runCatching { contentResolver.delete(srcUri, null, null) }
                                    .onFailure { srcFile.delete() }
                            } else {
                                srcFile.delete()
                            }
                        }

                        succeeded.add(srcFile.name)
                    } catch (e: Exception) {
                        failed.add(srcFile.name)
                        android.util.Log.e("FolderPicker", "Failed: ${srcFile.name} → $e")
                    }
                }
            }
            if (succeeded.isNotEmpty()) {
                Toast.makeText(this@FolderPickerActivity, succeeded.joinToString(", "), Toast.LENGTH_LONG).show()
            }
            if (failed.isNotEmpty()) {
                Toast.makeText(this@FolderPickerActivity, "실패: ${failed.joinToString(", ")}", Toast.LENGTH_LONG).show()
            }
            finish()
        }
    }

    private fun resolveContentUri(path: String): android.net.Uri? =
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

    // ── Inner Adapter ─────────────────────────────────────────
    inner class FolderAdapter(
        private val onClick: (File) -> Unit
    ) : ListAdapter<File, FolderAdapter.VH>(object : DiffUtil.ItemCallback<File>() {
        override fun areItemsTheSame(a: File, b: File)    = a.absolutePath == b.absolutePath
        override fun areContentsTheSame(a: File, b: File) = a.absolutePath == b.absolutePath
    }) {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val tvName:  TextView = view.findViewById(R.id.tvFolderName)
            val tvCount: TextView = view.findViewById(R.id.tvSubfolderCount)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_folder_picker, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val folder = getItem(position)
            holder.tvName.text  = folder.name
            val subCount = folder.listFiles { f -> f.isDirectory }?.size ?: 0
            holder.tvCount.text = "$subCount mappen"
            holder.itemView.setOnClickListener { onClick(folder) }
        }
    }
}
