package nl.blauw.pipplayer

import android.net.Uri
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.ImageButton
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ui.PlayerView
import com.google.android.exoplayer2.util.RepeatModeUtil
import android.widget.Toast
import java.io.File

class PopupManager @Jvmoverloads constructor(private val context: Context, private val contentUrl: String, private var playerFactory: PlayerFactory = DefaultPlayerFactory(context), private var playerViewFactory: PlayerViewFactory = DefaultPlayerViewFactory(context)): JsonSerializable {
    private val player: Player
    private val playerView: PlayerView
    
    private val windowManager: WindowManager
    private var layoutParams: WindowManager.LayoutParams
    
    private val imageView: ImageView = ImageView(context)
    private var isPlaying: Boolean = false
    private var isDisposed: Boolean = false
    
    init {
        player = playerFacory.create(contentUrl)
        playerView = playerViewFactory.create(player)
    }
    
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
        private const val MAX_POPUP_WIDTH = 400
        private const val MAX_POPUP_HEIGHT = 400

        private const val DEFAULT_POPUP_X = 100
        private const val DEFAULT_POPUP_Y = 200

        private const val CONTROLLER_SHOW_TIMEOUT = 2500

        private const val DEFAULT_WINDOW_FLAGS = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
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
        
        // playerManager.setRenderedFirstFrameListener {
        //     windowManager.addView(playerView, layoutParams)
         //}
         
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
            dispose()
        }
        
        //val muteToggleButtonListener =  MuteToggleButtonListener(player)
        val audioCodecMuteToggleButtonListener =  AudioCodecMuteToggleButtonListener(player)
        playerViewWrapper.setupMuteToggleButton (audioCodecMuteToggleButtonListener::onClick)
        
        val playerTouchListener = PlayerTouchListener(context, windowManager, layoutParams)
        playerViewWrapper.setupTouchListener(playerTouchListener::onTouch)
    }

    fun show(params: WindowManager.LayoutParams? = null) {
        //layoutParams?.let { this.layoutParams = it }
        params?.apply {
          layoutParams.x = x 
          layoutParams.y = y 
          layoutParams.width = width
          layoutParams.height = height
          isPlaying = true
        }
        
        setupPlayer()
        setupPlayerView()
        windowManager.addView(playerView, layoutParams)
    }
    
    fun play(currentPosition: Long = 0) {
        player.repeatMode = Player.REPEAT_MODE_ALL
        player.prepare()
        player.seekTo(currentPosition)
        player.playWhenReady = true
    }

    fun removePopupWindow() {
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
                imageView.setOnClickListener(null)
                
                player = playerFactory.create(videoUri)
                playerView = playerViewFactory.create(player)
                
                player.seekTo(currentPosition)
                (player as PlayerWrapper).setRenderedFirstFrameListener {
                      if (imageView.parent != null) {
                          windowManager.removeView(imageView)
                      }
                }
                show()
                play()
                //playerController.play(imageView.layoutParams as? WindowManager.LayoutParams)
            }
            
            layoutParams = (playerView.layoutParams as? WindowManager.LayoutParams) ?: throw IllegalStateException("cannot get LayoutParams from PlayerView.")

            imageView.tag = playerView.tag
            imageView.setOnTouchListener(PlayerTouchListener(context, windowManager, layoutParams))
            
            // 4. PlayerView를 WindowManager에서 제거
            //val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            //windowManager.removeView(playerView)
            dispose()

            // 5. 동일한 위치에 ImageView를 추가
            /*val layoutParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT
            )*/
            windowManager.addView(imageView, layoutParams)
        }
    }
    
    fun dispose() {
        player.release()
        playerView.player = null
        removePopupWindow()
        isDisposed = true
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
                put("isPlaying", player.isPlaying)
                put("x", layoutParams.x)
                put("y", layoutParams.y)
                put("width", layoutParams.width)
                put("height", layoutParams.height)
            }
    
        // JSON 문자열로 변환
        return jsonObject.toString()
   }
}