
package nl.blauw.pipplayer

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.content.Context
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import nl.blauw.pipplayer.databinding.FloatingButtonLayoutBinding
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import com.google.android.material.floatingactionbutton.FloatingActionButton

class FloatingButtonService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View

    override fun onCreate() {
        super.onCreate()
        
        val themedContext = ContextThemeWrapper(this, R.style.AppTheme)
        val inflater = LayoutInflater.from(themedContext)
        
       // val binding = FloatingButtonLayoutBinding.inflate(LayoutInflater.from(this))
        val binding = FloatingButtonLayoutBinding.inflate(inflater)
        floatingView = binding.root

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        layoutParams.gravity = Gravity.BOTTOM or Gravity.END
        layoutParams.x = 50
        layoutParams.y = 100

        binding.fab.setOnClickListener {
            Toast.makeText(this, "Floating 버튼 클릭됨", Toast.LENGTH_SHORT).show()
        }
        
        binding.fab.setOnTouchListener(object : View.OnTouchListener {
    var dX = 0f
    var dY = 0f

    override fun onTouch(view: View, event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                dX = view.x - event.rawX
                dY = view.y - event.rawY
            }
            MotionEvent.ACTION_MOVE -> {
                view.animate()
                    .x(event.rawX + dX)
                    .y(event.rawY + dY)
                    .setDuration(0)
                    .start()
            }
        }
        return true
    }
})

        windowManager.addView(floatingView, layoutParams)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::floatingView.isInitialized) windowManager.removeView(floatingView)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}