package nl.blauw.pipplayer

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * NewPlaylistDialog
 * ─────────────────────────────────────────────────────────────
 * PlaylistActivity 의 "Nieuwe playlist aanmaken" 버튼에서 호출.
 * 미디어 추가 없이 플레이리스트만 생성.
 *
 * PlaylistBottomSheet 와 달리 mediaPath 가 필요 없음.
 */
class NewPlaylistDialog : BottomSheetDialogFragment() {

    companion object {
        private const val TAG = "NewPlaylistDialog"

        fun show(fm: FragmentManager) {
            if (fm.findFragmentByTag(TAG) == null) {
                NewPlaylistDialog().show(fm, TAG)
            }
        }
    }

    private lateinit var repo: PlaylistRepository
    private lateinit var etName: EditText
    private lateinit var btnCreate: Button

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // bottom_sheet_playlist_container 의 viewNewPlaylist 부분과 동일한 레이아웃 재사용
        return inflater.inflate(R.layout.bottom_sheet_new_playlist_standalone, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        repo      = PlaylistRepository(requireContext())
        etName    = view.findViewById(R.id.etPlaylistName)
        btnCreate = view.findViewById(R.id.btnCreatePlaylist)

        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        etName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                btnCreate.isEnabled = s?.toString()?.trim()?.isNotEmpty() == true
            }
        })

        etName.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE && btnCreate.isEnabled) {
                createPlaylist(); true
            } else false
        }

        btnCreate.setOnClickListener { createPlaylist() }
    }

    private fun createPlaylist() {
        val name = etName.text?.toString()?.trim() ?: ""
        if (name.isEmpty()) {
            Toast.makeText(requireContext(), "플레이리스트 이름을 입력해주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            val isDuplicate = withContext(Dispatchers.IO) { repo.isNameDuplicate(name) }
            if (isDuplicate) {
                Toast.makeText(requireContext(), "이미 존재하는 플레이리스트 이름입니다.", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val newId = withContext(Dispatchers.IO) { repo.createPlaylist(name) }
            if (newId > 0L) {
                Toast.makeText(requireContext(), "\"$name\" 플레이리스트가 생성되었습니다.", Toast.LENGTH_SHORT).show()
                dismiss()
            } else {
                Toast.makeText(requireContext(), "생성에 실패했습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}