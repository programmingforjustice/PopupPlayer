package nl.blauw.pipplayer

import android.content.Context
import com.google.android.exoplayer2.ui.PlayerView

class PlayerController(context: Context) {

    private val playerManager = PlayerManager(context)
    private val playerViewManager = PlayerViewManager(context)

    fun initialize(contentUrl: String) {
        playerManager.apply {
          createPlayer()
          loadMediaSource(contentUrl)
        }
        
        playerViewManager.createPlayerView(playerManager.getPlayer())
    }

    fun play() {
        playerManager.play(playerViewManager.playerView)
    }

    fun getPlayerView(): PlayerView = playerViewManager.playerView

    fun releaseResources() {
        playerManager.releasePlayer()
        playerViewManager.releasePlayerView()
    }
}