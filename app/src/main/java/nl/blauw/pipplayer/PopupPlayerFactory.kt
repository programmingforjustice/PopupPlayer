
package nl.blauw.pipplayer

import android.content.Context

interface PopupPlayerFactory {
    fun canHandle(mediaUrl: String): Boolean
    fun create(context: Context, mediaUrl: String): PopupPlayer
}

class DefaultPopupPlayerFactory : PopupPlayerFactory {
    val popupPlayerFactoryList = listOf(
        VideoPopupPlayerFactory(),
        ImagePopupPlayerFactory()
    )
    
    override fun canHandle(mediaUrl: String): Boolean = true
    
    override fun create(context: Context, mediaUrl: String): PopupPlayer {
        val factory = popupPlayerFactoryList.firstOrNull { it.canHandle(mediaUrl) } ?: throw IllegalArgumentException("Unsupported video or image extensions.")
        return factory.create(context, mediaUrl)
    }
}

class VideoPopupPlayerFactory : PopupPlayerFactory {
    override fun canHandle(mediaUrl: String): Boolean {
        return mediaUrl.lowercase().let {
            it.endsWith(".mp4") 
            || it.endsWith(".mkv") 
        }
    }
    
    override fun create(context: Context, mediaUrl: String): PopupPlayer {
        return VideoPopupPlayer(context, mediaUrl)
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
    
    override fun create(context: Context, mediaUrl: String): PopupPlayer {
        return ImagePopupPlayer(context, mediaUrl)
    }
}
