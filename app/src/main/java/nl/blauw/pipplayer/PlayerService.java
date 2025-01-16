
package nl.blauw.pipplayer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

public class PlayerService extends Service {
    private static final String CHANNEL_ID = "ForegroundServiceChannel";

    private PlayerManager playerManager;
    private PopupManager popupManager;
    
    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
      Notification notification = new Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("PopupPlayer Service 실행 중")
            .setContentText("Foreground Service가 실행 중입니다.")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .build();
        startForeground(1, notification);
      
        String url = intent.getStringExtra("data");

        // PlayerManager와 PopupManager 초기화 및 실행
        playerManager = new PlayerManager(this);
        playerManager.createPlayer();

        popupManager = new PopupManager(this, playerManager.getPlayer());
        popupManager.createPlayerView();
        popupManager.show();
        
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
    
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "PopupPlayer Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }
}
