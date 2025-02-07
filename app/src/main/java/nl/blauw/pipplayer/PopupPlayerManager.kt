package nl.blauw.pipplayer

import android.content.Context
import android.os.Build
import android.graphics.PixelFormat
import android.view.WindowManager
import java.util.Collections
import org.json.JSONArray
import org.json.JSONObject

object PopupPlayerManager : JsonSerializable {
    private const val PLAY_LIST_PATH = "play_list.txt"
    
    private lateinit var context: Context
    private var playerList: MutableList<PopupPlayer> = mutableListOf()
    private val factory = DefaultPopupPlayerFactory()
    
    fun initialize(context: Context) {
        this.context = context
    }
    
    fun create(mediaUrl: String): PopupPlayer {
        return factory.create(context, mediaUrl).also {
            playerList.add(it)
        }
    }
    
    override fun toJsonString(): String {
        return playerList
          /*.filter{ popupPlayer -> 
            !popupPlayer.isDisposed
          }*/
          .map{ 
            (it as? JsonSerializable)?.toJsonString() 
          }
          .joinToString(",", "[", "]")
    }
    
    fun savePlayList() {
      FileUtils.writeToFile(context, PLAY_LIST_PATH, toJsonString())
    }
    
    fun fromJsonString(jsonString: String?) {
        jsonString?.let {
        val jsonArray = JSONArray(it)

        // 각 객체의 "id" 값을 읽기
        for (i in 0 until jsonArray.length()) {
            val playerInfo: JSONObject = jsonArray.getJSONObject(i)
            
            var url = playerInfo.getString("mediaPath")
            var popupPlayer = factory.create(context, url)
              
            //player.isPlaying = playerInfo.getBoolean("isPlaying")
            
            val layoutParams = WindowManager.LayoutParams().apply {
              x = playerInfo.getInt("x")
              y = playerInfo.getInt("y")
              width = playerInfo.getInt("width")
              height = playerInfo.getInt("height")
              type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                  WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
              else
                  WindowManager.LayoutParams.TYPE_TOAST
              flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
              format = PixelFormat.TRANSLUCENT
            }
            
            if (popupPlayer is VideoPopupPlayer) {
                popupPlayer.isPlaying = playerInfo.getBoolean("isPlaying")
            }
            
            popupPlayer.show(layoutParams)
            popupPlayer.play(playerInfo.getLong("currentPosition"))
            playerList.add(popupPlayer)
        }
      }
    }
    
    fun restorePlayList() {
        FileUtils.readFromFile(context, PLAY_LIST_PATH)?.let {
            fromJsonString(it)
        }
    }
    
    fun getPopupPlayerList(): List<PopupPlayer> {
        return Collections.unmodifiableList(playerList)
    }
    
    fun remove(popupPlayer: PopupPlayer) {
        popupPlayer.dispose()
        playerList.remove(popupPlayer)
    }
    
    fun clear() {
        playerList
          /*.filter{ popupPlayer -> 
            !popupPlayer.isDisposed
          }*/
          .forEach { popupPlayer -> 
            popupPlayer.dispose() 
          }
        playerList = mutableListOf()
    }
}
