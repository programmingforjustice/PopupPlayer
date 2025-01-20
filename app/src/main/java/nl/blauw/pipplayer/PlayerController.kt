package nl.blauw.pipplayer

import android.content.Context
import com.google.android.exoplayer2.ui.PlayerView

class PlayerController(private val context: Context) {

    private lateinit var playerManager: PlayerManager
    private lateinit var playerViewManager: PlayerViewManager

    fun initialize(contentUrl: String) {
        playerManager = PlayerManager(context, DefaultPlayerViewManagerFactory(context)).apply {
          createPlayer()
          loadMediaSource(contentUrl)
        }
        
        playerViewManager = playerManager.createPlayerViewManager()
        playerViewManager.createPlayerView(playerManager.getPlayer())
    }

    fun play() {
        playerManager.play()
    }
    
    fun getPlayerViewManager(): PlayerViewManager = playerViewManager
    
    fun getPlayerManager(): PlayerManager = playerManager

    //fun getPlayerView(): PlayerView = playerViewManager.getPlayerView()

    fun releaseResources() {
        playerManager.releasePlayer()
        playerViewManager.releasePlayerView()
    }
}