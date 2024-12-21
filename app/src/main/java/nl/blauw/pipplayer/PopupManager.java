
package nl.blauw.pipplayer;

import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Build;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.View;
import android.widget.ImageButton;

import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.util.RepeatModeUtil;

public class PopupManager {

    private static final int MAX_POPUP_WIDTH = 400;
    private static final int MAX_POPUP_HEIGHT = 400;

    private static final int DEFAULT_POPUP_X = 100;
    private static final int DEFAULT_POPUP_Y = 200;
    
    private static final int CONTROLLER_SHOW_TIMEOUT = 2500;

    private static final int DEFAULT_WINDOW_FLAGS = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;

    private final Context context;
    private final SimpleExoPlayer player;
    private WindowManager windowManager;
    private PlayerView playerView;

    public PopupManager(Context context, SimpleExoPlayer player) {
        this.context = context;
        this.player = player;
    }

    public void createPopupWindow() {
        playerView = new PlayerView(context);
        playerView.setPlayer(player);
        playerView.setKeepScreenOn(true);
        playerView.setControllerShowTimeoutMs(CONTROLLER_SHOW_TIMEOUT);
        //playerView.setRepeatToggleModes(RepeatModeUtil.REPEAT_TOGGLE_MODE_ONE);
        // Ensure touch on PlayerView shows controls
        // playerView.setOnTouchListener((view, motionEvent) -> {
        //     playerView.showController();
        //     return false; // Allow default behavior (like toggling play/pause on tap)
        // });
        
        player.addListener(new Player.Listener() {
            @Override
            public void onVideoSizeChanged(VideoSize videoSize) {
                int width = videoSize.width;
                int height = videoSize.height;
                if (width > 0 && height > 0) {
                    double scaleFactor = (double) width / height;
                    playerView.setTag(scaleFactor); // 비율 정보를 PlayerView에 저장
                    Log.d("PlayerManager", "ScaleFactor: " + scaleFactor);
                }
            }
        });

        windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
            Utils.convertDpToPixelsInt(160, context),
            Utils.convertDpToPixelsInt(90, context),
            WindowManager.LayoutParams.TYPE_PHONE,
            DEFAULT_WINDOW_FLAGS,
            PixelFormat.TRANSLUCENT
        );

        // Android 버전별 팝업 타입 설정
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            params.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            params.type = WindowManager.LayoutParams.TYPE_TOAST;
        }

        // 팝업 윈도우 초기 위치 설정
        params.gravity = Gravity.TOP | Gravity.LEFT;
        params.x = DEFAULT_POPUP_X;
        params.y = DEFAULT_POPUP_Y;
        
        //setupCrossButton(playerView);

        setupTouchListeners(playerView, params);

        windowManager.addView(playerView, params);
    }
    
    private void setupCrossButton(PlayerView playerView) {
        ImageButton crossButton = playerView.findViewById(R.id.cross_button);
        if (crossButton != null) {
            crossButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (playerView.getParent() != null) {
                        windowManager.removeViewImmediate(playerView);
                        player.release(); // 플레이어 리소스 해제
                    }
                }
            });
        }
    }

    private void setupTouchListeners(PlayerView view, WindowManager.LayoutParams params) {
        view.setOnTouchListener(new PlayerTouchListener(context, windowManager, params));
    }

    public void removePopupWindow() {
        if (windowManager != null && playerView != null) {
            windowManager.removeViewImmediate(playerView);
        }
    }
    
    public PlayerView getPlayerView() {
      return playerView;
    }
}
