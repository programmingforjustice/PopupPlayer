package nl.blauw.pipplayer

import android.content.Context
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.DefaultLoadControl
import com.google.android.exoplayer2.DefaultRenderersFactory
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.util.Util

interface PlayerFactory {
    fun create(contentUrl: String): Player
}

class DefaultPlayerFactory(
    private val context: Context
) : PlayerFactory {
    
    override fun create(contentUrl: String): Player {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                1000, // 최소 버퍼
                2000, // 최대 버퍼
                250,  // 재생 시작 전 버퍼
                500   // 재버퍼링 후 버퍼
            ).build()

        val renderersFactory = DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)
            //.setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)

        val player = ExoPlayer.Builder(context, renderersFactory)
            .setLoadControl(loadControl)
            .build().run { PlayerWrapper(this) }
        loadMediaSource(player, contentUrl)
        return player
    }

   private fun loadMediaSource(player: Player, contentUrl: String) {
        val dataSourceFactory = DefaultDataSourceFactory(
            context, Util.getUserAgent(context, context.getString(R.string.app_name))
        )
        val contentMediaSource: MediaSource = ProgressiveMediaSource.Factory(
            dataSourceFactory,
            DefaultExtractorsFactory()
        ).createMediaSource(MediaItem.fromUri(contentUrl))

        (player as PlayerWrapper).apply {
          enableExoPlayerFeatures().setMediaSource(contentMediaSource)
          this.contentUrl = contentUrl
        }
    }    
}

