package ani.dantotsu.media.anime

import android.annotation.SuppressLint
import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.SessionAvailabilityListener
import androidx.media3.common.C
import androidx.media3.ui.PlayerView
import ani.dantotsu.R
import ani.dantotsu.connections.subtitles.StremioSub
import ani.dantotsu.databinding.ActivityExoplayerBinding
import ani.dantotsu.media.Media
import ani.dantotsu.media.anime.mpv.Anime4KManager
import ani.dantotsu.media.anime.mpv.Debanding
import ani.dantotsu.media.anime.mpv.MPVPlayer
import ani.dantotsu.media.anime.mpv.VideoTrack
import ani.dantotsu.media.anime.mpv.applyAnime4K
import ani.dantotsu.media.anime.mpv.applyDebandMode
import ani.dantotsu.media.anime.mpv.resolveUri
import ani.dantotsu.parsers.Subtitle
import ani.dantotsu.settings.saving.PrefManager
import ani.dantotsu.settings.saving.PrefName
import ani.dantotsu.toast
import ani.dantotsu.util.Logger
import com.google.android.gms.cast.framework.CastContext
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.io.File
import kotlin.math.max
import kotlin.math.min

class MpvPlayerActivity : AppCompatActivity(), SurfaceHolder.Callback {

    private lateinit var binding: ActivityExoplayerBinding
    private var mpvPlayer: MPVPlayer? = null
    private var surfaceView: SurfaceView? = null
    private var surfaceHolder: SurfaceHolder? = null

    private val handler = Handler(Looper.getMainLooper())
    private var isPlayerPlaying = false
    private var playbackPosition = 0L
    private var duration = 0L
    private var isActivityDestroyed = false

    // Gestures
    private lateinit var gestureDetector: GestureDetector
    private lateinit var scaleGestureDetector: ScaleGestureDetector
    private var brightness = 0.5f

    // UI overlays
    private var showControls = true
    private var isLocked = false

    // Chromecast
    private var castContext: CastContext? = null
    private var castPlayer: CastPlayer? = null
    private var isCasting = false

    // Anime4K / Filters
    private lateinit var anime4KManager: Anime4KManager

    // Video properties from extractor
    private var videoUrl: String? = null
    private var headers: Map<String, String>? = null
    private val externalSubtitles = mutableListOf<Subtitle>()

    // Subtitle dialog helpers
    private var localSubtitles = mutableListOf<Subtitle>()

    companion object {
        var media: Media? = null
        var initialized = false
        private const val CONTROLLER_TIMEOUT = 4500L
    }

    private val selectSubtitleFile = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val path = it.resolveUri(this) ?: it.toString()
            val label = "Local: " + (it.lastPathSegment ?: "Subtitle")
            val stremioSub = StremioSub(
                id = path,
                url = path,
                lang = label
            )
            applyOnlineSubtitle(stremioSub)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityExoplayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        anime4KManager = Anime4KManager(this)
        anime4KManager.initialize()

        setupCast()
        initLayout()
        initPlayer()
    }

    private fun initLayout() {
        val playerView = binding.playerView
        
        // Disable Media3 default bindings since we run them ourselves
        playerView.useController = false

        // Insert native SurfaceView inside AspectRatioFrameLayout of PlayerView
        val contentFrame = playerView.findViewById<FrameLayout>(androidx.media3.ui.R.id.exo_content_frame)
        surfaceView = SurfaceView(this)
        surfaceView?.holder?.addCallback(this)
        contentFrame.addView(surfaceView, 0)

        // Set up double tap seek and drag controls on touch overlay
        val touchView = playerView.findViewById<View>(R.id.exo_touch_view) ?: playerView
        gestureDetector = GestureDetector(this, GestureListener())
        scaleGestureDetector = ScaleGestureDetector(this, ScaleListener())

        touchView.setOnTouchListener { _, event ->
            scaleGestureDetector.onTouchEvent(event)
            gestureDetector.onTouchEvent(event)
            if (event.action == MotionEvent.ACTION_UP) {
                hideOverlayIndicators()
            }
            true
        }

        // Initialize control buttons
        val playButton = playerView.findViewById<ImageButton>(androidx.media3.ui.R.id.exo_play)
        playButton?.setOnClickListener {
            togglePlayPause()
        }

        val lockButton = playerView.findViewById<ImageButton>(R.id.exo_lock)
        lockButton?.setOnClickListener {
            isLocked = !isLocked
            updateLockState()
        }

        val speedButton = playerView.findViewById<ImageButton>(androidx.media3.ui.R.id.exo_playback_speed)
        speedButton?.setOnClickListener {
            showSpeedDialog()
        }

        val subtitleButton = playerView.findViewById<ImageButton>(R.id.exo_sub)
        subtitleButton?.setOnClickListener {
            showSubtitleTrackDialog()
        }

        val audioButton = playerView.findViewById<ImageButton>(R.id.exo_audio)
        audioButton?.setOnClickListener {
            showAudioTrackDialog()
        }

        val backButton = playerView.findViewById<ImageButton>(R.id.exo_back)
        backButton?.setOnClickListener {
            onBackPressed()
        }

        updateLockState()
        resetControllerTimer()
    }

    private fun setupCast() {
        try {
            castContext = CastContext.getSharedInstance(this)
            castPlayer = CastPlayer(castContext!!)
            castPlayer?.setSessionAvailabilityListener(object : SessionAvailabilityListener {
                override fun onCastSessionAvailable() {
                    startCasting()
                }

                override fun onCastSessionUnavailable() {
                    stopCasting()
                }
            })
        } catch (e: Exception) {
            Logger.log("Chromecast not available: ${e.message}")
        }
    }

    private fun startCasting() {
        isCasting = true
        mpvPlayer?.mpv?.setPropertyBoolean("pause", true)
        surfaceView?.visibility = View.GONE

        val media = Companion.media ?: return
        val currentEpisodeKey = media.anime?.selectedEpisode ?: return
        val video = media.anime.episodes?.get(currentEpisodeKey) ?: return
        
        toast("Casting video to TV...")
    }

    private fun stopCasting() {
        isCasting = false
        surfaceView?.visibility = View.VISIBLE
        mpvPlayer?.mpv?.setPropertyBoolean("pause", false)
    }

    private fun initPlayer() {
        val media = Companion.media ?: return
        val currentEpisodeKey = media.anime?.selectedEpisode ?: return
        val episode = media.anime.episodes?.get(currentEpisodeKey) ?: return
        
        // Active extractor and video
        val extractor = episode.extractors?.find { it.server.name == episode.selectedExtractor }
            ?: episode.extractors?.firstOrNull() ?: return
        val video = extractor.videos.getOrNull(episode.selectedVideo)
            ?: extractor.videos.firstOrNull() ?: return

        videoUrl = video.file.url
        headers = video.file.headers

        // Create local MPVPlayer wrapper
        mpvPlayer = MPVPlayer(this, "gpu")
        
        // Listen to native events
        mpvPlayer?.eventFlow?.onEach { event ->
            when (event) {
                is MPVPlayer.Event.FileLoaded -> {
                    toast("Video Loaded")
                    updateTimeline()
                    applyDefaultSettings()
                }
                is MPVPlayer.Event.EOF -> {
                    if (event.value) {
                        onVideoComplete()
                    }
                }
                is MPVPlayer.Event.LuaEvent -> {
                    Logger.log("Lua event: ${event.property} -> ${event.value}")
                }
                is MPVPlayer.Event.TrackLoadFailure -> {
                    Logger.log("Track load failure: ${event.url}")
                }
                else -> {}
            }
        }?.launchIn(lifecycleScope)

        // Load stream file
        videoUrl?.let { url ->
            val resolvedUrl = Uri.parse(url).resolveUri(this) ?: url
            
            // Set headers option
            headers?.let {
                val httpHeaderString = it.map { entry ->
                    entry.key + ": " + entry.value.replace(",", "\\,")
                }.joinToString(",")
                mpvPlayer?.mpv?.setOptionString("http-header-fields", httpHeaderString)
            }

            mpvPlayer?.mpv?.command("loadfile", resolvedUrl, "replace")
            mpvPlayer?.mpv?.setPropertyBoolean("pause", false)
            isPlayerPlaying = true
            updatePlayPauseIcon()
        }

        // Start progress polling loop
        handler.post(progressRunnable)
    }

    private fun applyDefaultSettings() {
        val mpv = mpvPlayer?.mpv ?: return
        applyAnime4K(mpv, anime4KManager, isInit = true)
        val debandMode = PrefManager.getCustomVal("mpv_debanding_mode", Debanding.None.name)
        applyDebandMode(mpv, Debanding.valueOf(debandMode))
    }

    private fun onVideoComplete() {
        toast("Episode complete")
        finish()
    }

    private fun togglePlayPause() {
        if (isCasting) {
            if (castPlayer?.isPlaying == true) castPlayer?.pause() else castPlayer?.play()
            return
        }
        val paused = mpvPlayer?.mpv?.getPropertyBoolean("pause") ?: true
        mpvPlayer?.mpv?.setPropertyBoolean("pause", !paused)
        isPlayerPlaying = paused
        updatePlayPauseIcon()
        resetControllerTimer()
    }

    private fun updatePlayPauseIcon() {
        val playButton = binding.playerView.findViewById<ImageButton>(androidx.media3.ui.R.id.exo_play)
        if (isPlayerPlaying) {
            playButton?.setImageResource(R.drawable.ic_round_pause_24)
        } else {
            playButton?.setImageResource(R.drawable.ic_round_play_arrow_24)
        }
    }

    private fun updateLockState() {
        val lockButton = binding.playerView.findViewById<ImageButton>(R.id.exo_lock)
        val controllers = binding.playerView.findViewById<View>(R.id.exo_controller)
        if (isLocked) {
            lockButton?.setImageResource(R.drawable.ic_round_lock_24)
            controllers?.visibility = View.GONE
        } else {
            lockButton?.setImageResource(R.drawable.ic_round_lock_open_24)
            controllers?.visibility = View.VISIBLE
        }
    }

    // SurfaceHolder callbacks
    override fun surfaceCreated(holder: SurfaceHolder) {
        surfaceHolder = holder
        val mpv = mpvPlayer?.mpv ?: return
        mpv.command("vf", "clr")
        mpv.setPropertyString("wid", holder.surface.hashCode().toString())
        mpv.command("vf", "add", "format=yuv420p")
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        surfaceHolder = null
        mpvPlayer?.mpv?.setPropertyString("wid", "0")
    }

    // Timeline progress loop
    private val progressRunnable = object : Runnable {
        override fun run() {
            if (!isActivityDestroyed && mpvPlayer != null) {
                updateTimeline()
                handler.postDelayed(this, 1000)
            }
        }
    }

    private fun updateTimeline() {
        val mpv = mpvPlayer?.mpv ?: return
        val posSec = mpv.getPropertyInt("time-pos") ?: 0
        val durSec = mpv.getPropertyInt("duration") ?: 0
        playbackPosition = posSec * 1000L
        duration = durSec * 1000L

        val positionText = binding.playerView.findViewById<TextView>(androidx.media3.ui.R.id.exo_position)
        val durationText = binding.playerView.findViewById<TextView>(androidx.media3.ui.R.id.exo_duration)
        positionText?.text = formatTime(playbackPosition)
        durationText?.text = formatTime(duration)

        val timeline = binding.playerView.findViewById<View>(androidx.media3.ui.R.id.exo_progress) as? androidx.media3.ui.TimeBar
        timeline?.setPosition(playbackPosition)
        timeline?.setDuration(duration)
    }

    private fun formatTime(ms: Long): String {
        val totalSec = ms / 1000
        val sec = totalSec % 60
        val min = (totalSec / 60) % 60
        val hrs = totalSec / 3600
        return if (hrs > 0) {
            String.format("%d:%02d:%02d", hrs, min, sec)
        } else {
            String.format("%02d:%02d", min, sec)
        }
    }

    // Controller overlay show/hide
    private val controllerTimerRunnable = Runnable {
        hideControllers()
    }

    private fun resetControllerTimer() {
        handler.removeCallbacks(controllerTimerRunnable)
        if (showControls && !isLocked) {
            handler.postDelayed(controllerTimerRunnable, CONTROLLER_TIMEOUT)
        }
    }

    private fun hideControllers() {
        showControls = false
        binding.playerView.findViewById<View>(R.id.exo_controller)?.visibility = View.GONE
        hideSystemUI()
    }

    private fun showControllers() {
        showControls = true
        binding.playerView.findViewById<View>(R.id.exo_controller)?.visibility = View.VISIBLE
        showSystemUI()
        resetControllerTimer()
    }

    private fun showSystemUI() {
        WindowCompat.getInsetsController(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
    }

    private fun hideSystemUI() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    // Gestures listener
    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            if (isLocked) {
                // Toggle only the lock button visibility
                val lockButton = binding.playerView.findViewById<View>(R.id.exo_lock)
                lockButton?.visibility = if (lockButton?.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                return true
            }
            if (showControls) hideControllers() else showControllers()
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (isLocked) return false
            val width = binding.root.width
            val x = e.x
            if (x < width / 2) {
                seekBy(-10000L) // seek back 10 seconds
            } else {
                seekBy(10000L) // seek forward 10 seconds
            }
            return true
        }

        override fun onScroll(
            e1: MotionEvent?,
            e2: MotionEvent,
            distanceX: Float,
            distanceY: Float
        ): Boolean {
            if (isLocked || e1 == null) return false
            val width = binding.root.width
            if (e1.x < width / 2) {
                adjustBrightness(-distanceY / binding.root.height)
            } else {
                adjustVolume(-distanceY / binding.root.height)
            }
            return true
        }
    }

    private fun seekBy(ms: Long) {
        val mpv = mpvPlayer?.mpv ?: return
        val currentPos = mpv.getPropertyInt("time-pos") ?: 0
        val targetPos = max(0, currentPos + (ms / 1000).toInt())
        mpv.setPropertyInt("time-pos", targetPos)
        updateTimeline()
    }

    private fun adjustBrightness(delta: Float) {
        brightness = max(0.01f, min(1f, brightness + delta))
        val layoutParams = window.attributes
        layoutParams.screenBrightness = brightness
        window.attributes = layoutParams
        toast("Brightness: ${(brightness * 100).toInt()}%")
    }

    private fun adjustVolume(delta: Float) {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val newVolume = max(0, min(maxVolume, currentVolume + (delta * maxVolume).toInt()))
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)
        toast("Volume: ${(newVolume.toFloat() / maxVolume * 100).toInt()}%")
    }

    private fun hideOverlayIndicators() {}

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val scaleFactor = detector.scaleFactor
            if (scaleFactor > 1.0f) {
                mpvPlayer?.mpv?.setOptionString("video-aspect-override", "16:9")
            } else {
                mpvPlayer?.mpv?.setOptionString("video-aspect-override", "-1")
            }
            return true
        }
    }

    // Subtitle features called by SubtitleDialogFragment
    fun requestLocalSubtitle() {
        selectSubtitleFile.launch("*/*")
    }

    fun reApplyLocalSubtitle(url: String) {
        val resolvedUrl = Uri.parse(url).resolveUri(this) ?: url
        mpvPlayer?.mpv?.command("sub-add", resolvedUrl, "select")
        toast("Local Subtitle Added")
    }

    fun applyOnlineSubtitle(subtitle: StremioSub) {
        val resolvedUrl = Uri.parse(subtitle.url).resolveUri(this) ?: subtitle.url
        mpvPlayer?.mpv?.command("sub-add", resolvedUrl, "select")
        toast("Subtitle loaded: ${subtitle.lang}")
    }

    // Track overriding selector
    fun onSetTrackOverride(track: VideoTrack, type: Int) {
        val mpv = mpvPlayer?.mpv ?: return
        when (type) {
            C.TRACK_TYPE_AUDIO -> {
                val trackId = track.trackId
                if (trackId != null && trackId >= 0) {
                    mpv.setPropertyInt("aid", trackId)
                    toast("Audio set to ${track.title}")
                } else {
                    mpv.setPropertyString("aid", "no")
                    toast("Audio disabled")
                }
            }
            C.TRACK_TYPE_TEXT -> {
                val trackId = track.trackId
                if (trackId != null && trackId >= 0) {
                    mpv.setPropertyInt("sid", trackId)
                    toast("Subtitles set to ${track.title}")
                } else {
                    mpv.setPropertyString("sid", "no")
                    toast("Subtitles disabled")
                }
            }
        }
    }

    private fun showSpeedDialog() {
        val speeds = arrayOf("0.25x", "0.5x", "0.75x", "1.0x", "1.25x", "1.5x", "1.75x", "2.0x")
        val speedsValues = arrayOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
        
        val builder = android.app.AlertDialog.Builder(this)
        builder.setTitle("Select Playback Speed")
        builder.setItems(speeds) { _, which ->
            val value = speedsValues[which]
            mpvPlayer?.mpv?.setPropertyDouble("speed", value.toDouble())
            toast("Speed set to $value")
        }
        builder.show()
    }

    private fun showSubtitleTrackDialog() {
        val mpv = mpvPlayer?.mpv ?: return
        val trackListJson = mpv.getPropertyString("track-list") ?: "[]"
        
        // Parse tracks from json
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val tracks = try {
            json.decodeFromString<List<ani.dantotsu.media.anime.mpv.TrackNode>>(trackListJson)
        } catch(e: Exception) {
            emptyList()
        }

        val subTracks = tracks.filter { it.isSubtitle }.map { VideoTrack.Internal(it) }
        val allSubTracks = mutableListOf<VideoTrack>()
        allSubTracks.add(VideoTrack.Internal(ani.dantotsu.media.anime.mpv.TrackNode(-1, "sub", title = "None", lang = "none")))
        allSubTracks.addAll(subTracks)

        val dialog = TrackGroupDialogFragment(this, allSubTracks, C.TRACK_TYPE_TEXT)
        dialog.show(supportFragmentManager, "subtitle_tracks")
    }

    private fun showAudioTrackDialog() {
        val mpv = mpvPlayer?.mpv ?: return
        val trackListJson = mpv.getPropertyString("track-list") ?: "[]"

        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val tracks = try {
            json.decodeFromString<List<ani.dantotsu.media.anime.mpv.TrackNode>>(trackListJson)
        } catch(e: Exception) {
            emptyList()
        }

        val audioTracks = tracks.filter { it.isAudio }.map { VideoTrack.Internal(it) }
        val allAudioTracks = mutableListOf<VideoTrack>()
        allAudioTracks.add(VideoTrack.Internal(ani.dantotsu.media.anime.mpv.TrackNode(-1, "audio", title = "None", lang = "none")))
        allAudioTracks.addAll(audioTracks)

        val dialog = TrackGroupDialogFragment(this, allAudioTracks, C.TRACK_TYPE_AUDIO)
        dialog.show(supportFragmentManager, "audio_tracks")
    }

    private fun takeScreenshotAndShare() {
        val mpv = mpvPlayer?.mpv ?: return
        val filename = cacheDir.path + "/mpv_screenshot_${System.currentTimeMillis()}.png"
        mpv.command("screenshot-to-file", filename, "video")
        
        val file = File(filename)
        if (file.exists()) {
            val uri = androidx.core.content.FileProvider.getUriForFile(this, "$packageName.provider", file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "Share Screen"))
        } else {
            toast("Failed to take screenshot")
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            hideSystemUI()
        }
    }

    override fun onDestroy() {
        isActivityDestroyed = true
        handler.removeCallbacks(progressRunnable)
        mpvPlayer?.release()
        castPlayer?.release()
        super.onDestroy()
    }
}
