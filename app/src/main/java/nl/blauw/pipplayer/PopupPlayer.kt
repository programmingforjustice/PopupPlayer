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

/*interface PopupPlayer {
    fun show(params: WindowManager.LayoutParams? = null)
    fun play(currentPosition: Long = 0)
    fun dispose()
}*/

abstract class PopupPlayer(protected val context: Context) {
    protected val windowManager: WindowManager
    protected var layoutParams: WindowManager.LayoutParams
    
    init {
      windowManager = (context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager) ?: throw IllegalStateException("WindowManager is not available")
    }
    
    init {
      layoutParams = WindowManager.LayoutParams(
            Utils.convertDpToPixelsInt(2f, context),
            Utils.convertDpToPixelsInt(2f, context),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_TOAST,
            DEFAULT_WINDOW_FLAGS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            x = DEFAULT_POPUP_X
            y = DEFAULT_POPUP_Y
        }
    }

    companion object {
        const val MAX_POPUP_WIDTH = 400
        const val MAX_POPUP_HEIGHT = 400

        const val DEFAULT_POPUP_X = 100
        const val DEFAULT_POPUP_Y = 200

        const val CONTROLLER_SHOW_TIMEOUT = 2500
        const val DEFAULT_WINDOW_FLAGS = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
    }

    fun show(params: WindowManager.LayoutParams? = null) {
        params?.apply {
          layoutParams.x = x 
          layoutParams.y = y 
          layoutParams.width = width
          layoutParams.height = height
        }
        
        val view = createDisplayView()
        windowManager.addView(view, layoutParams)
    }
    
    abstract fun play(currentPosition: Long = 0)
    abstract fun removePopupWindow() 
    abstract fun dispose()
    
    abstract fun createDisplayView(): View
}

class ImagePopupPlayer @JvmOverloads constructor(context: Context, private val contentUrl: String): PopupPlayer(context), JsonSerializable {
    private val imageViewLayout: View
    private val controlLayout: View
    private val imageView: ImageView
    
    private var startTime: Long = 0
    
    init {
        val inflater = LayoutInflater.from(context) 
        imageViewLayout = inflater.inflate(R.layout.popup_player_image_view, null, false)
        controlLayout = imageViewLayout.findViewById<View>(R.id.player_image_view_control)
        imageView = imageViewLayout.findViewById<ImageView>(R.id.player_image_view)
    }
    
    fun setupImageView() {
        val bitmap: Bitmap = BitmapFactory.decodeFile(contentUrl) ?: throw IllegalStateException("cannot load image.")
        bitmap.run {
            layoutParams.height = height
            layoutParams.width = width
            imageViewLayout.tag = width.toDouble() / height //scaleFactor
            imageView.setImageBitmap(this)
        }
        
        imageView.scaleType = ImageView.ScaleType.FIT_CENTER
        
        imageViewLayout.setOnTouchListener(PlayerTouchListener(context, windowManager, layoutParams))
        
        imageViewLayout.setOnClickListener(::toggleControlLayout)
        
        val crossButton = imageViewLayout.findViewById<ImageButton>(R.id.cross_button)
        crossButton.setOnClickListener {
            windowManager.removeView(imageViewLayout)
        }
    }
    
    override fun createDisplayView(): View {
        setupImageView()
        return imageViewLayout
    }
    
    private fun hideControlLayout() {
        //imageViewLayout.isClickable = true
        if (System.currentTimeMillis() - startTime >= 2500) {
            controlLayout.visibility = View.GONE
        }
    }
    
    private fun toggleControlLayout(view: View?) {
        //imageViewLayout.isClickable = false
        if (controlLayout.visibility == View.VISIBLE) {
            controlLayout.removeCallbacks(::hideControlLayout)
            controlLayout.visibility = View.GONE
            //imageViewLayout.isClickable = true
            return
        }
        
        startTime = System.currentTimeMillis()
        controlLayout.visibility = View.VISIBLE
        controlLayout.removeCallbacks(::hideControlLayout)
        controlLayout.postDelayed(::hideControlLayout
            , 2500)
    }
    
    override fun play(currentPosition: Long) {
        //throw UnsupportedOperationException()
        toggleControlLayout(null)
    }
    
    override fun removePopupWindow() {
        
    }
    
    override fun dispose() {
        
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
                put("currentPosition", 0)
                put("isPlaying", true)
                put("x", layoutParams.x)
                put("y", layoutParams.y)
                put("width", layoutParams.width)
                put("height", layoutParams.height)
            }
    
        // JSON 문자열로 변환
        return jsonObject.toString()
   }
}

class VideoPopupPlayer @JvmOverloads constructor(context: Context, private val contentUrl: String, private val playerFactory: PlayerFactory = DefaultPlayerFactory(context), private val playerViewFactory: PlayerViewFactory = DefaultPlayerViewFactory(context)): PopupPlayer(context), JsonSerializable {
    private var player: Player
    private var playerView: PlayerView
    
    //private val windowManager: WindowManager
    //private var layoutParams: WindowManager.LayoutParams
    
    private val imageView: ImageView = ImageView(context)
    var isPlaying: Boolean = false
    var isDisposed: Boolean = false
      private set
    var isFullscreen: Boolean = false
      private set
      
    init {
        player = playerFactory.create(contentUrl)
        playerView = playerViewFactory.create(player)
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
                replacePlayerViewWithImageView()
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
            //dispose()
            PopupPlayerManager.remove(this)
        }
        
        //val muteToggleButtonListener =  MuteToggleButtonListener(player)
        val audioCodecMuteToggleButtonListener =  AudioCodecMuteToggleButtonListener(player)
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
        setupPlayer()
        setupPlayerView()
        return playerView
    }
    
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

    /**
     * PlayerView를 제거하고 ImageView로 대체하는 메서드
     */
    private fun replacePlayerViewWithImageView() {
        if (imageView.parent != null) return
        
        playerView.isClickable = false
        // 1. 현재 재생 중인 위치 확인
        val currentPosition = player.currentPosition
        val videoUri = Uri.fromFile(File(contentUrl)) ?: return
        // 2. 현재 정지된 프레임을 추출
        val bitmap = getFrameAtCurrentPosition(videoUri, currentPosition)
        if (bitmap != null) {
            // 3. ImageView에 추출한 프레임 설정
            imageView.setImageBitmap(bitmap)
            imageView.scaleType = ImageView.ScaleType.FIT_CENTER
            
            imageView.setOnClickListener {
                imageView.isClickable = false
                
                player = playerFactory.create(contentUrl)
                playerView = playerViewFactory.create(player)
                playerView.isClickable = false
                
                //player.seekTo(currentPosition)
                (player as PlayerWrapper).setRenderedFirstFrameListener {
                      if (imageView.parent != null) {
                          windowManager.removeView(imageView)
                          playerView.isClickable = true
                      }
                }
                show()
                play(currentPosition)
            }
            
            layoutParams = (playerView.layoutParams as? WindowManager.LayoutParams) ?: throw IllegalStateException("cannot get LayoutParams from PlayerView.")

            imageView.tag = playerView.tag
            imageView.setOnTouchListener(PlayerTouchListener(context, windowManager, layoutParams))
            windowManager.addView(imageView, layoutParams)
            
            release()
        }
        playerView.isClickable = true
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