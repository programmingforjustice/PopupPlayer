package nl.blauw.pipplayer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.content.Context
import android.view.Display
import android.view.WindowManager
import android.graphics.Point
import android.view.WindowMetrics
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
        debug(context, uri.toString())
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            BitmapFactory.decodeStream(inputStream)
        }
    } catch (e: Exception) {
        debug(context, "An Exception Occurred.")
        e.printStackTrace()
        null
    }
}

fun loadBitmapFromStorage(context: Context, uri: Uri): Bitmap? {
   return BitmapFactory.decodeFile(uri.path)
}

fun loadBitmap(context: Context, uri: Uri): Bitmap? {
    debug(context, uri.toString())
   return when(uri.scheme) {
       "content" -> loadBitmapFromContentUri(context, uri)
       "file", null -> loadBitmapFromStorage(context, uri)
       else -> null
   }
}

fun getDisplayResolution(context: Context): Pair<Int, Int> {
    val windowManager: WindowManager = (context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager) ?: throw IllegalStateException("WindowManager is not available")
    
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {  
        val metrics: WindowMetrics = windowManager.maximumWindowMetrics
        Pair(metrics.bounds.width(), metrics.bounds.height())
    } else {  
        val display: Display = windowManager.defaultDisplay
        val size = Point()
        display.getRealSize(size)
        Pair(size.x, size.y)
    }
}

fun debug(context: Context, text: String) {
    Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
}