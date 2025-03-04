package nl.blauw.pipplayer

import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.os.Bundle
import android.os.Build
import android.util.DisplayMetrics

class PopupMovementHandler(
    context: Context,
    private val windowManager: WindowManager,
    private val params: WindowManager.LayoutParams
) : View.OnTouchListener {

    private val displayWidth: Int
    private val displayHeight: Int
    private var offsetX = 0f
    private var offsetY = 0f
    private var flagActionMove = false
    
    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowMetrics: WindowMetrics = windowManager.currentWindowMetrics
            val bounds = windowMetrics.bounds
            displayWidth = bounds.width()
            displayHeight = bounds.height()
        } else {
            val displayMetrics = DisplayMetrics()
            windowManager.defaultDisplay.getMetrics(displayMetrics)
            displayWidth = displayMetrics.widthPixels
            displayHeight = displayMetrics.heightPixels
        }
    }

    override fun onTouch(view: View, event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                flagActionMove = false
                offsetX = minOf(maxOf(0f, params.x - event.rawX), displayWidth)
                offsetY = minOf(maxOf(0f, params.y - event.rawY), displayHeight)
                return false
            }

            MotionEvent.ACTION_MOVE -> {
                flagActionMove = true
                params.x = minOf(maxOf(0f, (event.rawX + offsetX).toInt()), displayWidth)
                params.y = minOf(maxOf(0f, (event.rawY + offsetY).toInt()), displayHeight)
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