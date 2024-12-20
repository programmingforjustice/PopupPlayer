
package nl.blauw.pipplayer;

import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

import com.google.android.exoplayer2.ui.PlayerView;

public class PopupResizeHandler implements View.OnTouchListener {

    private static final int MAX_WIDTH = 400;
    private static final int MAX_HEIGHT = 400;

    private final WindowManager windowManager;
    private final WindowManager.LayoutParams params;

    private double initialPointerDistance = -1;
    private int initialWidth;
    private int initialHeight;

    public PopupResizeHandler(Context context, WindowManager windowManager, WindowManager.LayoutParams params) {
        this.windowManager = windowManager;
        this.params = params;
    }

    @Override
    public boolean onTouch(View view, MotionEvent event) {
        if (event.getPointerCount() == 2) {
            PlayerView playerView = (PlayerView) view;
            if (playerView.getTag() == null) return false;

            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_POINTER_DOWN:
                    // 멀티터치 시작
                    initialPointerDistance = calculateDistance(event);
                    initialWidth = params.width;
                    initialHeight = params.height;
                    break;

                case MotionEvent.ACTION_MOVE:
                    if (initialPointerDistance > 0) {
                        double currentDistance = calculateDistance(event);
                        double scale = currentDistance / initialPointerDistance;

                        // 새로운 가로 길이 계산
                        int newWidth = (int) (initialWidth * scale);

                        // 비율에 따라 새로운 세로 길이 계산
                        double scaleFactor = (double) playerView.getTag();
                        int newHeight = (int) (newWidth / scaleFactor);

                        // 크기 제한
                        //newWidth = Math.min(newWidth, MAX_WIDTH);
                        //newHeight = Math.min(newHeight, MAX_HEIGHT);

                        params.width = newWidth;
                        params.height = newHeight;

                        // 레이아웃 업데이트
                        windowManager.updateViewLayout(view, params);
                    }
                    break;

                case MotionEvent.ACTION_POINTER_UP:
                    // 멀티터치 종료
                    initialPointerDistance = -1;
                    break;
            }
        }
        return true;
    }

    private double calculateDistance(MotionEvent event) {
        float x = event.getX(0) - event.getX(1);
        float y = event.getY(0) - event.getY(1);
        return Math.sqrt(x * x + y * y);
    }
}
