package nl.blauw.pipplayer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder

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
        val url = intent?.getStringExtra("data") ?: return START_NOT_STICKY

        playerController = PlayerController(this)
        playerController.?initialize(url)

        popupManager = PopupManager(this, playerController.?getPlayerView())
        popupManager.?show()

        playerController.?play()

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