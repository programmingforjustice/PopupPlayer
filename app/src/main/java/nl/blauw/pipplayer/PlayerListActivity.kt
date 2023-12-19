package nl.blauw.pipplayer

import androidx.appcompat.app.AppCompatActivity
import nl.blauw.pipplayer.databinding.ActivityPlayListBinding
import android.os.Bundle
import java.io.File
import kotlin.io.*

public class PlayerListActivity : AppCompatActivity() {

	private lateinit var binding: ActivityPlayListBinding
	
	private lateinit var klembordFile: File

    override fun onCreate(savedInstanceState: Bundle?) {
         super.onCreate(savedInstanceState)
         binding = ActivityPlayListBinding.inflate(layoutInflater)
         val view = binding.root
         setContentView(view)
		 
		 klembordFile = File(applicationContext.filesDir.path, "playlist.content")
    }
	
	override fun onPause() {
		super.onPause()
		with(binding) {
			if (klembord.text.length == 0) return
			klembordFile.writeText(klembord.text.toString())
		}
	}
	
	override fun onResume() {
		super.onResume()
		if (!klembordFile.exists()) return
		with(binding) {
			val contents = klembordFile.readText()
			klembord.setText(contents)
			klembord.setSelection(klembord.text.length)
		}
	}
}
