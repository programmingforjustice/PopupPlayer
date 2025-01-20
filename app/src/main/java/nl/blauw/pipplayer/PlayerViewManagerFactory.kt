package nl.blauw.pipplayer

import android.content.Context

interface PlayerViewManagerFactory {
    fun create(player: Player): PlayerViewManager
}

class DefaultPlayerViewManagerFactory(
    private val context: Context
) : PlayerViewManagerFactory {

    override fun create(player: Player): PlayerViewManager {
        val playerViewManager = PlayerViewManager(context)
        playerViewManager.createPlayerView(player)
        return playerViewManager
    }
}

