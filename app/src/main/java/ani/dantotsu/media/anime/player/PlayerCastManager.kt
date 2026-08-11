package ani.dantotsu.media.anime.player

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.SessionAvailabilityListener
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import ani.dantotsu.R
import ani.dantotsu.defaultHeaders
import ani.dantotsu.media.Media
import ani.dantotsu.media.anime.CustomCastButton
import ani.dantotsu.media.anime.CustomCastThemeFactory
import ani.dantotsu.parsers.Subtitle
import ani.dantotsu.parsers.Video
import ani.dantotsu.settings.saving.PrefManager
import ani.dantotsu.settings.saving.PrefName
import ani.dantotsu.snackString
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastContext
import java.util.concurrent.Executors

@UnstableApi
class PlayerCastManager(
    private val activity: AppCompatActivity,
    private val playerView: PlayerView,
    private val onCastStateChanged: (isPlaying: Boolean) -> Unit
) : SessionAvailabilityListener {

    var castPlayer: CastPlayer? = null
        private set
    var castContext: CastContext? = null
        private set
    var isCastApiAvailable = true
        private set

    private var currentMediaItem: MediaItem? = null
    private var exoPlayer: Player? = null

    init {
        initCastApi()
    }

    private fun initCastApi() {
        try {
            CastContext.getSharedInstance(activity, Executors.newSingleThreadExecutor())
                .addOnCompleteListener { task ->
                    if (task.isSuccessful && task.result != null) {
                        castContext = task.result
                        castPlayer = castContext?.let { CastPlayer(it) }
                        castPlayer?.setSessionAvailabilityListener(this)
                        setupCastPlayerListener()
                    } else {
                        isCastApiAvailable = false
                    }
                }
        } catch (e: Exception) {
            isCastApiAvailable = false
        }
    }

    private fun setupCastPlayerListener() {
        castPlayer?.addListener(object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                super.onPlayWhenReadyChanged(playWhenReady, reason)
                onCastStateChanged(playWhenReady)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                super.onIsPlayingChanged(isPlaying)
                onCastStateChanged(isPlaying)
            }
        })
    }

    fun setupCastButton(
        castButton: CustomCastButton,
        media: Media?,
        video: Video?,
        subtitle: Subtitle?,
        hasExtSubtitles: Boolean,
        episodeTitle: String?
    ) {
        if (!PrefManager.getVal<Boolean>(PrefName.Cast)) {
            castButton.visibility = View.GONE
            return
        }

        castButton.visibility = View.VISIBLE
        if (PrefManager.getVal(PrefName.UseInternalCast)) {
            try {
                CastButtonFactory.setUpMediaRouteButton(activity, castButton)
                castButton.dialogFactory = CustomCastThemeFactory()
            } catch (e: Exception) {
                isCastApiAvailable = false
            }
        } else {
            castButton.setCastCallback {
                castExternal(media, video, subtitle, hasExtSubtitles, episodeTitle)
            }
        }
    }

    fun updateCurrentMedia(mediaItem: MediaItem?, exoPlayer: Player?) {
        this.currentMediaItem = mediaItem
        this.exoPlayer = exoPlayer
    }

    fun isCasting(): Boolean = castPlayer?.isPlaying == true

    fun pause() {
        castPlayer?.pause()
    }

    fun play() {
        castPlayer?.play()
    }

    fun castExternal(
        media: Media?,
        video: Video?,
        subtitle: Subtitle?,
        hasExtSubtitles: Boolean,
        episodeTitle: String?
    ) {
        val videoURL = video?.file?.url ?: return
        val subtitleUrl = if (!hasExtSubtitles || subtitle == null) video.file.url else subtitle.file.url
        val shareVideo = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(videoURL.toUri(), "video/*")
            setPackage("com.instantbits.cast.webvideo")
            if (subtitle != null) putExtra("subtitle", subtitleUrl)
            putExtra(
                "title",
                (media?.userPreferredName ?: "") + " : " + (episodeTitle ?: "")
            )
            putExtra("poster", media?.cover)
            val headers = Bundle().apply {
                defaultHeaders.forEach { putString(it.key, it.value) }
                video.file.headers?.forEach { putString(it.key, it.value) }
            }
            putExtra("android.media.intent.extra.HTTP_HEADERS", headers)
            putExtra("secure_uri", true)
        }

        try {
            activity.startActivity(shareVideo)
        } catch (ex: ActivityNotFoundException) {
            try {
                activity.startActivity(
                    Intent(Intent.ACTION_VIEW, "market://details?id=com.instantbits.cast.webvideo".toUri())
                )
            } catch (e: Exception) {
                activity.startActivity(
                    Intent(Intent.ACTION_VIEW, "https://play.google.com/store/apps/details?id=com.instantbits.cast.webvideo".toUri())
                )
            }
        }
    }

    override fun onCastSessionAvailable() {
        if (isCastApiAvailable && !activity.isDestroyed && currentMediaItem != null) {
            castPlayer?.setMediaItem(currentMediaItem!!)
            castPlayer?.prepare()
            playerView.player = castPlayer
            exoPlayer?.stop()
        }
    }

    override fun onCastSessionUnavailable() {
        if (exoPlayer != null && currentMediaItem != null) {
            exoPlayer?.setMediaItem(currentMediaItem!!)
            exoPlayer?.prepare()
            playerView.player = exoPlayer
            castPlayer?.stop()
        }
    }

    fun release() {
        castPlayer?.setSessionAvailabilityListener(null)
        castPlayer?.release()
        castPlayer = null
    }
}
