package nl.blauw.pipplayer

import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ui.PlayerView

object FullscreenBridge {
    /** Called on destroy: finalIndex in playlist, final playback position. */
    var onFullscreenClosed: ((finalIndex: Int, finalPosition: Long) -> Unit)? = null
    var playlist: List<String> = emptyList()
    var currentIndex: Int = 0
    var navMode: NavMode = NavMode.NONE
    /** width / height of the video that triggered fullscreen; >1.0 means landscape. */
    var aspectRatio: Double = 1.0
}

class FullscreenPlayerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_URL      = "fullscreen_url"
        const val EXTRA_POSITION = "fullscreen_position"
    }

    private lateinit var playerView: PlayerView
    private var player: Player? = null
    private var lastPosition = 0L
    private var currentIndex = 0

    private val playlist get() = FullscreenBridge.playlist

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_fullscreen_player)

        val url = intent.getStringExtra(EXTRA_URL) ?: run { finish(); return }
        lastPosition = intent.getLongExtra(EXTRA_POSITION, 0L)
        currentIndex = FullscreenBridge.currentIndex

        playerView = findViewById(R.id.fullscreen_player_view)
        applyOrientation(FullscreenBridge.aspectRatio)
        startPlayer(url, lastPosition)

        playerView.findViewById<ImageButton>(R.id.fullscreen_back_button)
            ?.setOnClickListener { finish() }

        playerView.findViewById<TextView>(R.id.fullscreen_title)?.text = displayName(url)

        setupNavButtons()
        setupLockButton()
        hideSystemUi()
    }

    // ── Navigation ────────────────────────────────────────────

    private fun setupNavButtons() {
        val hasNav = playlist.size > 1
        val prevBtn = playerView.findViewById<ImageButton>(R.id.prev_button)
        val nextBtn = playerView.findViewById<ImageButton>(R.id.next_button)
        prevBtn?.visibility = if (hasNav) View.VISIBLE else View.GONE
        nextBtn?.visibility = if (hasNav) View.VISIBLE else View.GONE
        prevBtn?.setOnClickListener { navigateTo(currentIndex - 1) }
        nextBtn?.setOnClickListener { navigateTo(currentIndex + 1) }
    }

    private fun navigateTo(rawIndex: Int) {
        if (playlist.isEmpty()) return
        currentIndex = (rawIndex % playlist.size + playlist.size) % playlist.size
        val newUrl = playlist[currentIndex]

        player?.release()
        startPlayer(newUrl, 0L)

        playerView.findViewById<TextView>(R.id.fullscreen_title)?.text = displayName(newUrl)
    }

    private fun startPlayer(url: String, position: Long) {
        val p = DefaultPlayerFactory(this).create(url)
        player = p
        (p as? PlayerWrapper)?.setVideoSizeChangedListener { videoSize ->
            if (videoSize.width > 0 && videoSize.height > 0)
                applyOrientation(videoSize.width.toDouble() / videoSize.height)
        }
        playerView.player = p
        p.prepare()
        p.seekTo(position)
        p.playWhenReady = true
        lastPosition = position
    }

    private fun applyOrientation(aspectRatio: Double) {
        requestedOrientation = if (aspectRatio > 1.0)
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        else
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    // ── Lock ──────────────────────────────────────────────────

    private fun setupLockButton() {
        playerView.findViewById<ImageButton>(R.id.fullscreen_lock_button)
            ?.setOnClickListener { setLocked(true) }
        findViewById<View>(R.id.fullscreen_unlock_button)
            ?.setOnClickListener { setLocked(false) }
    }

    private fun setLocked(locked: Boolean) {
        if (locked) {
            playerView.useController = false
            findViewById<View>(R.id.fullscreen_lock_overlay).visibility = View.VISIBLE
        } else {
            playerView.useController = true
            playerView.showController()
            findViewById<View>(R.id.fullscreen_lock_overlay).visibility = View.GONE
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────

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
        FullscreenBridge.onFullscreenClosed?.invoke(currentIndex, lastPosition)
        FullscreenBridge.onFullscreenClosed = null
    }

    private fun displayName(url: String): String =
        Uri.decode(url.substringAfterLast('/')).substringBeforeLast('.')

    private fun hideSystemUi() {
        WindowInsetsControllerCompat(window, playerView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}
