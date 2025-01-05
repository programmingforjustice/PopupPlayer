
package nl.blauw.pipplayer;

import android.content.Context;
import android.view.ViewGroup;
import android.util.Log;

import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.video.VideoSize;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.util.Util;
import android.view.WindowManager;

public class PlayerManager {

    private final Context context;
    private SimpleExoPlayer player;

    public PlayerManager(Context context) {
        this.context = context;
    }
    
    public void createPlayer() {
        /*DefaultTrackSelector trackSelector = new DefaultTrackSelector(context);
        player = new SimpleExoPlayer.Builder(context).setTrackSelector(trackSelector).build();*/
        
        DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                1000, // 최소 버퍼
                2000, // 최대 버퍼
                250,  // 재생 시작 전 버퍼
                500   // 재버퍼링 후 버퍼
            )
            .build();

        // RenderersFactory 설정
        DefaultRenderersFactory renderersFactory = new DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true);

        // ExoPlayer 초기화
        player = new ExoPlayer.Builder(context, renderersFactory)
            .setLoadControl(loadControl)
            .build();
    }

    public void loadMediaSource(String contentUrl) {
        MediaSource contentMediaSource = new ProgressiveMediaSource.Factory(
            new DefaultDataSourceFactory(context, Util.getUserAgent(context, context.getString(R.string.app_name))),
            new DefaultExtractorsFactory()
        ).createMediaSource(MediaItem.fromUri(contentUrl));
        
        player.setMediaSource(contentMediaSource);
    }
    
    public void play(PlayerView playerView) {
        // 비율 계산 이벤트 등록
        player.addListener(new Player.Listener() {
            @Override
            public void onVideoSizeChanged(VideoSize videoSize) {
                int width = videoSize.width;
                int height = videoSize.height;
                if (width > 0 && height > 0) {
                    double scaleFactor = (double) width / height;
                    playerView.setTag(scaleFactor); // 비율 정보를 PlayerView에 저장
                    Log.d("PlayerManager", "ScaleFactor: " + scaleFactor);
                    
                    WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
                    
                    WindowManager.LayoutParams params = (WindowManager.LayoutParams) playerView.getLayoutParams();

                    params.width = width;
                    params.height = height;
                    
                    //playerView.setLayoutParams(params);
                    
                    windowManager.updateViewLayout(playerView, params);
                }
            }
        });

        player.prepare();
        player.setRepeatMode(Player.REPEAT_MODE_ALL);
        player.setPlayWhenReady(true);
    }

    public SimpleExoPlayer getPlayer() {
        return player;
    }

    public void releasePlayer() {
        if (player != null) {
            player.release();
            player = null;
        }
    }
}
