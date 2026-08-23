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
import com.idlix.android.config.AppConfig
import com.idlix.android.data.model.SubtitleTrack
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
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
        
        @Suppress("DEPRECATION", "UNCHECKED_CAST")
        val subtitles = (if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra(EXTRA_SUBTITLES, ArrayList::class.java) as? ArrayList<SubtitleTrack>
        } else {
            intent.getSerializableExtra(EXTRA_SUBTITLES) as? ArrayList<SubtitleTrack>
        }) ?: arrayListOf()

        if (streamUrl.isNullOrEmpty()) {
            Toast.makeText(this as Context, "Invalid stream URL", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        initializePlayer(streamUrl, subtitles)
    }

    private fun initializePlayer(streamUrl: String, subtitles: List<SubtitleTrack>) {
        val defaultHeaders = mapOf(
            "User-Agent" to "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36",
            "Referer" to "${AppConfig.WEBSITE_BASE_URL}/",
            "Origin" to AppConfig.WEBSITE_BASE_URL
        )

        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
            .setDefaultRequestProperties(defaultHeaders)
            .setAllowCrossProtocolRedirects(true)

        val mediaItemBuilder = MediaItem.Builder()
            .setUri(Uri.parse(streamUrl))

        // Force HLS MimeType for non-MP4 streams so ExoPlayer doesn't default to Progressive MP4 parser
        if (!streamUrl.endsWith(".mp4", ignoreCase = true) && !streamUrl.contains(".mp4?")) {
            mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
        }

        val validSubtitles = subtitles.filter { it.url.startsWith("http") }
        if (validSubtitles.isNotEmpty()) {
            val subtitleConfigs = validSubtitles.map { subTrack ->
                val mimeType = when {
                    subTrack.url.endsWith(".srt", ignoreCase = true) || subTrack.url.contains(".srt?") -> MimeTypes.APPLICATION_SUBRIP
                    else -> MimeTypes.TEXT_VTT
                }

                val isIndo = subTrack.lang.lowercase().contains("id") || 
                             subTrack.label.lowercase().contains("indo") || 
                             subTrack.label.lowercase().contains("id")

                MediaItem.SubtitleConfiguration.Builder(Uri.parse(subTrack.url))
                    .setMimeType(mimeType)
                    .setLanguage(subTrack.lang.ifEmpty { "id" })
                    .setLabel(subTrack.label.ifEmpty { "Indonesia" })
                    .setSelectionFlags(if (isIndo) C.SELECTION_FLAG_DEFAULT else 0)
                    .build()
            }
            mediaItemBuilder.setSubtitleConfigurations(subtitleConfigs)
        }

        val mediaItem = mediaItemBuilder.build()
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
        val mediaSource = mediaSourceFactory.createMediaSource(mediaItem)

        exoPlayer = ExoPlayer.Builder(this as Context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build().apply {
                setMediaSource(mediaSource)
                prepare()
                playWhenReady = true
                addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        if (validSubtitles.isNotEmpty()) {
                            Toast.makeText(this@PlayerActivity as Context, "Subtitle network error, falling back to main stream...", Toast.LENGTH_SHORT).show()
                            exoPlayer?.release()
                            exoPlayer = null
                            initializePlayer(streamUrl, emptyList())
                            return
                        }
                        val causeMsg = error.cause?.message ?: error.message
                        Toast.makeText(this@PlayerActivity as Context, "Playback error: $causeMsg", Toast.LENGTH_LONG).show()
                    }
                })
            }

        playerView?.apply {
            player = exoPlayer
            subtitleView?.setUserDefaultStyle()
            subtitleView?.setUserDefaultTextSize()
        }
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
