package nl.blauw.pipplayer

import android.content.Context
import android.graphics.Color
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
            //val videoSurfaceView = playerView.videoSurfaceView

        // videoSurfaceView가 SurfaceView인 경우에만 Z-Order를 최상위로 설정
        if (videoSurfaceView is SurfaceView) {
            // Surface를 최상위 레이어로 올림
            videoSurfaceView.setZOrderOnTop(true)
            // 투명/반투명 처리를 가능하게 하려면 PixelFormat 변경
            videoSurfaceView.holder.setFormat(PixelFormat.TRANSLUCENT)
            this.videoSurfaceView?.setBackgroundColor(Color.WHITE) // 원하는 색상으로 변경
            }
        }
    }
}

