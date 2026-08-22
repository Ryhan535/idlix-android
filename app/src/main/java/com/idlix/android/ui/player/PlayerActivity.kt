package com.idlix.android.ui.player

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.ui.PlayerView

@OptIn(UnstableApi::class)
class PlayerActivity : ComponentActivity() {

    private var playerView: PlayerView? = null
    private var exoPlayer: ExoPlayer? = null

    companion object {
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_STREAM_URL = "extra_stream_url"
        const val EXTRA_SUBTITLES = "extra_subtitles"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set full screen flag
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )

        playerView = PlayerView(this).apply {
            keepScreenOn = true
            useController = true
        }
        setContentView(playerView)

        val streamUrl = intent.getStringExtra(EXTRA_STREAM_URL)
        val title = intent.getStringExtra(EXTRA_TITLE)
        val subtitles = intent.getStringArrayListExtra(EXTRA_SUBTITLES) ?: arrayListOf()

        if (streamUrl.isNullOrEmpty()) {
            Toast.makeText(this as Context, "Invalid stream URL", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        initializePlayer(streamUrl, subtitles)
    }

    private fun initializePlayer(streamUrl: String, subtitleUrls: List<String>) {
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36")
            .setAllowCrossProtocolRedirects(true)

        val mediaItemBuilder = MediaItem.Builder()
            .setUri(Uri.parse(streamUrl))

        // Add WebVTT subtitles if available
        if (subtitleUrls.isNotEmpty()) {
            val subtitleConfigs = subtitleUrls.map { subUrl ->
                MediaItem.SubtitleConfiguration.Builder(Uri.parse(subUrl))
                    .setMimeType(MimeTypes.TEXT_VTT)
                    .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                    .build()
            }
            mediaItemBuilder.setSubtitleConfigurations(subtitleConfigs)
        }

        val mediaItem = mediaItemBuilder.build()
        val hlsMediaSource = HlsMediaSource.Factory(dataSourceFactory)
            .createMediaSource(mediaItem)

        exoPlayer = ExoPlayer.Builder(this as Context).build().apply {
            setMediaSource(hlsMediaSource)
            prepare()
            playWhenReady = true
            addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    Toast.makeText(this@PlayerActivity as Context, "Playback error: ${error.message}", Toast.LENGTH_LONG).show()
                }
            })
        }

        playerView?.player = exoPlayer
    }

    override fun onPause() {
        super.onPause()
        exoPlayer?.pause()
    }

    override fun onResume() {
        super.onResume()
        exoPlayer?.play()
    }

    override fun onDestroy() {
        super.onDestroy()
        exoPlayer?.release()
        exoPlayer = null
    }
}
