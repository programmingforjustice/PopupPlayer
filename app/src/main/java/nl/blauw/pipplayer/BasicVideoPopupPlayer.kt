package nl.blauw.pipplayer

import android.net.Uri
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Color
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.ImageView
import android.widget.ImageButton
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ui.PlayerView
import com.google.android.exoplayer2.util.RepeatModeUtil
import android.widget.Toast
import java.io.File
import org.json.JSONObject

class BasicVideoPopupPlayer @JvmOverloads constructor(context: Context, private val contentUrl: String, private val playerFactory: PlayerFactory = DefaultPlayerFactory(context), private val playerViewFactory: PlayerViewFactory = DefaultPlayerViewFactory(context)): PopupPlayer(context), JsonSerializable {
    private var player: Player
    private var playerView: PlayerView
    //private val imageView: ImageView = ImageView(context)
    
    //private val muteToggleButtonListener =  MuteToggleButtonListener(player)
    private lateinit var audioCodecMuteToggleButtonListener: AudioCodecMuteToggleButtonListener
    
    var isPlaying: Boolean = false
    var isMuted: Boolean = true
    var isDisposed: Boolean = false
      private set
    var isFullscreen: Boolean = false
      private set
      
    private var onIsPlayingChangedListener: (() -> Unit)? = null
    
    private var onClose: (() -> Unit)? = null
      
    init {
        player = playerFactory.create(contentUrl)
        playerView = playerViewFactory.create(player)
        //audioCodecMuteToggleButtonListener =  AudioCodecMuteToggleButtonListener(player)
    }
    
    fun setOnIsPlayingChangedListener(action: (() -> Unit)?) {
        onIsPlayingChangedListener = action
    }
    
    fun setOnClose(action: (() -> Unit)?) {
        onClose = action
    }
    
    private fun setupPlayer() {
        val playerWrapper = player as PlayerWrapper
        
        playerWrapper.setVideoSizeChangedListener { videoSize -> 
                //Toast.makeText(context, "width: ${videoSize.width}, height:${videoSize.height}", Toast.LENGTH_SHORT).show()
                val width = videoSize.width
                val height = videoSize.height
                if (width > 0 && height > 0) {
                    val scaleFactor = width.toDouble() / height
                    playerView.tag = scaleFactor
                    
                    if (!isPlaying) {
                      layoutParams.width = width
                      layoutParams.height = height
                      windowManager.updateViewLayout(playerView, layoutParams)
                      
                      isPlaying = true
                    }
            }
        }
         
         playerWrapper.setOnIsPlayingChangedListener {
                //replacePlayerViewWithImageView()
                    onIsPlayingChangedListener?.invoke()
        }
    }

    private fun setupPlayerView() {
        val playerViewWrapper = playerView as PlayerViewWrapper
        
        playerViewWrapper.apply {
            setKeepScreenOn(true)
            setControllerShowTimeoutMs(CONTROLLER_SHOW_TIMEOUT)
            setRepeatToggleModes(RepeatModeUtil.REPEAT_TOGGLE_MODE_ONE)
        }

        playerViewWrapper.setupCrossButton {
            onClose?.invoke()
            dispose()
            PopupPlayerManager.remove(this)
        }
        
        //val muteToggleButtonListener =  MuteToggleButtonListener(player)
        //val audioCodecMuteToggleButtonListener =  AudioCodecMuteToggleButtonListener(player, isMuted)
        if (::audioCodecMuteToggleButtonListener.isInitialized) {
            isMuted = audioCodecMuteToggleButtonListener.isMuted
        }
        
        audioCodecMuteToggleButtonListener =  AudioCodecMuteToggleButtonListener(player, isMuted)
        playerViewWrapper.setupMuteToggleButton (audioCodecMuteToggleButtonListener::onClick)
        
        playerViewWrapper.setupFullscreenButton {
            toggleFullscreen()
        }
        
        playerViewWrapper.setupOrderEscalationButton {
            PopupPlayerManager.escalateOrder(this)
            PopupPlayerManager.showAllPopupPlayerByOrder()
        }
        
        val playerTouchListener = PlayerTouchListener(context, windowManager, layoutParams)
        playerViewWrapper.setupTouchListener(playerTouchListener::onTouch)
    }
    
    override fun createDisplayView(): View {
        return playerView.also {
                setupPlayer()
                setupPlayerView()
        }
    }
    
    override fun getCurrentPosition(): Long = player.currentPosition
    
    override fun play(currentPosition: Long) {
        player.repeatMode = Player.REPEAT_MODE_ALL
        player.prepare()
        player.seekTo(currentPosition)
        player.playWhenReady = true
    }

    override fun removePopupWindow() {
        windowManager.removeViewImmediate(playerView)
    }
    
    private fun getFrameAtCurrentPosition(videoUri: Uri, currentPosition: Long): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, videoUri)
            retriever.getFrameAtTime(currentPosition * 1000) // 현재 위치의 프레임 가져오기
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            retriever.release()
        }
    }
    
    override fun exportCurrentFrame(): Bitmap? {
        val currentPosition = player.currentPosition
        val videoUri = Uri.fromFile(File(contentUrl)) ?: return null
        return getFrameAtCurrentPosition(videoUri, currentPosition)
    }
    
    fun release() {
        player.release()
        playerView.player = null
        removePopupWindow()
    }
    
    override fun dispose() {
        if (!isDisposed) {
            release()
            isDisposed = true
        }
    }
    
    fun toggleFullscreen() { 
        if (isFullscreen) {
            exitFullscreen()
        } else {
            enterFullscreen()
        }
        isFullscreen = !isFullscreen
    }

    private fun enterFullscreen() {
        layoutParams?.let { originalParams ->
            val fullscreenParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                originalParams.type,
                //WindowManager.LayoutParams.TYPE_TOAST,
                originalParams.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv(),
                //PixelFormat.TRANSLUCENT
                PixelFormat.OPAQUE
            ).apply {
                gravity = Gravity.FILL
                flags = flags or WindowManager.LayoutParams.FLAG_FULLSCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                windowAnimations = android.R.style.Animation_Translucent
            }
        
            windowManager.updateViewLayout(playerView, fullscreenParams)
            setupImmersiveMode()
            
            /*removePopupWindow()
            player.stop()
            playerView.player = null
            windowManager.addView(playerView, fullscreenParams)
            playerView.player = player
            play(player.currentPosition)*/
        }
    }

    private fun exitFullscreen() {
        layoutParams?.let {
            windowManager.updateViewLayout(playerView, it)
            playerView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        }
    }
    
    private fun setupImmersiveMode() {
        playerView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
    }
    
    override fun toJsonString(): String {
        //player: currentPos, isPlaying
        //playerView: scaleFactor, videoSize
        //layoutParams: x, y, width, height
        // 고유한 플레이어 식별자 생성 (UUID 사용)
        //val playerIdentifier = "instance-${UUID.randomUUID()}"
    
        // JSON 객체 생성
        val jsonObject = JSONObject().apply {
                put("mediaPath", contentUrl)
                put("currentPosition", player.currentPosition)
                put("isPlaying", isPlaying)
                put("x", layoutParams.x)
                put("y", layoutParams.y)
                put("width", layoutParams.width)
                put("height", layoutParams.height)
            }
    
        // JSON 문자열로 변환
        return jsonObject.toString()
   }
}