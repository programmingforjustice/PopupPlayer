package nl.blauw.pipplayer

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Environment
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
            withContext(Dispatchers.IO) {
                for (srcPath in sourcePaths) {
                    val src     = File(srcPath)
                    val target  = File(dest, src.name)
                    try {
                        if (mode == MODE_COPY) {
                            src.copyTo(target, overwrite = true)
                            succeeded.add(src.name)
                        } else {
                            val moved = src.renameTo(target)
                            if (!moved) {
                                src.copyTo(target, overwrite = true)
                                src.delete()
                            }
                            succeeded.add(src.name)
                        }
                    } catch (_: Exception) {}
                }
            }
            if (succeeded.isNotEmpty()) {
                Toast.makeText(
                    this@FolderPickerActivity,
                    succeeded.joinToString(", "),
                    Toast.LENGTH_LONG
                ).show()
            }
            finish()
        }
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
