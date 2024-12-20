
package nl.blauw.pipplayer;

import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

public class PlayerTouchListener implements View.OnTouchListener {

    private final PopupMovementHandler movementHandler;
    private final PopupResizeHandler resizeHandler;

    public PlayerTouchListener(Context context, WindowManager windowManager, WindowManager.LayoutParams params) {
        this.movementHandler = new PopupMovementHandler(context, windowManager, params);
        this.resizeHandler = new PopupResizeHandler(context, windowManager, params);
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if (event.getPointerCount() == 2) {
            return resizeHandler.onTouch(v, event); // 크기 조정
        } else {
            return movementHandler.onTouch(v, event); // 이동
        }
    }
}
