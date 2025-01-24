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

class PopupManager(private val context: Context, private var playerManager: PlayerManager, private var playerViewManager: PlayerViewManager) {
    private var player: Player = playerManager.getPlayer()
    private var playerView: PlayerView = playerViewManager.getPlayerView()
    
    private val windowManager: WindowManager
    private var layoutParams: WindowManager.LayoutParams
    
    private val imageView: ImageView = ImageView(context)
    private var isPlaying: Boolean = false
    
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
        playerManager.setVideoSizeChangedListener { videoSize -> 
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
         
         playerManager.setOnIsPlayingChangedListener {
                replacePlayerViewWithImageView()
        }
    }

    private fun setupPlayerView() {
        playerViewManager.apply {
            setKeepScreenOn(true)
            setControllerShowTimeoutMs(CONTROLLER_SHOW_TIMEOUT)
            setRepeatToggleModes(RepeatModeUtil.REPEAT_TOGGLE_MODE_ONE)
        }

        playerViewManager.setupCrossButton {
            playerManager.releasePlayer()
            playerViewManager.releasePlayerView()
            removePopupWindow()
        }
        
        //val muteToggleButtonListener =  MuteToggleButtonListener(player)
        val audioCodecMuteToggleButtonListener =  AudioCodecMuteToggleButtonListener(player)
        playerViewManager.setupMuteToggleButton (audioCodecMuteToggleButtonListener::onClick)
        
        val playerTouchListener = PlayerTouchListener(context, windowManager, layoutParams)
        playerViewManager.setupTouchListener(playerTouchListener::onTouch)
    }

    fun show() {
        setupPlayer()
        setupPlayerView()
        windowManager.addView(playerView, layoutParams)
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
        val videoUri = Uri.fromFile(File(playerManager.contentUrl)) ?: return

        // 2. 현재 정지된 프레임을 추출
        val bitmap = getFrameAtCurrentPosition(videoUri, currentPosition)
        if (bitmap != null) {
            // 3. ImageView에 추출한 프레임 설정
            imageView.setImageBitmap(bitmap)
            imageView.scaleType = ImageView.ScaleType.FIT_CENTER
            
            imageView.setOnClickListener {
                windowManager.removeView(imageView)
                
                val playerController = PlayerController(context)
                playerController.initialize(playerManager.contentUrl)
                
                playerManager = playerController.getPlayerManager()
                playerViewManager = playerController.getPlayerViewManager()
                player = playerManager.getPlayer()
                playerView = playerViewManager.getPlayerView()
                
                show()
                playerController.play()
            }
            
            layoutParams = (playerView.layoutParams as? WindowManager.LayoutParams) ?: throw IllegalStateException("cannot get LayoutParams from PlayerView.")

            // 4. PlayerView를 WindowManager에서 제거
            //val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            //windowManager.removeView(playerView)
            playerManager.releasePlayer()
            playerViewManager.releasePlayerView()
            removePopupWindow()

            // 5. 동일한 위치에 ImageView를 추가
            /*val layoutParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT
            )*/
            windowManager.addView(imageView, layoutParams)
        }
    }
}