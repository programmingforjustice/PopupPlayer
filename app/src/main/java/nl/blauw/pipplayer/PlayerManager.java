
package nl.blauw.pipplayer;

import android.content.Context;
import android.util.Log;

import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.video.VideoSize;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.util.Util;

public class PlayerManager {

    private final Context context;
    private SimpleExoPlayer player;

    public PlayerManager(Context context) {
        this.context = context;
    }

    public void initializePlayer(String contentUrl) {
        DefaultTrackSelector trackSelector = new DefaultTrackSelector(context);
        player = new SimpleExoPlayer.Builder(context).setTrackSelector(trackSelector).build();

        MediaSource contentMediaSource = new ProgressiveMediaSource.Factory(
            new DefaultDataSourceFactory(context, Util.getUserAgent(context, context.getString(R.string.app_name))),
            new DefaultExtractorsFactory()
        ).createMediaSource(MediaItem.fromUri(contentUrl));

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
                }
            }
        });

        player.setMediaSource(contentMediaSource);
        player.prepare();
        //player.setRepeatMode(Player.REPEAT_MODE_ALL);
        player.setPlayWhenReady(true);
        // playerView.setPlayer(player);
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
