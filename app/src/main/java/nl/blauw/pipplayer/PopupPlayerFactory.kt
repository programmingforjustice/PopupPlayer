
package nl.blauw.pipplayer

import android.content.Context
import org.json.JSONObject

abstract class PopupPlayerFactory(protected val context: Context): JsonDeserializable<PopupPlayer> {
    fun canHandle(mediaUrl: String): Boolean
    fun create(mediaUrl: String): PopupPlayer
}

class DefaultPopupPlayerFactory : PopupPlayerFactory {
    val popupPlayerFactoryList = listOf(
        VideoPopupPlayerFactory(),
        ImagePopupPlayerFactory()
    )
    
    override fun canHandle(mediaUrl: String): Boolean = true
    
    private fun findFactory(mediaUrl: String) {
        return popupPlayerFactoryList.firstOrNull { it.canHandle(mediaUrl) } ?: throw IllegalArgumentException("Unsupported video or image extensions.")
    }
    
    override fun create(mediaUrl: String): PopupPlayer {
        val factory = findFactory(mediaUrl)
        return factory.create(context, mediaUrl)
    }
    
    override fun fromJsonString(jsonString: String): PopupPlayer {
        val jsonObject = JSONObject(jsonString)
        val mediaUrl = jsonObject.getString("mediaPath") ?: throw IllegalStateExceprion("cannot find mediaUrl from jsonString.")
        
        val factory = findFactory(mediaUrl)
        return factory.fromJsonString(jsonString)
    }
}

class VideoPopupPlayerFactory : PopupPlayerFactory {
    override fun canHandle(mediaUrl: String): Boolean {
        return mediaUrl.lowercase().let {
            it.endsWith(".mp4") 
            || it.endsWith(".mkv") 
        }
    }
    
    override fun create(mediaUrl: String): PopupPlayer {
        return VideoPopupPlayer(context, mediaUrl)
    }
    
    override fun fromJsonString(jsonString: String): PopupPlayer {
        val playerInfo = JSONObject(jsonString)
        var url = playerInfo.getString("mediaPath")
        val popupPlayer = create(context, jsonString) as VideoPopupPlayer
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
        
        popupPlayer.isPlaying = playerInfo.getBoolean("isPlaying")
        popupPlayer.show(layoutParams)
        popupPlayer.play(playerInfo.getLong("currentPosition"))
        return popupPlayer       
    }
}

class ImagePopupPlayerFactory : PopupPlayerFactory {
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
    
    override fun fromJsonString(jsonString: String): PopupPlayer {
        val playerInfo = JSONObject(jsonString)
        var url = playerInfo.getString("mediaPath")
        val popupPlayer = create(context, jsonString) as ImagePopupPlayer
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
        
        //popupPlayer.isPlaying = playerInfo.getBoolean("isPlaying")
        popupPlayer.show(layoutParams)
        //popupPlayer.play(playerInfo.getLong("currentPosition"))
        return popupPlayer           
    }
}
