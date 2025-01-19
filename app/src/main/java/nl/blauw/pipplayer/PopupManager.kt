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

class PopupManager(private val context: Context, private val playerViewManager: PlayerViewManager) {
    private val windowManager: WindowManager by lazy {
      (context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager) ?: throw IllegalStateException("WindowManager is not available")
    }

    companion object {
        private const val MAX_POPUP_WIDTH = 400
        private const val MAX_POPUP_HEIGHT = 400

        private const val DEFAULT_POPUP_X = 100
        private const val DEFAULT_POPUP_Y = 200

        private const val CONTROLLER_SHOW_TIMEOUT = 2500

        private const val DEFAULT_WINDOW_FLAGS = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
    }

    fun setupPlayerView() {
        playerView.apply {
            keepScreenOn = true
            controllerShowTimeoutMs = CONTROLLER_SHOW_TIMEOUT
            setRepeatToggleModes(RepeatModeUtil.REPEAT_TOGGLE_MODE_ONE)
        }

        playerViewManager.setupCrossButton {
            playerViewManager.getPlayerView()?.let { playerView -> 
                playerView.parent?.let {
                    windowManager.removeViewImmediate(playerView)
                    playerView.player?.release()
                    playerView.player = null
                }
            }
        }
        
        val muteToggleButtonListener:  MuteToggleButtonListener(playerViewManager.getPlayerView()?.player)
        playerViewManager.setupMuteToggleButton { view -> 
            muteToggleButtonListener.onClick(view)
        }
    }

    fun show() {
        val params = WindowManager.LayoutParams(
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

        setupTouchListener(params)
        windowManager.addView(playerView, params)
    }

    private fun setupCrossButton() {
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
    }

    private fun setupTouchListener(params: WindowManager.LayoutParams) {
        playerView.setOnTouchListener(PlayerTouchListener(context, windowManager, params))
    }

    fun removePopupWindow() {
        windowManager.removeViewImmediate(playerView)
    }
}