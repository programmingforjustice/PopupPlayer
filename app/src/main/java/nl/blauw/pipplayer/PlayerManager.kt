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

class PlayerManager(private val context: Context, private val playerViewManagerFactory: PlayerViewManagerFactory) {

    private lateinit var player: ExoPlayer
    
    private val playerListener = object : Player.Listener {
        var onVideoSizeChanged: ((VideoSize) -> Unit)? = null
        var onRenderedFirstFrame:(() -> Unit)? = null
        
        override fun onVideoSizeChanged(videoSize: VideoSize) {
            onVideoSizeChanged?.invoke(videoSize)
        }
        
        override fun onRenderedFirstFrame() {
            onRenderedFirstFrame?.invoke()
        }
    }

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
            //.setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)

        player = ExoPlayer.Builder(context, renderersFactory)
            .setLoadControl(loadControl)
            .build()
            
        player.addListener(playerListener)
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
    
    fun setVideoSizeChangedListener(action: (VideoSize) -> Unit) {
        playerListener.onVideoSizeChanged = action
    }
    
    fun setRenderedFirstFrameListener(action: () -> Unit) {
        playerListener.onRenderedFirstFrame = action
    }
    
    fun play() {
        player.prepare()
        player.repeatMode = Player.REPEAT_MODE_ALL
        player.playWhenReady = true
    }
    
    fun applyToPlayer(command: (player: Player) -> Unit) {
        command(player)
    }
    
    fun createPlayerViewManager(): PlayerViewManager {
      return playerViewManagerFactory.create(player)
    }

    fun getPlayer(): Player {
        return player
    }

    fun releasePlayer() {
        if (::player.isInitialized) player.release()
    }
}