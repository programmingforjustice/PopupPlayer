package nl.blauw.pipplayer

import android.content.Context
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ui.PlayerView

interface PlayerViewFactory {
    fun create(player: Player): PlayerView
}

class DefaultPlayerViewFactory(
    private val context: Context
) : PlayerViewFactory {

    override fun create(player: Player): PlayerView {
        return PlayerViewWrapper(context).apply {
            this.player = player
        }
    }
}

