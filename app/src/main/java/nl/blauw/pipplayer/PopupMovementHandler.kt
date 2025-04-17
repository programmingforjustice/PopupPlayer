package nl.blauw.pipplayer

import android.content.Context
import android.widget.Toast
import android.view.MotionEvent
import android.view.View
import android.view.Display
import android.view.WindowManager
import android.view.Surface
import android.os.Bundle
import android.os.Build
import android.graphics.Point
import android.util.DisplayMetrics
import android.content.res.Resources
import android.view.WindowMetrics
import android.hardware.display.DisplayManager

class PopupMovementHandler(
    private val context: Context,
    private val windowManager: WindowManager,
    private val params: WindowManager.LayoutParams
) : View.OnTouchListener {

    private var displayWidth: Int = 0
    private var displayHeight: Int = 0
    private var offsetX = 0f
    private var offsetY = 0f
    private var flagActionMove = false

    init {
        updateDisplaySettings()
    }
    
    fun updateDisplaySettings() {
        getDisplayResolution(context).also { (width, height) -> 
            displayWidth = width
            displayHeight = height
        }
    }
    
    /*fun getDisplayResolution(): Pair<Int, Int> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {  
            val metrics: WindowMetrics = windowManager.maximumWindowMetrics
            Pair(metrics.bounds.width(), metrics.bounds.height())
        } else {  
            val display: Display = windowManager.defaultDisplay
            val size = Point()
            display.getRealSize(size)
            Pair(size.x, size.y)
        }
    }*/

    override fun onTouch(view: View, event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                updateDisplaySettings()
                flagActionMove = false
                offsetX = minOf(params.x, displayWidth - params.width) - event.rawX
                offsetY = minOf(params.y, displayHeight - params.height) - event.rawY
                return false
            }

            MotionEvent.ACTION_MOVE -> {
                flagActionMove = true
                params.x = minOf(maxOf(0, (event.rawX + offsetX).toInt()), displayWidth - params.width)
                params.y = minOf(maxOf(0, (event.rawY + offsetY).toInt()), displayHeight - params.height)
                windowManager.updateViewLayout(view, params)
                return true
            }

            MotionEvent.ACTION_UP -> {
                return flagActionMove
            }
        }
        return false
    }
}