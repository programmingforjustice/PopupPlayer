
package nl.blauw.pipplayer;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

public class PlayerService extends Service {

    private PlayerManager playerManager;
    private PopupManager popupManager;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String url = intent.getStringExtra("data");

        // PlayerManager와 PopupManager 초기화 및 실행
        playerManager = new PlayerManager(this);
        playerManager.createPlayer(url);

        popupManager = new PopupManager(this, playerManager.getPlayer());
        popupManager.createPopupWindow();

        playerManager.loadMediaSource(url);
        playerManager.play(popupManager.getPlayerView());
        
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        if (playerManager != null) playerManager.releasePlayer();
        if (popupManager != null) popupManager.removePopupWindow();
        super.onDestroy();
    }
}
