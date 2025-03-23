
package nl.blauw.pipplayer

import android.content.Context
import android.os.Build
import android.graphics.PixelFormat
import android.view.WindowManager
import org.json.JSONObject

abstract class PopupPlayerFactory(protected val context: Context): JsonDeserializable<PopupPlayer> {
    protected var onFromJsonString: ((PopupPlayer, WindowManager.LayoutParams, JSONObject) -> Unit)? = null

    abstract fun canHandle(mediaUrl: String): Boolean
    abstract fun create(mediaUrl: String): PopupPlayer
    
    override fun fromJsonString(jsonString: String): PopupPlayer {
        val playerInfo = JSONObject(jsonString)
        var url = playerInfo.getString("mediaPath")
        val popupPlayer = create(url)
        //val layoutParams = WindowManager.LayoutParams().apply {
        popupPlayer.layoutParams.apply {
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
        
        onFromJsonString?.invoke(popupPlayer, popupPlayer.layoutParams, playerInfo)
        
        //popupPlayer.isPlaying = playerInfo.getBoolean("isPlaying")
        // popupPlayer.show(layoutParams)
        // popupPlayer.play(playerInfo.getLong("currentPosition"))
        return popupPlayer
    }
}

class DefaultPopupPlayerFactory(context: Context) : PopupPlayerFactory(context) {
    val popupPlayerFactoryList = listOf(
        VideoPopupPlayerFactory(context),
        ImagePopupPlayerFactory(context)
    )
    
    override fun canHandle(mediaUrl: String): Boolean = true
    
    private fun findFactory(mediaUrl: String): PopupPlayerFactory {
        return popupPlayerFactoryList.firstOrNull { it.canHandle(mediaUrl) } ?: throw IllegalArgumentException("Unsupported video or image extensions.")
    }
    
    override fun create(mediaUrl: String): PopupPlayer {
        val factory = findFactory(mediaUrl)
        return factory.create(mediaUrl)
    }
    
    override fun fromJsonString(jsonString: String): PopupPlayer {
        val jsonObject = JSONObject(jsonString)
        val mediaUrl = jsonObject.getString("mediaPath") ?: throw IllegalStateException("cannot find mediaUrl from jsonString.")
        
        val factory = findFactory(mediaUrl)
        return factory.fromJsonString(jsonString)
    }
}

class VideoPopupPlayerFactory(context: Context) : PopupPlayerFactory(context) {
    
    init {
        onFromJsonString = { popupPlayer, layoutParams, playerInfo -> 
           // (popupPlayer as VideoPopupPlayer).isPlaying = playerInfo.getBoolean("isPlaying")
            (popupPlayer as AdaptivePopupPlayer).isPlaying = playerInfo.getBoolean("isPlaying")
        }
    }
    
    override fun canHandle(mediaUrl: String): Boolean {
        return mediaUrl.lowercase().let {
            it.endsWith(".mp4") 
            || it.endsWith(".mkv") 
        }
    }
    
    override fun create(mediaUrl: String): PopupPlayer {
        //return VideoPopupPlayer(context, mediaUrl)
        //return BasicVideoPopupPlayer(context, mediaUrl)
        return AdaptivePopupPlayer(context, mediaUrl)
        
    }
}

class ImagePopupPlayerFactory(context: Context) : PopupPlayerFactory(context) {
    override fun canHandle(mediaUrl: String): Boolean {
        return mediaUrl.lowercase().let {
            it.endsWith(".jpg") 
            || it.endsWith(".jpeg") 
            || it.endsWith(".png") 
            || it.endsWith(".webp") 
            || it.endsWith(".bmp")
        }
    }
    
    override fun create(mediaUrl: String): PopupPlayer {
        return ImagePopupPlayer(context, mediaUrl)
    }
}
