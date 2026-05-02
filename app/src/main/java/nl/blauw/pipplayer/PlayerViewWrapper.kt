package nl.blauw.pipplayer

import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.TextureView
import android.widget.ImageButton
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ui.PlayerView

class PlayerViewWrapper(private val context: Context): PlayerView(context) {
    
    fun setupCrossButton(action: (View) -> Unit) {
        val crossButton: ImageButton? = super.findViewById(R.id.cross_button)
        crossButton?.setOnClickListener(action)
    }
    
    fun setupMuteToggleButton(action: ((View) -> Unit)?) {
        val muteToggleButton: ImageButton? = super.findViewById(R.id.mute_toggle_button)
        muteToggleButton?.setOnClickListener(action)
    }
    
    fun setupFullscreenButton(action: (View) -> Unit) {
        val fullscreenButton: ImageButton? = super.findViewById(R.id.fullscreen_button)
        fullscreenButton?.setOnClickListener(action)
    }
    
    fun setupOrderEscalationButton(action: (View) -> Unit) {
        val orderEscalationButton: ImageButton? = super.findViewById(R.id.order_escalation_button)
        orderEscalationButton?.setOnClickListener(action)
    }
    
    fun setupTopOrderEscalationButton(action: (View) -> Unit) {
        val topOrderEscalationButton: ImageButton? = super.findViewById(R.id.top_order_escalation_button)
        topOrderEscalationButton?.setOnClickListener(action)
    }

    fun setupTouchListener(action: (View, MotionEvent) -> Boolean) {
        super.setOnTouchListener(action)
    }

    fun setupTouchThroughButton(action: (View) -> Unit) {
        super.findViewById<ImageButton>(R.id.touch_through_button)?.setOnClickListener(action)
    }

    fun setupPrevButton(action: (View) -> Unit) {
        super.findViewById<ImageButton>(R.id.prev_button)?.setOnClickListener(action)
    }

    fun setupNextButton(action: (View) -> Unit) {
        super.findViewById<ImageButton>(R.id.next_button)?.setOnClickListener(action)
    }

    fun setPrevNextVisibility(visible: Boolean) {
        val v = if (visible) View.VISIBLE else View.GONE
        super.findViewById<View>(R.id.prev_button)?.visibility = v
        super.findViewById<View>(R.id.next_button)?.visibility = v
    }

    fun setNavModeButtonsVisibility(visible: Boolean) {
        val v = if (visible) View.VISIBLE else View.GONE
        super.findViewById<View>(R.id.shuffle_button)?.visibility = v
        super.findViewById<View>(R.id.repeat_one_button)?.visibility = v
        super.findViewById<View>(R.id.repeat_all_button)?.visibility = v
    }

    fun setupShuffleButton(action: (View) -> Unit) {
        super.findViewById<ImageButton>(R.id.shuffle_button)?.setOnClickListener(action)
    }

    fun setupRepeatOneButton(action: (View) -> Unit) {
        super.findViewById<ImageButton>(R.id.repeat_one_button)?.setOnClickListener(action)
    }

    fun setupRepeatAllButton(action: (View) -> Unit) {
        super.findViewById<ImageButton>(R.id.repeat_all_button)?.setOnClickListener(action)
    }

    fun updateNavModeButtons(mode: NavMode) {
        super.findViewById<ImageButton>(R.id.shuffle_button)?.setImageResource(
            if (mode == NavMode.SHUFFLE) R.drawable.ic_shuffle else R.drawable.ic_shuffle_inactive
        )
        super.findViewById<ImageButton>(R.id.repeat_one_button)?.setImageResource(
            if (mode == NavMode.REPEAT_ONE) R.drawable.ic_repeat_one else R.drawable.ic_repeat_one_inactive
        )
        super.findViewById<ImageButton>(R.id.repeat_all_button)?.setImageResource(
            if (mode == NavMode.REPEAT_ALL) R.drawable.ic_repeat else R.drawable.ic_repeat_inactive
        )
    }

    /*fun applyToPlayerView(command: (playerView: PlayerView) -> Unit) {
        command(playerView)
    }*/
}