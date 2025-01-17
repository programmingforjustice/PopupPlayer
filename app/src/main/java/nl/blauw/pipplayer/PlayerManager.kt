package nl.blauw.pipplayer

import android.content.Context
import android.util.Log
import android.view.ViewGroup
import android.view.WindowManager
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.ui.PlayerView
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.util.Util
import com.google.android.exoplayer2.video.VideoSize

class PlayerManager(private val context: Context) {

    private lateinit var player: ExoPlayer

    fun createPlayer() {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                1000, // 최소 버퍼
                2000, // 최대 버퍼
                250,  // 재생 시작 전 버퍼
                500   // 재버퍼링 후 버퍼
            ).build()

        val renderersFactory = DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)

        player = ExoPlayer.Builder(context, renderersFactory)
            .setLoadControl(loadControl)
            .build()
    }

    fun loadMediaSource(contentUrl: String) {
        val dataSourceFactory = DefaultDataSourceFactory(
            context, Util.getUserAgent(context, context.getString(R.string.app_name))
        )
        val contentMediaSource: MediaSource = ProgressiveMediaSource.Factory(
            dataSourceFactory,
            DefaultExtractorsFactory()
        ).createMediaSource(MediaItem.fromUri(contentUrl))

        player.setMediaSource(contentMediaSource)
    }

    fun play(playerView: PlayerView) {
        player.addListener(object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                val width = videoSize.width
                val height = videoSize.height
                if (width > 0 && height > 0) {
                    val scaleFactor = width.toDouble() / height
                    playerView.tag = scaleFactor
                    Log.d("PlayerManager", "ScaleFactor: $scaleFactor")

                    val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                    val params = playerView.layoutParams as WindowManager.LayoutParams

                    params.width = width
                    params.height = height

                    // playerView.layoutParams = params
                    windowManager.updateViewLayout(playerView, params)
                }
            }
        })

        player.prepare()
        player.repeatMode = Player.REPEAT_MODE_ALL
        player.playWhenReady = true
    }

    fun getPlayer(): Player {
        return player
    }

    fun releasePlayer() {
        player.release()
    }
}