package nl.blauw.pipplayer

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import com.google.android.exoplayer2.ui.PlayerView
import com.google.android.exoplayer2.util.RepeatModeUtil

class PopupManager(private val context: Context, private val playerManager: PlayerManager, private val playerViewManager: PlayerViewManager) {
    private val windowManager: WindowManager
    private val layoutParams: WindowManager.LayoutParams
    
    init {
      windowManager = (context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager) ?: throw IllegalStateException("WindowManager is not available")
    }
    
    init {
      layoutParams = WindowManager.LayoutParams(
            Utils.convertDpToPixelsInt(160f, context),
            Utils.convertDpToPixelsInt(90f, context),
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
        playerManager.setVideoSizeChangedListener { 
            videoSize -> {
                val width = videoSize.width
                val height = videoSize.height
                if (width > 0 && height > 0) {
                    val scaleFactor = width.toDouble() / height
                    playerViewManager.getPlayerView().tag = scaleFactor
                    //Log.d("PlayerManager", "ScaleFactor: $scaleFactor")

                    val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                    val params = playerViewManager.getPlayerView().layoutParams as WindowManager.LayoutParams

                    params.width = width
                    params.height = height

                    // playerView.layoutParams = params
                    windowManager.updateViewLayout(playerViewManager.getPlayerView(), params)
                }
            }
        }
    }

    private fun setupPlayerView() {
        playerViewManager.apply {
            setKeepScreenOn(true)
            setControllerShowTimeoutMs(CONTROLLER_SHOW_TIMEOUT)
            setRepeatToggleModes(RepeatModeUtil.REPEAT_TOGGLE_MODE_ONE)
        }

        playerViewManager.setupCrossButton {
            playerViewManager.getPlayerView()?.takeIf{ it.parent != null }?.apply { 
                windowManager.removeViewImmediate(this)
                player?.release()
                player = null
            }
        }
        
        val muteToggleButtonListener =  MuteToggleButtonListener(playerViewManager.getPlayerView()?.player)
        playerViewManager.setupMuteToggleButton (muteToggleButtonListener::onClick)
        
        val playerTouchListener = PlayerTouchListener(context, windowManager, layoutParams)
        playerViewManager.setupTouchListener(playerTouchListener::onTouch)
    }

    fun show() {
        setupPlayer()
        setupPlayerView()
        windowManager.addView(playerViewManager.getPlayerView(), layoutParams)
    }

    /*private fun setupCrossButton() {
        val crossButton: ImageButton? = playerView.findViewById(R.id.cross_button)
        crossButton?.setOnClickListener {
            playerView.parent?.let {
                windowManager.removeViewImmediate(playerView)
                playerView.player?.release()
                playerView.player = null
            }
        }
    }

    private fun setupMuteToggleButton() {
        val muteToggleButton: ImageButton? = playerView.findViewById(R.id.mute_toggle_button)
        muteToggleButton?.setOnClickListener(MuteToggleButtonListener(playerView.player))
    }*/

    /*private fun setupTouchListener(params: WindowManager.LayoutParams) {
        playerViewManager.getPlayerView().setOnTouchListener(PlayerTouchListener(context, windowManager, params))
    }*/

    fun removePopupWindow() {
        windowManager.removeViewImmediate(playerViewManager.getPlayerView())
    }
}