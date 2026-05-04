package nl.blauw.pipplayer

import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ui.PlayerView

object FullscreenBridge {
    var onFullscreenClosed: ((Long) -> Unit)? = null
}

class FullscreenPlayerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_URL      = "fullscreen_url"
        const val EXTRA_POSITION = "fullscreen_position"
    }

    private lateinit var playerView: PlayerView
    private var player: Player? = null
    private var lastPosition = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_fullscreen_player)

        val url = intent.getStringExtra(EXTRA_URL) ?: run { finish(); return }
        lastPosition = intent.getLongExtra(EXTRA_POSITION, 0L)

        playerView = findViewById(R.id.fullscreen_player_view)

        val p = DefaultPlayerFactory(this).create(url)
        player = p
        playerView.player = p
        p.prepare()
        p.seekTo(lastPosition)
        p.playWhenReady = true

        playerView.findViewById<ImageButton>(R.id.fullscreen_back_button)
            ?.setOnClickListener { finish() }

        playerView.findViewById<TextView>(R.id.fullscreen_title)?.text =
            url.substringAfterLast('/').substringBeforeLast('.')

        hideSystemUi()
    }

    override fun onResume() {
        super.onResume()
        player?.playWhenReady = true
        hideSystemUi()
    }

    override fun onPause() {
        super.onPause()
        lastPosition = player?.currentPosition ?: lastPosition
        player?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        lastPosition = player?.currentPosition ?: lastPosition
        player?.release()
        player = null
        FullscreenBridge.onFullscreenClosed?.invoke(lastPosition)
        FullscreenBridge.onFullscreenClosed = null
    }

    private fun hideSystemUi() {
        WindowInsetsControllerCompat(window, playerView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}
