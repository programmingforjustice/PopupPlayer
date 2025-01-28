package nl.blauw.pipplayer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.view.WindowManager
import android.graphics.PixelFormat
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import com.google.android.exoplayer2.ExoPlayer

class PlayerService : Service() {

    companion object {
        const val ACTION_START_PIP = "nl.blauw.pipplayer.ACTION_START_PIP"
        const val ACTION_SAVE_CURRENT_PLAYLIST = "nl.blauw.pipplayer.SAVE_CURRENT_PLAYLIST"
        const val ACTION_RESTORE_CURRENT_PLAYLIST = "nl.blauw.pipplayer.RESTORE_CURRENT_PLAYLIST"
        const val COMMAND = "command"
        
        private const val CHANNEL_ID = "PopupPlayerChannel"
    }

    //private var playerController: PlayerController? = null
    //private var popupManager: PopupManager? = null
    private var playerList: MutableList<PlayerController> = mutableListOf()

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
            
            var playerController = PlayerController(this).apply {
              initialize(url)
            }
            var popupManager = PopupManager(this, playerController.getPlayerManager(), playerController.getPlayerViewManager())
            
            popupManager.show()
            playerController.apply {
              play()
              playerList.add(this) 
              onReleaseResources = { popupManager.removePopupWindow()
              }
            }
          }
          ACTION_SAVE_CURRENT_PLAYLIST -> {
            saveCurrentPlayList()
          }
          ACTION_RESTORE_CURRENT_PLAYLIST -> {
            restorePlayList()
          }
          else -> {
            
          }
        }
        
    
        
        //Toast.makeText(this, "$url", Toast.LENGTH_SHORT).show()

        
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        playerList
          .filter{ controller ->
            //var player = (controller.getPlayerManager().getPlayer() as ExoPlayer)
            var player = controller.getPlayerManager().getPlayer() as PlayerWrapper
            player.isReleased == false
          }
          .forEach { controller -> 
            controller.releaseResources() 
          }
        playerList = mutableListOf()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
    
    fun saveCurrentPlayList() {
      var jsonStringForPlayetList = playerList/*.filter{  }*/.map{ it.toJsonString() }.joinToString(",", "[", "]")
      
      savePlayerListToFile(this,jsonStringForPlayetList)
    }
    
    fun restorePlayList() {
      var jsonStringForPlayetList: String? = readPlayerListFromFile(this)
      jsonStringForPlayetList?.let {
        val jsonArray = JSONArray(it)

        // 각 객체의 "id" 값을 읽기
        for (i in 0 until jsonArray.length()) {
            val playerInfo: JSONObject = jsonArray.getJSONObject(i)
            val playerController = PlayerController(this).apply {
                initialize(playerInfo.getString("mediaPath"))
              }            
              
            val player = playerController.getPlayerManager().getPlayer()
            player.seekTo(playerInfo.getLong("currentPosition"))
            //player.isPlaying = playerInfo.getBoolean("isPlaying")
            
            val layoutParams = WindowManager.LayoutParams().apply {
              x = playerInfo.getInt("x")
              y = playerInfo.getInt("y")
              width = playerInfo.getInt("width")
              height = playerInfo.getInt("height")
              type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                  WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
              else
                  WindowManager.LayoutParams.TYPE_TOAST
              flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
              format = PixelFormat.TRANSLUCENT
            }
            
            val playerView = playerController.getPlayerViewManager().getPlayerView()
            playerView.layoutParams = layoutParams
            
            val popupManager = PopupManager(this, playerController.getPlayerManager(), playerController.getPlayerViewManager())
            popupManager.show(layoutParams)
            playerController.play()
            playerController.onReleaseResources = { popupManager.removePopupWindow()
              }
            playerList.add(playerController)
        }
      }
    }
  
    fun savePlayerListToFile(context: Context, data: String) {
        // 파일 이름 정의
        val fileName = "playerList.txt"
    
        // 앱 전용 디렉토리에 파일 생성 및 데이터 저장
        try {
            context.openFileOutput(fileName, Context.MODE_PRIVATE).use { outputStream ->
                outputStream.write(data.toByteArray())
            }
            println("File saved successfully to: ${context.filesDir}/$fileName")
        } catch (e: Exception) {
            e.printStackTrace()
            println("Failed to save file.")
        }
    }
    
    fun readPlayerListFromFile(context: Context): String? {
        val fileName = "playerList.txt"
        return try {
            context.openFileInput(fileName).bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

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