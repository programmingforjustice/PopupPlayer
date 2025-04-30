package nl.blauw.pipplayer

import android.content.Context
import android.os.Build
import android.net.Uri
import android.graphics.PixelFormat
import android.view.WindowManager
import java.util.Collections
import org.json.JSONArray
import org.json.JSONObject

object PopupPlayerManager : JsonSerializable {
    private const val PLAY_LIST_PATH = "play_list.txt"
    
    private lateinit var context: Context
    private var playerList: MutableList<PopupPlayer> = mutableListOf()
    private var orderList: MutableList<PopupPlayer> = mutableListOf()
    private lateinit var factory: PopupPlayerFactory
    
    fun initialize(context: Context) {
        this.context = context
        this.factory = DefaultPopupPlayerFactory(context)
    }
    
    fun create(mediaUrl: String): PopupPlayer {
        return factory.create(mediaUrl).also {
            playerList.add(it)
            orderList.add(it)
        }
    }
    
    override fun toJsonString(): String {
        return orderList
        //return playerList
          /*.filter{ popupPlayer -> 
            !popupPlayer.isDisposed
          }*/
          .map{ popupPlayer -> 
            (popupPlayer as? JsonSerializable)?.toJsonString() 
          }
          .joinToString(",", "[", "]")
    }
    
    fun savePlayList() {
      FileUtils.writeToFile(context, PLAY_LIST_PATH, toJsonString())
    }
    
    fun fromJsonString(jsonString: String?) {
        jsonString?.let {
        val jsonArray = JSONArray(it)

        for (i in 0 until jsonArray.length()) {
            val playerInfo: JSONObject = jsonArray.getJSONObject(i)
            val playingPlayer = playerList.find { player -> player.getMediaUri() == Uri.parse(playerInfo.getString("mediaPath")) }
            val recoveredPlayer = factory.fromJsonString(playerInfo.toString())
            //val popupPlayer = playingPlayer ?: recoveredPlayer
            
            if (playingPlayer != null) {
                //playingPlayer?.updatePlayerView(recoveredPlayer.layoutParams)
                playingPlayer?.removePopupWindow()
                playingPlayer?.show(recoveredPlayer.layoutParams)
                
            } else {
                recoveredPlayer.show(recoveredPlayer.layoutParams)
                recoveredPlayer.play(playerInfo.getLong("currentPosition"))
                playerList.add(recoveredPlayer)
                orderList.add(recoveredPlayer)
            }
        }
      }
    }
    
    fun escalateTopOrder(player: PopupPlayer) {
        if (player in playerList) {
            orderList.remove(player)
            orderList.add(player)
        }
    }
    
    fun escalateOrder(player: PopupPlayer) {
        val order = orderList.indexOf(player)
        /*if (order == -1) throw IllegalArgumentException("the specified PopupPlayer instance is not managed by PopupPlayerManager.")*/
        if (order == -1) return
        
        val escalatedOrder = Math.min(orderList.size-1, order+1)
        val temp = orderList[escalatedOrder]
        orderList[escalatedOrder] = orderList[order]
        orderList[order] = temp
    }
    
    fun showAllPopupPlayerByOrder() {
        closeAllPopupPlayerWindow()
        orderList.forEach { popupPlayer ->
            popupPlayer.show()
            //popupPlayer.play()
        }
    }
    
    fun closePopupPlayerWindow(player: PopupPlayer) {
        player.removePopupWindow()
    }
    
    fun closeAllPopupPlayerWindow() {
        playerList.forEach { popupPlayer -> 
            closePopupPlayerWindow(popupPlayer)
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
        //popupPlayer.dispose()
        playerList.remove(popupPlayer)
        orderList.remove(popupPlayer)
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
        orderList = mutableListOf()
    }
}
