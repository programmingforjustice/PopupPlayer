
package nl.blauw.pipplayer;

import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Build;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.View;
import android.widget.ImageButton;

import com.google.android.exoplayer2.Player;
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
    private final Player player;
    private WindowManager windowManager;
    private PlayerView playerView;
    
    //private boolean isMuted;
    //private ImageButton muteToggleButton;

    public PopupManager(Context context, Player player) {
        this.context = context;
        this.player = player;
    }
    
    public void createPlayerView() {
        playerView = new PlayerView(context);
        playerView.setPlayer(player);
        playerView.setKeepScreenOn(true);
        playerView.setControllerShowTimeoutMs(CONTROLLER_SHOW_TIMEOUT);
        playerView.setRepeatToggleModes(RepeatModeUtil.REPEAT_TOGGLE_MODE_ONE);
        
        setupCrossButton(playerView);
        setupMuteToggleButton(playerView);
    }

    public void show() {
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
    
    
    
    /*private void toggleMute() {
        isMuted = !isMuted; // Mute 상태를 반전
        player.setVolume(isMuted ? 0f : 1f); // Mute 시 볼륨을 0, Unmute 시 1
        updateButtonImage(); // 버튼 이미지 업데이트
    }

    private void updateButtonImage() {
        if (isMuted) {
            muteToggleButton.setImageResource(R.drawable.ic_mute); // Mute 이미지
        } else {
            muteToggleButton.setImageResource(R.drawable.ic_unmute); // Unmute 이미지
        }
    }*/

    
    private void setupMuteToggleButton(PlayerView playerView) {
        ImageButton muteToggleButton = playerView.findViewById(R.id.mute_toggle_button);
        if (muteToggleButton != null) {
            muteToggleButton.setOnClickListener(new MuteToggleButtonListener(player));
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
