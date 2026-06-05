package nl.blauw.pipplayer

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.appcompat.widget.AppCompatImageView

/**
 * Thumbnail view used by the list row that is shared by directory and media items.
 *
 * FileListAdapter uses the same item_file_entry.xml for sub-folder rows and media rows.
 * Directory rows should visually match the top-level folder rows, while media rows should
 * keep the wide thumbnail. This view adjusts its parent thumbnail frame when the adapter
 * binds a folder icon or media thumbnail.
 */
class ListThumbnailImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    private val folderWidthPx = dp(100)
    private val folderHeightPx = ViewGroup.LayoutParams.WRAP_CONTENT
    private val mediaWidthPx = dp(100)
    private val mediaHeightPx = ViewGroup.LayoutParams.WRAP_CONTENT

    override fun setImageResource(resId: Int) {
        super.setImageResource(resId)
        if (resId == R.drawable.ic_folder_default) {
            applyFolderSizing()
        }
    }

    override fun setScaleType(scaleType: ImageView.ScaleType?) {
        super.setScaleType(scaleType)
        if (scaleType == ImageView.ScaleType.CENTER_CROP) {
            applyMediaSizing()
        }
    }

    private fun applyFolderSizing() {
        resizeParent(folderWidthPx, folderHeightPx)
        (parent as? View)?.background = null
        (parent as? View)?.clipToOutline = false
    }

    private fun applyMediaSizing() {
        resizeParent(mediaWidthPx, mediaHeightPx)
        (parent as? View)?.setBackgroundResource(R.drawable.bg_media_thumb)
        (parent as? View)?.clipToOutline = true
    }

    private fun resizeParent(width: Int, height: Int) {
        val parentView = parent as? View ?: return
        val params = parentView.layoutParams ?: return
        if (params.width != width || params.height != height) {
            params.width = width
            params.height = height
            parentView.layoutParams = params
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()
}
