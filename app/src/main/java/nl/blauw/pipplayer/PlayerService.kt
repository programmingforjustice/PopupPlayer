package nl.blauw.pipplayer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.widget.Toast;

class PlayerService : Service() {

    companion object {
        private const val CHANNEL_ID = "PopupPlayerChannel"
    }

    private var playerController: PlayerController? = null
    private var popupManager: PopupManager? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("PopupPlayer Service 실행 중")
            .setContentText("Foreground Service가 실행 중입니다.")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .build();
        startForeground(1, notification);
        
    
        val url = intent?.getStringExtra("data") ?: return START_NOT_STICKY
        
        Toast.makeText(this, "$url", Toast.LENGTH_SHORT).show()

        playerController = PlayerController(this)
        playerController?.initialize(url)

        popupManager = PopupManager(this, playerController?.getPlayerManager() ?: throw IllegalStateException("cannot obtain PlayerManager"), playerController?.getPlayerViewManager() ?: throw IllegalStateException("cannot obtain PlayerViewManager"))
        popupManager?.prepare()

        playerController?.play()
        
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        playerController?.releaseResources()
        popupManager?.removePopupWindow()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "PopupPlayer Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }
}