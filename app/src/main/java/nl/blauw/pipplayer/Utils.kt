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

fun debug(context: Context, text: String) {
    Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
}