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
    
    /*private val displayManager: DisplayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayChanged(displayId: Int) {
            Toast.makeText(context, "displayId: $displayId", Toast.LENGTH_SHORT).show()
            if (displayId == Display.DEFAULT_DISPLAY) {
                updateDisplaySettings()
                // println("화면 회전 감지됨: $rotation")
                //calculateDisplayResolution()
                Toast.makeText(context, "화면 회전 감지됨: ($displayWidth, $displayHeight)", Toast.LENGTH_SHORT).show()
            }
        }

        override fun onDisplayAdded(displayId: Int) {}
        override fun onDisplayRemoved(displayId: Int) {}
    }*/
    
    init {
        //displayManager.registerDisplayListener(displayListener, null)
        updateDisplaySettings()
        
    }
    
    fun updateDisplaySettings() {
        // offsetX = 0f
        // offsetY = 0f
        // flagActionMove = false
        
        val (width, height) = getDisplayResolution()
        displayWidth = width
        displayHeight = height
        
        val rotation = windowManager.defaultDisplay.rotation
        when (rotation) {
            Surface.ROTATION_0 ->  { 
                /*val (width, height) = getDisplayResolution()
                displayWidth = minOf(width, height)
                displayHeight = maxOf(width, height)*/
                Toast.makeText(context, "화면 회전 감지됨: Surface.ROTATION_0", Toast.LENGTH_SHORT).show()
            }
            Surface.ROTATION_90 -> {
                /*val (width, height) = getDisplayResolution()
                displayWidth = maxOf(width, height)
                displayHeight = minOf(width, height)*/
                Toast.makeText(context, "화면 회전 감지됨: Surface.ROTATION_90", Toast.LENGTH_SHORT).show()
            }
            Surface.ROTATION_180 -> { 
                /*val (width, height) = getDisplayResolution()
                displayWidth = minOf(width, height)
                displayHeight = maxOf(width, height)*/
                Toast.makeText(context, "화면 회전 감지됨: Surface.ROTATION_180", Toast.LENGTH_SHORT).show()
            }
            Surface.ROTATION_270 -> {
                /*val (width, height) = getDisplayResolution()
                displayWidth = maxOf(width, height)
                displayHeight = minOf(width, height)*/
                Toast.makeText(context, "화면 회전 감지됨: Surface.ROTATION_270", Toast.LENGTH_SHORT).show()
            }
            //else -> -1 // 알 수 없는 값
        }
    }
    
    fun getDisplayResolution(): Pair<Int, Int> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {  
            // API 30 이상 (Android 11+)
            //val windowManager = getSystemService(WindowManager::class.java)
            val metrics: WindowMetrics = windowManager.maximumWindowMetrics
            Pair(metrics.bounds.width(), metrics.bounds.height())
        } else {  
            // API 29 이하 (Android 10-)
            //val windowManager = getSystemService(WindowManager::class.java)
            val display: Display = windowManager.defaultDisplay
            val size = Point()
            display.getRealSize(size)
            Pair(size.x, size.y)
        }
    }

    override fun onTouch(view: View, event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                updateDisplaySettings()
                flagActionMove = false
                offsetX = params.x - event.rawX
                offsetY = params.y - event.rawY
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