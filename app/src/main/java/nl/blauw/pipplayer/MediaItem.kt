package nl.blauw.pipplayer

import android.net.Uri
import android.graphics.Bitmap

/*data class MediaItem(
    val uri: Uri,
    val displayName: String,
    val mimeType: String
)*/

data class MediaItem(
    val id: Long,
    val displayName: String,
    val mimeType: String,
    val mediaType: Int,
    val contentUri: Uri,
    var thumbnail: Bitmap? = null
)