package nl.blauw.pipplayer

import android.content.Context
import android.os.Build
import android.graphics.PixelFormat
import android.view.WindowManager
import java.util.Collections
import org.json.JSONArray
import org.json.JSONObject

/*class PopupPlayerManager: private constructor() {
    companion object {
        //val instance: PopupPlayerManager by lazy { PopupPlayerMananger() }
        val instance: PopupPlayerManager = PopupPlayerMananger()
    }
    
    
}*/

object PopupPlayerManager {
    private const val PLAY_LIST_PATH = "PlayList.db"
    
    private lateinit var context: Context
    private var playerList: MutableList<PopupPlayer> = mutableListOf()
    
    fun initialize(context: Context) {
        this.context = context
    }
    
    fun create(mediaUrl: String): PopupPlayer {
        return PopupPlayer(context, mediaUrl).also {
            playerList.add(it)
        }
    }
    
    fun savePlayList() {
      var jsonStringForPlayetList = 
        playerList
          .filter{ popupPlayer -> 
            !popupPlayer.isDisposed
          }
          .map{ 
            it.toJsonString() 
          }
          .joinToString(",", "[", "]")
      
      //savePlayerListToFile(context,jsonStringForPlayetList)
      FileUtils.writeToFile(context, PLAY_LIST_PATH, jsonStringForPlayetList)
      
    }
    
    fun restorePlayList() {
      var jsonStringForPlayetList: String? = FileUtils.readFromFile(context, PLAY_LIST_PATH)
      jsonStringForPlayetList?.let {
        val jsonArray = JSONArray(it)

        // 각 객체의 "id" 값을 읽기
        for (i in 0 until jsonArray.length()) {
            val playerInfo: JSONObject = jsonArray.getJSONObject(i)
            
            var url = playerInfo.getString("mediaPath")
            var popupPlayer = PopupPlayer(context, url)
              
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
            
            popupPlayer.show(layoutParams)
            popupPlayer.play(playerInfo.getLong("currentPosition"))
            playerList.add(popupPlayer)
        }
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
          .filter{ popupPlayer -> 
            !popupPlayer.isDisposed
          }
          .forEach { popupPlayer -> 
            popupPlayer.dispose() 
          }
        playerList = mutableListOf()
    }
}
