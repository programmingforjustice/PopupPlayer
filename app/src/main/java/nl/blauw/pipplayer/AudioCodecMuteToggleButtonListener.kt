package nl.blauw.pipplayer

import android.view.View
import android.widget.ImageButton
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.C

class AudioCodecMuteToggleButtonListener(private val player: Player) : View.OnClickListener {
    private var audioToggleHelper = AudioToggleHelper(player)
    
    init {
      audioToggleHelper.toggleMute()
    }

    override fun onClick(v: View) {
        toggleMute(v as ImageButton)
    }

    private fun toggleMute(muteToggleButton: ImageButton) {
        audioToggleHelper.toggleMute()
        updateButtonImage(muteToggleButton)
    }

    private fun updateButtonImage(muteToggleButton: ImageButton) {
        muteToggleButton.setImageResource(
            if (audioToggleHelper.isMuted) R.drawable.ic_mute else R.drawable.ic_unmute
        )
    }
}

class AudioToggleHelper(private val player: Player) {

    var isMuted: Boolean = false
        private set

    fun toggleMute() {
        isMuted = !isMuted
        
        val trackSelector = (player as? PlayerWrapper)?..enableExoPlayerFeatures().trackSelector as? DefaultTrackSelector
        val parametersBuilder = trackSelector?.buildUponParameters()
        
        parametersBuilder?.setRendererDisabled(C.TRACK_TYPE_AUDIO, if (isMuted) true else false)
        parametersBuilder?.build()?.let {
            trackSelector.setParameters(it)
        }

        /*trackSelector?.parameters = if (isMuted) { 
            trackSelector
              ?.buildUponParameters()
                ?.setRendererDisabled(C.TRACK_TYPE_AUDIO, true) // Disable audio decoder
                .build()
        } else {
            trackSelector
              ?.buildUponParameters()
                ?.setRendererDisabled(C.TRACK_TYPE_AUDIO, false) // Enable audio decoder
                .build()
        }*/
    }
}