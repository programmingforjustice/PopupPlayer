package nl.blauw.pipplayer

import android.content.Context
import android.view.WindowManager
import com.google.android.exoplayer2.ui.PlayerView
import org.json.JSONObject
import java.util.UUID

class PlayerController(private val context: Context): JsonSerializable {

    private lateinit var playerManager: PlayerManager
    private lateinit var playerViewManager: PlayerViewManager
    private lateinit var popupManager: PopupManager
    
    var isPlayerReleased = false
      private set

    fun initialize(contentUrl: String) {
        playerManager = PlayerManager(context, DefaultPlayerViewManagerFactory(context)).apply {
          createPlayer()
          loadMediaSource(contentUrl)
        }
        
        playerViewManager = playerManager.createPlayerViewManager()
        //playerViewManager.createPlayerView(playerManager.getPlayer())
        popupManager = PopupManager(context, playerManager, playerViewManager)
    }

    fun play() {
        popupManager.show()
        playerManager.play()
    }
    
    fun getPlayerViewManager(): PlayerViewManager = playerViewManager
    
    fun getPlayerManager(): PlayerManager = playerManager

    //fun getPlayerView(): PlayerView = playerViewManager.getPlayerView()

    fun releaseResources() {
        playerManager.releasePlayer()
        playerViewManager.releasePlayerView()
        popupManager.removePopupWindow()
        isPlayerReleased = true
    }
    
    override fun toJsonString(): String {
        //player: currentPos, isPlaying
        //playerView: scaleFactor, videoSize
        //layoutParams: x, y, width, height
        // 고유한 플레이어 식별자 생성 (UUID 사용)
        //val playerIdentifier = "instance-${UUID.randomUUID()}"
    
        val layoutParams = (playerViewManager.getPlayerView().layoutParams as? WindowManager.LayoutParams) ?: throw IllegalStateException("cannot get LayoutParams from PlayerView.")
    
        // JSON 객체 생성
        val jsonObject = JSONObject().apply {
                put("mediaPath", playerManager.contentUrl)
                put("currentPosition", playerManager.getPlayer().currentPosition)
                put("isPlaying", playerManager.getPlayer().isPlaying)
                put("x", layoutParams.x)
                put("y", layoutParams.y)
                put("width", layoutParams.width)
                put("height", layoutParams.height)
            }
    
        // JSON 문자열로 변환
        return jsonObject.toString()
   }
}