package nl.blauw.pipplayer

import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.widget.ImageButton
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ui.PlayerView

class PlayerViewManager(private val context: Context) {

    private lateinit var playerView: PlayerView

    fun createPlayerView(player: Player) {
        playerView = PlayerView(context).apply {
            player = player
            keepScreenOn = true
            controllerShowTimeoutMs = 2500
        }
    }
    
    fun setKeepScreenOn(keepScreenOn: Boolean): PlayerViewManager {
        playerView.keepScreenOn = keepScreenOn
        return this
    }

    fun setControllerShowTimeoutMs(timeoutMs: Int): PlayerViewManager {
        playerView.controllerShowTimeoutMs = timeoutMs
        return this
    }

    fun setRepeatToggleModes(modes: Int): PlayerViewManager {
        playerView.setRepeatToggleModes(modes)
        return this
    }
    
    fun setupCrossButton(action: (View) -> Unit) {
        val crossButton: ImageButton? = playerView.findViewById(R.id.cross_button)
        crossButton?.setOnClickListener(action)
    }
    
    fun setupMuteToggleButton(action: (View) -> Unit) {
        val muteToggleButton: ImageButton? = playerView.findViewById(R.id.mute_toggle_button)
        muteToggleButton?.setOnClickListener(action)
    }

    fun setupTouchListener(action: (View, MotionEvent) -> Boolean) {
        playerView.setOnTouchListener(action)
    }

    fun getPlayerView(): PlayerView = playerView

    fun releasePlayerView() {
        playerView.player = null
    }
}