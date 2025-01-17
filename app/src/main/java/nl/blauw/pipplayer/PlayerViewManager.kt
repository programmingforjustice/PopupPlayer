package nl.blauw.pipplayer

import android.content.Context
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ui.PlayerView

class PlayerViewManager(private val context: Context) {

    private lateinit var playerView: PlayerView

    fun createPlayerView(player: Player) {
        playerView = PlayerView(context).apply {
            this.player = player
            keepScreenOn = true
            controllerShowTimeoutMs = 2500
        }
    }

    fun getPlayerView(): PlayerView = playerView

    fun releasePlayerView() {
        playerView.player = null
    }
}