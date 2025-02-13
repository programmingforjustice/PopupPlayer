package nl.blauw.pipplayer

import android.view.View
import android.widget.ImageButton
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.C

class AudioCodecMuteToggleButtonListener(private val muteToggleButton: ImageButton, player: Player, isMuted: Boolean) : View.OnClickListener {
    private var audioToggleHelper = AudioToggleHelper(player, isMuted)
    
    /*init {
        if (isMuted) {
            audioToggleHelper.toggleMute()
        }
    }*/
    
    var isMuted: Boolean
        get() = audioToggleHelper.isMuted
        private set
        /*set(value) {
            audioToggleHelper.isMuted = value
        }*/

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

class AudioToggleHelper(val player: Player, isMuted: Boolean) {

    // var player: Player = player
    //     set(value) {
    //         field = value
    //         updateAudioCodecStatus()
    //     }
        
    var isMuted: Boolean = isMuted
        set(value) {
            field = value
            updateAudioCodecStatus()
        }

    fun toggleMute() {
        isMuted = !isMuted
        updateAudioCodecStatus()
    }
    
    private fun updateAudioCodecStatus() {
        val trackSelector = (player as? PlayerWrapper)?.enableExoPlayerFeatures()?.trackSelector as? DefaultTrackSelector
        val parametersBuilder = trackSelector?.buildUponParameters()
        
        parametersBuilder?.setRendererDisabled(C.TRACK_TYPE_AUDIO, isMuted)
        parametersBuilder?.build()?.let {
            trackSelector.setParameters(it)
        }
    }
}