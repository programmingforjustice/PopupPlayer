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
import android.widget.Toast

class PlayerManager(private val context: Context, private val playerViewManagerFactory: PlayerViewManagerFactory): JsonSerializable {

    private lateinit var player: Player
    
    lateinit var contentUrl: String
      private set
    
    private val playerListener = object : Player.Listener {
        var onVideoSizeChangedListener: ((VideoSize) -> Unit)? = null
        var onRenderedFirstFrameListener: (() -> Unit)? = null
        var onIsPlayingChangedListener: (() -> Unit)? = null
        
        private var isSeekInProgress: Boolean = false
        
        override fun onVideoSizeChanged(videoSize: VideoSize) {
            //Toast.makeText(context, "onVideoSizeChangedListener : ${if (onVideoSizeChangedListener == null) false else true}", Toast.LENGTH_SHORT).show()
            onVideoSizeChangedListener?.invoke(videoSize)
        }
        
        override fun onRenderedFirstFrame() {
            //Toast.makeText(context, "onRenderedFirstFrameListener : ${if (onRenderedFirstFrameListener == null) false else true}", Toast.LENGTH_SHORT).show()
            onRenderedFirstFrameListener?.invoke()
        }
        
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            //super.onIsPlayingChanged(isPlaying)
            if (player.playbackState != Player.STATE_BUFFERING && !isPlaying && !isSeekInProgress) {
                // 플레이어가 일시정지되었을 때 처리
                onIsPlayingChangedListener?.invoke()   //replacePlayerViewWithImageView()
            }
        }
        
        override fun onPositionDiscontinuity(reason: Int) {
            // 진행바 이동이 시작되었을 때 호출
            if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                isSeekInProgress = true
                println("진행바 이동 시작")
            }
        }
        
        override fun onSeekProcessed() {
            // 진행바 이동이 완료된 후 호출
            isSeekInProgress = false
            println("진행바 이동 완료")
        }
        
        /*override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_ENDED -> {

                }
                Player.STATE_READY -> {
                    
                }
            }
        }*/
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
            .build().run { PlayerWrapper(this) }
            
        player.addListener(playerListener)
    }

    fun loadMediaSource(contentUrl: String) {
        this.contentUrl = contentUrl
        
        val dataSourceFactory = DefaultDataSourceFactory(
            context, Util.getUserAgent(context, context.getString(R.string.app_name))
        )
        val contentMediaSource: MediaSource = ProgressiveMediaSource.Factory(
            dataSourceFactory,
            DefaultExtractorsFactory()
        ).createMediaSource(MediaItem.fromUri(contentUrl))

        (player as PlayerWrapper).setMediaSource(contentMediaSource)
    }
    
    fun setVideoSizeChangedListener(action: ((VideoSize) -> Unit)?) {
        playerListener.onVideoSizeChangedListener = action
    }
    
    fun setRenderedFirstFrameListener(action: (() -> Unit)?) {
        playerListener.onRenderedFirstFrameListener = action
    
    }
    
    fun setOnIsPlayingChangedListener(action: (() -> Unit)?) {
        playerListener.onIsPlayingChangedListener = action
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
    
    override fun toJsonString(): String {
        return ""
    }
}