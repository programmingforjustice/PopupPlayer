package nl.blauw.pipplayer

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.widget.Toast

fun getMediaResolution(context: Context, uri: Uri): Pair<Int, Int>? {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, uri)

        val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
        val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)

        if (width != null && height != null) {
            Pair(width.toInt(), height.toInt())
        } else {
            null
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    } finally {
        retriever.release()
    }
}

fun loadBitmapFromContentUri(context: Context, uri: Uri): Bitmap? {
    return try {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            BitmapFactory.decodeStream(inputStream)
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

fun loadBitmapFromStorage(context: Context, uri: Uri): Bitmap? {
   return BitmapFactory.decodeFile(uri.path)
}

fun loadBitmap(context: Context, uri: Uri): Bitmap? {
   return when(uri.scheme) {
       "content" -> loadBitmapFromContentUri(context, uri)
       "file", null -> loadBitmapFromStorage(context, uri)
       else -> null
   }
}

fun debug(context: Context, text: String) {
    Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
}