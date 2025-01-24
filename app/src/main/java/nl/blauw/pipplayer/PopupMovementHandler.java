
package nl.blauw.pipplayer;

import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

public class PopupMovementHandler implements View.OnTouchListener {

    private final WindowManager windowManager;
    private final WindowManager.LayoutParams params;

    private float offsetX;
    private float offsetY;

    public PopupMovementHandler(Context context, WindowManager windowManager, WindowManager.LayoutParams params) {
        this.windowManager = windowManager;
        this.params = params;
    }

    @Override
    public boolean onTouch(View view, MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                offsetX = params.x - event.getRawX();
                offsetY = params.y - event.getRawY();
                return false;

            case MotionEvent.ACTION_MOVE:
                params.x = (int) (event.getRawX() + offsetX);
                params.y = (int) (event.getRawY() + offsetY);
                windowManager.updateViewLayout(view, params);
                return true;
        }
        return false;
    }
}
