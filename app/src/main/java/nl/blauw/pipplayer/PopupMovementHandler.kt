package nl.blauw.pipplayer

import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager

class PopupMovementHandler(
    private val context: Context,
    private val windowManager: WindowManager,
    private val params: WindowManager.LayoutParams
) : View.OnTouchListener {

    // System-defined minimum distance a finger must travel before a gesture is
    // recognised as a drag rather than a tap. Using this instead of a hard-coded
    // constant ensures consistent behaviour across all OEMs and touch-sampling rates.
    private val touchSlop: Float = ViewConfiguration.get(context).scaledTouchSlop.toFloat()

    private var displayWidth: Int = 0
    private var displayHeight: Int = 0
    private var offsetX = 0f
    private var offsetY = 0f
    private var initialRawX = 0f
    private var initialRawY = 0f
    private var isDragging = false

    init {
        updateDisplaySettings()
    }

    fun updateDisplaySettings() {
        getDisplayResolution(context).also { (width, height) ->
            displayWidth = width
            displayHeight = height
        }
    }

    override fun onTouch(view: View, event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                updateDisplaySettings()
                isDragging   = false
                initialRawX  = event.rawX
                initialRawY  = event.rawY
                offsetX = minOf(params.x, displayWidth  - params.width)  - event.rawX
                offsetY = minOf(params.y, displayHeight - params.height) - event.rawY
                return false  // let DOWN propagate so child click listeners arm themselves
            }

            MotionEvent.ACTION_MOVE -> {
                if (!isDragging) {
                    val dx = event.rawX - initialRawX
                    val dy = event.rawY - initialRawY
                    // Ignore micro-movements below the system touch-slop threshold.
                    // High-sampling-rate or sensitive hardware (Samsung, etc.) fires
                    // MOVE events with sub-pixel deltas on a plain tap; without this
                    // guard every tap would be misclassified as a drag.
                    if (dx * dx + dy * dy < touchSlop * touchSlop) return false
                    isDragging = true
                }
                params.x = minOf(maxOf(0, (event.rawX + offsetX).toInt()), displayWidth  - params.width)
                params.y = minOf(maxOf(0, (event.rawY + offsetY).toInt()), displayHeight - params.height)
                windowManager.updateViewLayout(view, params)
                return true
            }

            MotionEvent.ACTION_UP -> {
                // Only consume UP when a real drag occurred. If movement stayed
                // within touch-slop (i.e. a tap), return false so the UP event
                // reaches child click listeners and shows the control overlay.
                return isDragging
            }
        }
        return false
    }
}