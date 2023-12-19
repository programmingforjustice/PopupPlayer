package nl.blauw.pipplayer;


import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.view.WindowManager;
import android.view.LayoutInflater;
import android.util.Log;

import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
//import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelector;
//import com.google.android.exoplayer2.ui.StyledPlayerView;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.util.RepeatModeUtil;
import androidx.constraintlayout.widget.ConstraintLayout;

import android.widget.Toast;
import android.os.Looper;

import android.os.Handler;

public class PlayerService extends Service {

    SimpleExoPlayer player          = null;
    WindowManager windowManager     = null;
    PlayerView simpleExoPlayerView  = null;

    final String TAG = PlayerService.class.getSimpleName();

    public PlayerService() {

    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }

    private void addPopupWindow() {
        try {
            windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    Utils.convertDpToPixelsInt(300, getApplicationContext()),
                    Utils.convertDpToPixelsInt(169, getApplicationContext()),
                    WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                params.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;

            } else {
                params.type = WindowManager.LayoutParams.TYPE_TOAST;
	    }
            params.gravity = Gravity.TOP | Gravity.LEFT;
            params.x = 100;
            params.y = 200;

            //simpleExoPlayerView = new StyledPlayerView(getApplicationContext());
            simpleExoPlayerView = new PlayerView(getApplicationContext());

	    ImageButton crossButton = (ImageButton)simpleExoPlayerView.findViewById(R.id.cross_button);
	    crossButton.setTag(simpleExoPlayerView);

	    crossButton.setOnClickListener(new View.OnClickListener() { 
            @Override
            public void onClick(View view) 
            {            
		PlayerView playerView = (PlayerView)view.getTag();	    
		Player player = (Player)playerView.getPlayer();
		player.release();	
		
	        windowManager.removeViewImmediate(playerView);
	    } 
        }); 

/* View view = LayoutInflater.from(getApplicationContext()).inflate(R.layout.exoplayer_popup, null, false);
 Log.e(TAG, "View = " + view);
 simpleExoPlayerView = (PlayerView)view.getRootView();*/

            
           /* LayoutInflater inflator = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
    
	    ConstraintLayout layout = (ConstraintLayout)inflator.inflate(R.layout.exoplayer_popup, null);
	    
	    Log.d(TAG, "ConstraintLayout = " + layout);



            simpleExoPlayerView = (PlayerView)layout.findViewById(R.id.video_player_view);*/
	    //Log.e(TAG, "PlayerView = " + simpleExoPlayerView);
            
            simpleExoPlayerView.setKeepScreenOn(true);
            simpleExoPlayerView.setLayoutParams(params);
            simpleExoPlayerView.setOnTouchListener(new ListenerImpl());
            //simpleExoPlayerView.setShowNextButton(true);
            //simpleExoPlayerView.setShowPreviousButton(true);
            simpleExoPlayerView.setRepeatToggleModes(RepeatModeUtil.REPEAT_TOGGLE_MODE_ONE);
	    simpleExoPlayerView.setControllerShowTimeoutMs(2500);
	    /*ImageButton closeButton = (ImageButton)simpleExoPlayerView.findViewById(R.id.exo_video_close_button);
	    closeButton.setOnClickListener(new View.OnClickListener() {
	        @Override
	        public void onClick(View view) {
	          if (view != null && view.isEnabled()) {
	            windowManager.remove(view);
	          }
	        }
	    });*/
	    
            windowManager.addView(simpleExoPlayerView, params);
        } catch (Exception e) {
            Utils.LogData(false, TAG, e.getMessage());
        } 
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
            addPopupWindow();
            final String url = intent.getStringExtra("data");
	    /*new Handler(Looper.getMainLooper()).post(new Runnable() {

    @Override
    public void run() {
            Toast.makeText(VideoPlayerOverviewService.this.getApplicationContext(),url,Toast.LENGTH_SHORT).show();
            }
        });*/
            initializePlayer(url);
        return START_NOT_STICKY;
    }

    void initializePlayer(String contentUrl) {
        // Create a default track selector.
        TrackSelection.Factory videoTrackSelectionFactory = new AdaptiveTrackSelection.Factory();
        TrackSelector trackSelector = new DefaultTrackSelector(videoTrackSelectionFactory);
        // Create a player instance.
        player = ExoPlayerFactory.newSimpleInstance(getApplicationContext(), trackSelector);
        // Bind the player to the view.
	Log.e(TAG, "PlayerView = " + simpleExoPlayerView);
	Log.e(TAG, "Player = " + player);
        simpleExoPlayerView.setPlayer(player);
        // Produces DataSource instances through which media data is loaded.
        DataSource.Factory dataSourceFactory = new DefaultDataSourceFactory(getApplicationContext(), Util.getUserAgent(getApplicationContext(), getString(R.string.app_name)));
        // This is the MediaSource representing the content media (i.e. not the ad).
        MediaSource contentMediaSource =
                new ExtractorMediaSource.Factory(dataSourceFactory)
                        .createMediaSource(Uri.parse(contentUrl));

	/*player.addMediaItem(MediaItem.fromUri("file:///storage/emulated/0/Movies/Hommage van Lissa Meyvis aan het Toon Hermans Huis van Sittard.mp4"));
	player.addMediaItem(MediaItem.fromUri("/storage/emulated/0/Movies/Floor Jansen - Storm in a Glass (Live).mp4"));
	player.addMediaItem(MediaItem.fromUri("/storage/emulated/0/Movies/Ja zuster, nee zuster.mp4"));
	player.addMediaItem(MediaItem.fromUri("/storage/emulated/0/Movies/Ja zuster, nee zuster -  samen met u onder een paraplu.mp4"));*/
        player.prepare(contentMediaSource);
        //player.prepare();
	player.setRepeatMode(Player.REPEAT_MODE_ALL);
        player.seekTo(0L);
        player.setPlayWhenReady(true);
    }

private class ListenerImpl implements View.OnTouchListener { 
  
    float offsetX;
    float offsetY;

    int originalXPos;
    int originalYPos;

    //boolean moving;
    //boolean isResizing;
    
    private boolean isMoving = false;
    private boolean moving = false;
    
    private int initialPopupX = -1;
    private int initialPopupY = -1;
    private boolean isResizing = false;

    // initial coordinates and distance between fingers
    private double initPointerDistance = -1.0;
    private double initFirstPointerX = -1f;
    private double initFirstPointerY = -1f;
    private double initSecPointerX = -1f;
    private double initSecPointerY = -1f;
   
    PlayerView simpleExoPlayerView;
    WindowManager.LayoutParams params;
    
    private double hypot(double a, double b) {
      return Math.sqrt(Math.pow(a,2) + Math.pow(b,2));
    }
    
    private double getMinimumVideoHeight(final double width) {
        return width / (16.0 / 9.0); // Respect the 16:9 ratio that most videos have
    }
    
    @Override
    public boolean onTouch(View view, MotionEvent event) {
	    simpleExoPlayerView = (PlayerView)view;
	    params = (WindowManager.LayoutParams)
	    simpleExoPlayerView.getLayoutParams();
	    
      if (event.getPointerCount() == 2 && !isMoving && !isResizing) {
          // record coordinates of fingers
          initFirstPointerX = event.getX(0);
          initFirstPointerY = event.getY(0);
          initSecPointerX = event.getX(1);
          initSecPointerY = event.getY(1);
          // record distance between fingers
          initPointerDistance = hypot(
              initFirstPointerX - initSecPointerX,
              initFirstPointerY - initSecPointerY
          );

          isResizing = true;
      }
      
      if (event.getAction() == MotionEvent.ACTION_MOVE && !isMoving && isResizing) {
        return handleMultiDrag(event);
      }
      
      if (event.getAction() == MotionEvent.ACTION_UP) {
          if (isMoving) {
              isMoving = false;
              //onScrollEnd(event);
          }
          if (isResizing) {
              isResizing = false;

              initPointerDistance = -1;
              initFirstPointerX = -1;
              initFirstPointerY = -1;
              initSecPointerX = -1;
              initSecPointerY = -1;

              //onPopupResizingEnd();
              //player.changeState(player.currentState);
          }
          /*if (!playerUi.isPopupClosing) {
              playerUi.savePopupPositionAndSizeToPrefs()
          }*/
      }
    
	    
	    
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            int[] topLeftLocationOnScreen = new int[2];
            simpleExoPlayerView.getLocationOnScreen(topLeftLocationOnScreen);
            moving = false;
            offsetX = topLeftLocationOnScreen[0] - event.getRawX();
            offsetY = topLeftLocationOnScreen[1] - event.getRawY();
        } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
            WindowManager.LayoutParams params = (WindowManager.LayoutParams) simpleExoPlayerView.getLayoutParams();
            int newX = (int) (offsetX + event.getRawX());
            int newY = (int) (offsetY + event.getRawY());
            if (Math.abs(newX - originalXPos) < 1 && Math.abs(newY - originalYPos) < 1 && !moving) {
                return false;
            }
            params.x = newX;
            params.y = newY;
            windowManager.updateViewLayout(simpleExoPlayerView, params);
            moving = true;
        }
        return false;
    }
    
    private boolean handleMultiDrag(MotionEvent event){
        if (initPointerDistance == -1.0 || event.getPointerCount() != 2) {
            return false;
        }

        // get the movements of the fingers
        double firstPointerMove = hypot(
            event.getX(0) - initFirstPointerX,
            event.getY(0) - initFirstPointerY
        );
        double secPointerMove = hypot(
            event.getX(1) - initSecPointerX,
            event.getY(1) - initSecPointerY
        );

        // minimum threshold beyond which pinch gesture will work
        /*val minimumMove = ViewConfiguration.get(player.context).scaledTouchSlop
        if (max(firstPointerMove, secPointerMove) <= minimumMove) {
            return false
        }*/

        // calculate current distance between the pointers
        double currentPointerDistance = hypot(
            (double)event.getX(0) - event.getX(1),
            (double)event.getY(0) - event.getY(1)
        );

        //double popupWidth = playerUi.popupLayoutParams.width.toDouble()
        double popupWidth = params.width;
        // change co-ordinates of popup so the center stays at the same position
        double newWidth = popupWidth * currentPointerDistance /
        initPointerDistance;
        initPointerDistance = currentPointerDistance;
        params.x += (int)((popupWidth - newWidth) / 2.0);

        // lplayerUi.checkPopupPositionBounds()
        //playerUi.updateScreenSize()
        //playerUi.changePopupSize(min(playerUi.screenWidth.toDouble(), newWidth).toInt())
        
        final int actualWidth = (int)newWidth;
        final int actualHeight = (int) getMinimumVideoHeight(newWidth);

        params.width = actualWidth;
        params.height = actualHeight;
        //binding.surfaceView.setHeights(popupLayoutParams.height, popupLayoutParams.height);
        windowManager.updateViewLayout(simpleExoPlayerView, params);
        
        return true;
    }
}
}
