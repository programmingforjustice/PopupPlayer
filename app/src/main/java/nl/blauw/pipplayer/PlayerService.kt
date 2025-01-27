package nl.blauw.pipplayer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.widget.Toast

class PlayerService : Service() {

    companion object {
        const val ACTION_START_PIP = "nl.blauw.pipplayer.ACTION_START_PIP"
        const val ACTION_SAVE_CURRENT_PLAYLIST = "nl.blauw.pipplayer.SAVE_CURRENT_PLAYLIST"
        const val ACTION_RESTORE_CURRENT_PLAYLIST = "nl.blauw.pipplayer.RESTORE_CURRENT_PLAYLIST"
        const val COMMAND = "command"
        
        private const val CHANNEL_ID = "PopupPlayerChannel"
    }

    private var playerController: PlayerController? = null
    private var popupManager: PopupManager? = null
    private var playerList = mutableListOf<PlayerController>()

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
        
        val command = intent?.getStringExtra(COMMAND)
        when (command) {
          ACTION_START_PIP -> {
            val url = intent?.getStringExtra("data") ?: return START_NOT_STICKY
            
            playerController = PlayerController(this)?.apply {
              initialize(url)
              play()
              playerList.add(this) 
            }
          }
          ACTION_SAVE_CURRENT_PLAYLIST -> {
            
          }
          ACTION_RESTORE_CURRENT_PLAYLIST -> {
            
          }
          else -> {
            
          }
        }
        
    
        
        //Toast.makeText(this, "$url", Toast.LENGTH_SHORT).show()

        
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        playerList.forEach { controller -> controller.releaseResources() }
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