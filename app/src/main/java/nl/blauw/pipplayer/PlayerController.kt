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
        playerManager.play(playerViewManager.getPlayerView())
    }
    
    fun getPlayerViewManager(): PlayerViewManager = playerViewManager

    fun getPlayerView(): PlayerView = playerViewManager.getPlayerView()

    fun releaseResources() {
        playerManager.releasePlayer()
        playerViewManager.releasePlayerView()
    }
}