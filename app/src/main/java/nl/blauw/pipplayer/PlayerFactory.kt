package nl.blauw.pipplayer

import android.content.Context
import android.net.Uri
import java.io.File
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.DefaultLoadControl
import com.google.android.exoplayer2.DefaultRenderersFactory
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.audio.AudioAttributes
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
                2000,  // minBufferMs
                5000,  // maxBufferMs
                1500,  // bufferForPlaybackMs — was 250, too small; caused audio underruns and AV drift
                2000   // bufferForPlaybackAfterRebufferMs — was 500, too small
            ).build()

        val renderersFactory = DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)

        val exoPlayer = ExoPlayer.Builder(context, renderersFactory)
            .setLoadControl(loadControl)
            .build()

        // AUDIO_CONTENT_TYPE_MOVIE routes audio through the low-latency media path
        // and configures AudioTrack for accurate playback-head timestamps, which
        // ExoPlayer's video renderer uses as its sync clock.
        exoPlayer.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(),
            /* handleAudioFocus = */ false
        )

        val player = PlayerWrapper(exoPlayer)
        loadMediaSource(player, contentUrl)
        return player
    }

   private fun loadMediaSource(player: Player, contentUrl: String) {
        val dataSourceFactory = DefaultDataSourceFactory(
            context, Util.getUserAgent(context, context.getString(R.string.app_name))
        )
    
        val mediaUri = if (contentUrl.startsWith("/")) Uri.fromFile(File(contentUrl))
                       else Uri.parse(contentUrl)

        val contentMediaSource: MediaSource = ProgressiveMediaSource.Factory(
            dataSourceFactory,
            DefaultExtractorsFactory()
        ).createMediaSource(MediaItem.fromUri(mediaUri))

        (player as PlayerWrapper).apply {
          enableExoPlayerFeatures().setMediaSource(contentMediaSource)
          this.contentUrl = contentUrl
        }
    }    
}

