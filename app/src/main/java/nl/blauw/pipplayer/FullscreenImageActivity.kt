package nl.blauw.pipplayer

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
import java.io.File

class FullscreenImageActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_URL = "fullscreen_image_url"
        private const val SLIDESHOW_INTERVAL_MS = 5_000L
        private const val TIMER_TICK_MS         = 50L
    }

    private lateinit var imageView:    ImageView
    private lateinit var progressBar:  ProgressBar

    private var currentIndex = 0
    private val playlist get() = FullscreenBridge.playlist
    private val isNavigationMode get() = playlist.size > 1

    private var slideshowTimer: CountDownTimer? = null

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_fullscreen_image)

        val url = intent.getStringExtra(EXTRA_URL) ?: run { finish(); return }
        currentIndex = FullscreenBridge.currentIndex

        imageView   = findViewById(R.id.fullscreen_image_view)
        progressBar = findViewById(R.id.slideshow_progress)

        loadImage(url)
        setupControls()
        hideSystemUi()

        if (isNavigationMode) startSlideshow()
    }

    override fun onResume() {
        super.onResume()
        hideSystemUi()
        if (isNavigationMode) startSlideshow()
    }

    override fun onPause() {
        super.onPause()
        slideshowTimer?.cancel()
    }

    override fun onDestroy() {
        super.onDestroy()
        slideshowTimer?.cancel()
        // Persist final index so ImagePopupPlayer can sync on return
        FullscreenBridge.currentIndex = currentIndex
        FullscreenBridge.onFullscreenClosed?.invoke(currentIndex, 0L)
        FullscreenBridge.onFullscreenClosed = null
    }

    // ── Image loading ─────────────────────────────────────────────────────────

    private fun loadImage(url: String) {
        val source: Any = if (url.startsWith("/") || url.startsWith("file://"))
            File(url.removePrefix("file://"))
        else
            Uri.parse(url)
        Glide.with(this).load(source).into(imageView)
        updateTitle(url)
    }

    private fun updateTitle(url: String) {
        val name = Uri.decode(url.substringAfterLast('/')).substringBeforeLast('.')
        findViewById<TextView>(R.id.fullscreen_title)?.text = name
    }

    // ── Controls ──────────────────────────────────────────────────────────────

    private fun setupControls() {
        findViewById<ImageButton>(R.id.fullscreen_back_button)?.setOnClickListener { finish() }

        val hasNav = isNavigationMode
        val prevBtn = findViewById<ImageButton>(R.id.prev_button)
        val nextBtn = findViewById<ImageButton>(R.id.next_button)
        prevBtn?.visibility = if (hasNav) View.VISIBLE else View.GONE
        nextBtn?.visibility = if (hasNav) View.VISIBLE else View.GONE
        progressBar.visibility = if (hasNav) View.VISIBLE else View.GONE

        prevBtn?.setOnClickListener { navigateTo(currentIndex - 1) }
        nextBtn?.setOnClickListener { navigateTo(currentIndex + 1) }
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    private fun navigateTo(rawIndex: Int) {
        if (playlist.isEmpty()) return
        currentIndex = ((rawIndex % playlist.size) + playlist.size) % playlist.size
        loadImage(playlist[currentIndex])
        startSlideshow()   // reset the 5-second countdown for the new image
    }

    // ── Slideshow ─────────────────────────────────────────────────────────────

    private fun startSlideshow() {
        slideshowTimer?.cancel()
        progressBar.progress = progressBar.max

        slideshowTimer = object : CountDownTimer(SLIDESHOW_INTERVAL_MS, TIMER_TICK_MS) {
            override fun onTick(millisUntilFinished: Long) {
                progressBar.progress =
                    ((millisUntilFinished * progressBar.max) / SLIDESHOW_INTERVAL_MS).toInt()
            }
            override fun onFinish() {
                navigateTo(currentIndex + 1)
            }
        }.start()
    }

    // ── System UI ─────────────────────────────────────────────────────────────

    private fun hideSystemUi() {
        WindowInsetsControllerCompat(window, imageView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}
