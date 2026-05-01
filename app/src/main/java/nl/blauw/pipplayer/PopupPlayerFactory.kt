package nl.blauw.pipplayer

import android.net.Uri
import android.content.Context
import android.os.Build
import android.graphics.PixelFormat
import android.view.WindowManager
import org.json.JSONObject
import java.io.File

abstract class PopupPlayerFactory(protected val context: Context): JsonDeserializable<PopupPlayer> {
    protected var onFromJsonString: ((PopupPlayer, WindowManager.LayoutParams, JSONObject) -> Unit)? = null

    //abstract fun canHandle(mediaUrl: String): Boolean
    abstract fun canHandle(mediaUri: Uri): Boolean
    abstract fun create(mediaUrl: String): PopupPlayer
    abstract fun create(mediaUri: Uri): PopupPlayer
    
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
    
    //override fun canHandle(mediaUrl: String): Boolean = true
    
    override fun canHandle(mediaUri: Uri): Boolean = true
    
    /*private fun findFactory(mediaUrl: String): PopupPlayerFactory {
        return popupPlayerFactoryList.firstOrNull { it.canHandle(mediaUrl) } ?: throw IllegalArgumentException("Unsupported video or image extensions.")
    }*/
    
    private fun findFactory(mediaUri: Uri): PopupPlayerFactory {
        return popupPlayerFactoryList.firstOrNull { it.canHandle(mediaUri) } ?: throw IllegalArgumentException("Unsupported video or image extensions.")
    }
    
    override fun create(mediaUri: String): PopupPlayer {
        // 문자열이 http나 content로 시작하지 않는 일반 경로라면 fromFile 사용
        val uri = if (mediaUri.startsWith("/") || mediaUri.startsWith("file://")) {
            Uri.fromFile(File(mediaUri.replace("file://", "")))
        } else {
            Uri.parse(mediaUri)
        }
        return create(uri)
    }
    
    override fun create(mediaUri: Uri): PopupPlayer {
        val factory = findFactory(mediaUri)
        return factory.create(mediaUri)
    }
    
    override fun fromJsonString(jsonString: String): PopupPlayer {
        val jsonObject = JSONObject(jsonString)
        val mediaUri = jsonObject.getString("mediaPath") ?: throw IllegalStateException("cannot find mediaUrl from jsonString.")
        
        val factory = findFactory(Uri.parse(mediaUri))
        return factory.fromJsonString(jsonString)
    }
}

class VideoPopupPlayerFactory(context: Context) : PopupPlayerFactory(context) {
    
    init {
        onFromJsonString = { popupPlayer, layoutParams, playerInfo -> 
            (popupPlayer as BasicVideoPopupPlayer).isPlaying = playerInfo.getBoolean("isPlaying")
           // (popupPlayer as AdaptivePopupPlayer).isPlaying = playerInfo.getBoolean("isPlaying")
        }
    }
    
    private fun isSupportedFileExtensions(mediaUri: Uri): Boolean {
            return mediaUri.path?.lowercase()?.let {
                it.endsWith(".mp4") 
                || it.endsWith(".mkv")
            } ?: false
    }
    
    private fun isSupportedMediaType(mediaUri: Uri): Boolean {
        val mimeType = context.contentResolver.getType(mediaUri)
        debug(context, "mimeType = $mimeType")
        return mimeType?.takeIf { mimeType.startsWith("video/") }?.let { subType -> 
                subType.endsWith("/mp4")
                || subType.endsWith("/mkv")
            } ?: false
    }
    
    override fun canHandle(mediaUri: Uri): Boolean {
        return when(mediaUri.scheme) {
            "content" -> isSupportedMediaType(mediaUri)
            "file", null -> isSupportedFileExtensions(mediaUri)
            else -> false
        }
    }
    
    override fun create(mediaUri: String): PopupPlayer {
        return create(Uri.parse(mediaUri))
    }
    
    override fun create(mediaUri: Uri): PopupPlayer {
        //return VideoPopupPlayer(context, mediaUrl)
        return BasicVideoPopupPlayer(context, mediaUri.toString ?: "")
        //return AdaptivePopupPlayer(context, mediaUri.toString() ?: "")
        
    }
}

class ImagePopupPlayerFactory(context: Context) : PopupPlayerFactory(context) {

    private fun isSupportedFileExtensions(mediaUri: Uri): Boolean {
            return mediaUri.path?.lowercase()?.let {
                it.endsWith(".jpg") 
                || it.endsWith(".jpeg") 
                || it.endsWith(".png") 
                || it.endsWith(".gif") 
                || it.endsWith(".webp") 
                || it.endsWith(".bmp")
            } ?: false
    }
    
    private fun isSupportedMediaType(mediaUri: Uri): Boolean {
        val mimeType = context.contentResolver.getType(mediaUri)
        debug(context, "mimeType = $mimeType")
        return mimeType?.takeIf { mimeType.startsWith("image/") }?.let { subType -> 
                subType.endsWith("/jpg") 
                    || subType.endsWith("/jpeg") 
                    || subType.endsWith("/png") 
                    || subType.endsWith("/gif") 
                    || subType.endsWith("/webp") 
                    || subType.endsWith("/bmp")
            } ?: false
    }
    
    override fun canHandle(mediaUri: Uri): Boolean {
        return when(mediaUri.scheme) {
            "content" -> isSupportedMediaType(mediaUri)
            "file", null -> isSupportedFileExtensions(mediaUri)
            else -> false
        }
    }

    override fun create(mediaUri: String): PopupPlayer {
        return create(Uri.parse(mediaUri))
    }
    
    override fun create(mediaUri: Uri): PopupPlayer {
        return ImagePopupPlayer(context, mediaUri.toString() ?: "")
    }
}
