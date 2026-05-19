package nl.blauw.pipplayer

import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.bumptech.glide.Glide
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ui.PlayerView
import java.io.File

object FullscreenBridge {
    /** Called on destroy: finalIndex in playlist, final playback position (0 for images). */
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

        private val VIDEO_EXTENSIONS =
            setOf("mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "3gp", "m4v", "ts")
        private const val IMAGE_DISPLAY_MS = 5_000L
        private const val TIMER_TICK_MS    = 50L
    }

    // ── Views ─────────────────────────────────────────────────────────────────
    private lateinit var playerView:          PlayerView
    private lateinit var fullscreenImageView: ImageView
    private lateinit var imageControlOverlay: View
    private lateinit var slideshowProgress:   ProgressBar

    // ── State ─────────────────────────────────────────────────────────────────
    private var player: Player? = null
    private var lastPosition     = 0L
    private var currentIndex     = 0
    private var isCurrentItemImage = false
    private var slideshowTimer: CountDownTimer? = null

    private val playlist get() = FullscreenBridge.playlist

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_fullscreen_player)

        val url = intent.getStringExtra(EXTRA_URL) ?: run { finish(); return }
        lastPosition = intent.getLongExtra(EXTRA_POSITION, 0L)
        currentIndex = FullscreenBridge.currentIndex

        playerView           = findViewById(R.id.fullscreen_player_view)
        fullscreenImageView  = findViewById(R.id.fullscreen_image_view)
        imageControlOverlay  = findViewById(R.id.image_control_overlay)
        slideshowProgress    = imageControlOverlay.findViewById(R.id.slideshow_progress)

        applyOrientation(FullscreenBridge.aspectRatio)
        startItem(url, lastPosition)

        // Video-mode controls (inside PlayerView controller layout)
        playerView.findViewById<ImageButton>(R.id.fullscreen_back_button)
            ?.setOnClickListener { finish() }
        setupVideoNavButtons()
        setupLockButton()

        // Image-mode controls (inside image_control_overlay)
        setupImageControls()

        hideSystemUi()
    }

    override fun onResume() {
        super.onResume()
        player?.playWhenReady = true
        hideSystemUi()
        // Resume slideshow countdown if we're on an image and the timer was cancelled
        if (isCurrentItemImage && playlist.size > 1) startSlideshowTimer()
    }

    override fun onPause() {
        super.onPause()
        lastPosition = player?.currentPosition ?: lastPosition
        player?.pause()
        slideshowTimer?.cancel()
    }

    override fun onDestroy() {
        super.onDestroy()
        slideshowTimer?.cancel()
        lastPosition = player?.currentPosition ?: lastPosition
        player?.release()
        player = null
        // Images have no meaningful playback position
        val finalPos = if (isCurrentItemImage) 0L else lastPosition
        FullscreenBridge.onFullscreenClosed?.invoke(currentIndex, finalPos)
        FullscreenBridge.onFullscreenClosed = null
    }

    // ── Media type dispatcher ─────────────────────────────────────────────────

    private fun isVideoFile(url: String): Boolean =
        url.substringAfterLast('.', "").lowercase() in VIDEO_EXTENSIONS

    private fun startItem(url: String, position: Long) {
        if (isVideoFile(url)) showVideoMode(url, position)
        else                   showImageMode(url)
    }

    // ── Video mode ────────────────────────────────────────────────────────────

    private fun showVideoMode(url: String, position: Long) {
        isCurrentItemImage = false
        slideshowTimer?.cancel()

        player?.release()
        fullscreenImageView.visibility = View.GONE
        imageControlOverlay.visibility = View.GONE
        playerView.visibility          = View.VISIBLE

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

        playerView.findViewById<TextView>(R.id.fullscreen_title)?.text = displayName(url)
    }

    // ── Image mode ────────────────────────────────────────────────────────────

    private fun showImageMode(url: String) {
        isCurrentItemImage = true

        player?.release()
        player = null
        playerView.player  = null
        playerView.visibility = View.GONE

        val source: Any = if (url.startsWith("/") || url.startsWith("file://"))
            File(url.removePrefix("file://")) else Uri.parse(url)
        Glide.with(this).load(source).into(fullscreenImageView)

        fullscreenImageView.visibility = View.VISIBLE
        imageControlOverlay.visibility = View.VISIBLE

        imageControlOverlay.findViewById<TextView>(R.id.image_title)?.text = displayName(url)

        // Images are typically portrait; let the system decide orientation
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

        if (playlist.size > 1) startSlideshowTimer()
        else slideshowProgress.visibility = View.GONE
    }

    // ── Slideshow countdown ───────────────────────────────────────────────────

    private fun startSlideshowTimer() {
        slideshowTimer?.cancel()
        slideshowProgress.visibility = View.VISIBLE
        slideshowProgress.progress   = slideshowProgress.max
        slideshowTimer = object : CountDownTimer(IMAGE_DISPLAY_MS, TIMER_TICK_MS) {
            override fun onTick(ms: Long) {
                slideshowProgress.progress =
                    ((ms * slideshowProgress.max) / IMAGE_DISPLAY_MS).toInt()
            }
            override fun onFinish() { navigateTo(currentIndex + 1) }
        }.start()
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    private fun setupVideoNavButtons() {
        val hasNav = playlist.size > 1
        val prevBtn = playerView.findViewById<ImageButton>(R.id.prev_button)
        val nextBtn = playerView.findViewById<ImageButton>(R.id.next_button)
        prevBtn?.visibility = if (hasNav) View.VISIBLE else View.GONE
        nextBtn?.visibility = if (hasNav) View.VISIBLE else View.GONE
        prevBtn?.setOnClickListener { navigateTo(currentIndex - 1) }
        nextBtn?.setOnClickListener { navigateTo(currentIndex + 1) }
    }

    private fun setupImageControls() {
        imageControlOverlay.findViewById<ImageButton>(R.id.image_back_button)
            ?.setOnClickListener { finish() }

        val hasNav = playlist.size > 1
        val prevBtn = imageControlOverlay.findViewById<ImageButton>(R.id.image_prev_button)
        val nextBtn = imageControlOverlay.findViewById<ImageButton>(R.id.image_next_button)
        prevBtn?.visibility = if (hasNav) View.VISIBLE else View.GONE
        nextBtn?.visibility = if (hasNav) View.VISIBLE else View.GONE
        prevBtn?.setOnClickListener { navigateTo(currentIndex - 1) }
        nextBtn?.setOnClickListener { navigateTo(currentIndex + 1) }
    }

    private fun navigateTo(rawIndex: Int) {
        if (playlist.isEmpty()) return
        currentIndex = ((rawIndex % playlist.size) + playlist.size) % playlist.size
        val newUrl = playlist[currentIndex]
        slideshowTimer?.cancel()
        player?.release()
        player = null
        playerView.player = null
        startItem(newUrl, 0L)
    }

    // ── Lock (video mode only) ────────────────────────────────────────────────

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

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun applyOrientation(aspectRatio: Double) {
        requestedOrientation = if (aspectRatio > 1.0)
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        else
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    private fun displayName(url: String): String =
        Uri.decode(url.substringAfterLast('/')).substringBeforeLast('.')

    private fun hideSystemUi() {
        WindowInsetsControllerCompat(window, playerView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}
