package nl.blauw.pipplayer

import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.Display
import android.view.WindowManager
import android.os.Bundle
import android.os.Build
import android.graphics.Point
import android.util.DisplayMetrics
import android.content.res.Resources
import android.view.WindowMetrics
import android.hardware.display.DisplayManager

class PopupMovementHandler(
    context: Context,
    private val windowManager: WindowManager,
    private val params: WindowManager.LayoutParams
) : View.OnTouchListener {

    private var displayWidth: Int = 0
    private var displayHeight: Int = 0
    private var offsetX = 0f
    private var offsetY = 0f
    private var flagActionMove = false
    
    private val displayManager: DisplayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayChanged(displayId: Int) {
            if (displayId == Display.DEFAULT_DISPLAY) {
                // val rotation = activity.windowManager.defaultDisplay.rotation
                // println("화면 회전 감지됨: $rotation")
                calculateDisplayResolution()
            }
        }

        override fun onDisplayAdded(displayId: Int) {}
        override fun onDisplayRemoved(displayId: Int) {}
    }
    
    init {
        /*if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowMetrics: WindowMetrics = windowManager.currentWindowMetrics
            val bounds = windowMetrics.bounds
            displayWidth = bounds.width()
            displayHeight = bounds.height()
        } else {
            val displayMetrics = DisplayMetrics()
            windowManager.defaultDisplay.getMetrics(displayMetrics)
            displayWidth = displayMetrics.widthPixels
            displayHeight = displayMetrics.heightPixels
        }*/
        
        /*val displayMetrics: DisplayMetrics = 
        Resources.getSystem().displayMetrics
        displayWidth = displayMetrics.widthPixels
        displayHeight = displayMetrics.heightPixels*/
        
        displayManager.registerDisplayListener(displayListener, null)
        
        calculateDisplayResolution()
    }
    
    fun calculateDisplayResolution() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {  
            // API 30 이상 (Android 11+)
            //val windowManager = getSystemService(WindowManager::class.java)
            val metrics: WindowMetrics = windowManager.maximumWindowMetrics
            displayWidth = metrics.bounds.width() 
            displayHeight = metrics.bounds.height()
        } else {  
            // API 29 이하 (Android 10-)
            //val windowManager = getSystemService(WindowManager::class.java)
            val display: Display = windowManager.defaultDisplay
            val size = Point()
            display.getRealSize(size)
            displayWidth = size.x
            displayHeight = size.y
            //Pair(size.x, size.y)
        }
    }

    override fun onTouch(view: View, event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
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