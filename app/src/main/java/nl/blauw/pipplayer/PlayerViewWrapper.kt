package nl.blauw.pipplayer

import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.TextureView
import android.widget.ImageButton
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ui.PlayerView

class PlayerViewWrapper(private val context: Context): PlayerView(context) {
    
    fun setKeepScreenOn(keepScreenOn: Boolean) = super.keepScreenOn = keepScreenOn

    fun setControllerShowTimeoutMs(timeoutMs: Int) = super.controllerShowTimeoutMs = timeoutMs

    fun setRepeatToggleModes(modes: Int) = 
        super.setRepeatToggleModes(modes)
    
    fun setupCrossButton(action: (View) -> Unit) {
        val crossButton: ImageButton? = super.findViewById(R.id.cross_button)
        crossButton?.setOnClickListener(action)
    }
    
    fun setupMuteToggleButton(action: (View) -> Unit) {
        val muteToggleButton: ImageButton? = super.findViewById(R.id.mute_toggle_button)
        muteToggleButton?.setOnClickListener(action)
    }

    fun setupTouchListener(action: (View, MotionEvent) -> Boolean) {
        super.setOnTouchListener(action)
    }
    
    /*fun applyToPlayerView(command: (playerView: PlayerView) -> Unit) {
        command(playerView)
    }*/

    //fun getPlayerView(): PlayerView = playerView

    /*fun releasePlayerView() {
        this.player = null
    }*/
}