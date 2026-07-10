package ani.dantotsu.media.anime.mpv

import android.content.Context
import android.content.Context.AUDIO_SERVICE
import android.media.AudioManager
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.view.KeyCharacterMap
import android.view.KeyEvent
import androidx.media.AudioAttributesCompat
import androidx.media.AudioFocusRequestCompat
import androidx.media.AudioManagerCompat
import com.hippo.unifile.UniFile
import ani.dantotsu.settings.saving.PrefManager
import ani.dantotsu.settings.saving.PrefName
import `is`.xyz.mpv.KeyMapping
import `is`.xyz.mpv.MPV
import `is`.xyz.mpv.MPVNode
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.File

class MPVPlayer(
    context: Context,
    videoOutput: String
) : MPV.EventObserver, MPV.LogObserver, AudioManager.OnAudioFocusChangeListener {

    val mpv: MPV
    private val handler = Handler(context.mainLooper)

    private val audioManager by lazy { context.getSystemService(AUDIO_SERVICE) as AudioManager }
    private var restoreAudioFocus: () -> Unit = {}
    private var audioFocusRequest: AudioFocusRequestCompat? = null

    @Volatile
    var isExiting = false
    private var httpError: String? = null

    private val _eventFlow = MutableSharedFlow<Event>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val eventFlow = _eventFlow.asSharedFlow()

    init {
        val cachePath: String = context.cacheDir.path
        val filesDir = context.filesDir

        val mpvDir = UniFile.fromFile(filesDir)?.createDirectory(MPV_DIR)
        val mpvConfFile = mpvDir?.createFile("mpv.conf")
        val customConf = PrefManager.getCustomVal("mpv_conf_text", "")
        if (customConf.isNotEmpty()) {
            mpvConfFile?.writeText(customConf)
        }
        val mpvInputFile = mpvDir?.createFile("input.conf")
        val customInput = PrefManager.getCustomVal("mpv_input_text", "")
        if (customInput.isNotEmpty()) {
            mpvInputFile?.writeText(customInput)
        }

        mpv = MPV(context) {
            it.setOptionString("config", "yes")
            it.setOptionString("config-dir", File(filesDir, MPV_DIR).absolutePath)
            it.setOptionString("gpu-shader-cache-dir", cachePath)
            it.setOptionString("icc-cache-dir", cachePath)
            it.setOptionString("keep-open", "yes")
        }

        val optionNameRegex = Regex("""^(?:--)?([\w-]+)(?:=|$)""", RegexOption.MULTILINE)
        val mpvOptionNames = optionNameRegex.findAll(customConf).map {
            it.groupValues[1].removePrefix("no-")
        }.toSet()

        fun setSafeOptionString(name: String, value: String) {
            if (name in mpvOptionNames) return
            mpv.setOptionString(name, value)
        }

        mpv.setOptionString("vo", videoOutput)
        setSafeOptionString("profile", "fast")
        
        val tryHW = PrefManager.getCustomVal("mpv_try_hw_dec", true)
        mpv.setOptionString("hwdec", if (tryHW) "mediacodec" else "no")
        
        val useYuv420p = PrefManager.getCustomVal("mpv_use_yuv420p", false)
        if (useYuv420p) {
            mpv.setOptionString("vf", "format=yuv420p")
        }

        val verboseLogging = PrefManager.getCustomVal("mpv_verbose_logging", false)
        mpv.setOptionString("msg-level", "all=" + if (verboseLogging) "v" else "warn")
        mpv.setPropertyBoolean("input-default-bindings", true)
        mpv.setOptionString("idle", "yes")
        mpv.setOptionString("ytdl", "no")
        setSafeOptionString("tls-verify", "yes")
        setSafeOptionString("tls-ca-file", File(filesDir, "$MPV_DIR/cacert.pem").absolutePath)

        mpv.setOptionString("sid", "no")
        mpv.setOptionString("aid", "no")

        val cacheMegs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) 64 else 32
        setSafeOptionString("demuxer-max-bytes", "${cacheMegs * 1024 * 1024}")
        setSafeOptionString("demuxer-max-back-bytes", "${cacheMegs * 1024 * 1024}")

        val screenshotDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).also {
            it.mkdirs()
        }
        mpv.setOptionString("screenshot-directory", screenshotDir.path)

        VideoFilters.entries.forEach {
            val value = PrefManager.getCustomVal(it.prefKey, it.defaultValue)
            mpv.setOptionString(it.mpvProperty, value.toString())
        }

        val savedSpeedIndex = PrefManager.getCustomVal("mpv_player_speed_index", 5) // default 1.0f speed index
        val speeds = arrayOf(0.25f, 0.33f, 0.5f, 0.66f, 0.75f, 1f, 1.15f, 1.25f, 1.33f, 1.5f, 1.66f, 1.75f, 2f)
        val speed = speeds.getOrNull(savedSpeedIndex) ?: 1.0f
        mpv.setOptionString("speed", speed.toString())
        setSafeOptionString("vd-lavc-film-grain", "cpu")

        val debandModeStr = PrefManager.getCustomVal("mpv_debanding_mode", Debanding.None.name)
        val debandMode = try { Debanding.valueOf(debandModeStr) } catch(e: Exception) { Debanding.None }
        when (debandMode) {
            Debanding.None -> {}
            Debanding.CPU -> mpv.setOptionString("vf", "gradfun=radius=12")
            Debanding.GPU -> mpv.setOptionString("deband", "yes")
        }

        mpv.addObserver(this)
        mpv.addLogObserver(this)

        setupSubtitlesOptions()
        setupAudio()

        mapOf(
            "eof-reached" to MPV.mpvFormat.MPV_FORMAT_FLAG,
            "user-data/aniyomi/show_text" to MPV.mpvFormat.MPV_FORMAT_STRING,
            "user-data/aniyomi/toggle_ui" to MPV.mpvFormat.MPV_FORMAT_STRING,
            "user-data/aniyomi/show_panel" to MPV.mpvFormat.MPV_FORMAT_STRING,
            "user-data/aniyomi/software_keyboard" to MPV.mpvFormat.MPV_FORMAT_STRING,
            "user-data/aniyomi/set_button_title" to MPV.mpvFormat.MPV_FORMAT_STRING,
            "user-data/aniyomi/reset_button_title" to MPV.mpvFormat.MPV_FORMAT_STRING,
            "user-data/aniyomi/toggle_button" to MPV.mpvFormat.MPV_FORMAT_STRING,
            "user-data/aniyomi/switch_episode" to MPV.mpvFormat.MPV_FORMAT_STRING,
            "user-data/aniyomi/pause" to MPV.mpvFormat.MPV_FORMAT_STRING,
            "user-data/aniyomi/seek_by" to MPV.mpvFormat.MPV_FORMAT_STRING,
            "user-data/aniyomi/seek_to" to MPV.mpvFormat.MPV_FORMAT_STRING,
            "user-data/aniyomi/seek_by_with_text" to MPV.mpvFormat.MPV_FORMAT_STRING,
            "user-data/aniyomi/seek_to_with_text" to MPV.mpvFormat.MPV_FORMAT_STRING,
            "user-data/aniyomi/launch_int_picker" to MPV.mpvFormat.MPV_FORMAT_STRING,
            "user-data/aniyomi/show_seek_text" to MPV.mpvFormat.MPV_FORMAT_STRING,
        ).forEach { (name, format) ->
            mpv.observeProperty(name, format)
        }
    }

    private fun UniFile.writeText(text: String) {
        this.openOutputStream().use {
            it.write(text.toByteArray())
        }
    }

    private fun setupAudio() {
        val preferredAudioLang = PrefManager.getCustomVal("mpv_preferred_audio_lang", "jpn")
        mpv.setOptionString("alang", preferredAudioLang)
        val audioDelay = PrefManager.getCustomVal("mpv_audio_delay", 0)
        mpv.setOptionString("audio-delay", (audioDelay / 1000.0).toString())
        val pitchCorrection = PrefManager.getCustomVal("mpv_pitch_correction", true)
        mpv.setOptionString("audio-pitch-correction", pitchCorrection.toString())
        val volumeBoostCap = PrefManager.getCustomVal("mpv_volume_boost_cap", 30) // max 130% volume
        mpv.setOptionString("volume-max", (volumeBoostCap + 100).toString())

        val request = AudioFocusRequestCompat.Builder(AudioManagerCompat.AUDIOFOCUS_GAIN).also {
            it.setAudioAttributes(
                AudioAttributesCompat.Builder().setUsage(AudioAttributesCompat.USAGE_MEDIA)
                    .setContentType(AudioAttributesCompat.CONTENT_TYPE_MUSIC).build(),
            )
            it.setOnAudioFocusChangeListener(this)
        }.build()
        AudioManagerCompat.requestAudioFocus(audioManager, request).let {
            if (it == AudioManager.AUDIOFOCUS_REQUEST_FAILED) return@let
            audioFocusRequest = request
        }
    }

    private fun setupSubtitlesOptions() {
        val subDelay = PrefManager.getCustomVal("mpv_sub_delay", 0)
        mpv.setOptionString("sub-delay", (subDelay / 1000.0).toString())
        val subSpeed = PrefManager.getCustomVal("mpv_sub_speed", 1.0f)
        mpv.setOptionString("sub-speed", subSpeed.toString())

        val fontSize = PrefManager.getVal<Int>(PrefName.FontSize)
        mpv.setOptionString("sub-font-size", fontSize.toString())
        
        val primaryColor = PrefManager.getVal<Int>(PrefName.PrimaryColor)
        mpv.setOptionString("sub-color", primaryColor.toColorHexString())
        
        val secondaryColor = PrefManager.getVal<Int>(PrefName.SecondaryColor)
        mpv.setOptionString("sub-outline-color", secondaryColor.toColorHexString())

        val outlineType = PrefManager.getVal<Int>(PrefName.Outline) // 0: None, 1: Outline, 2: Shadow/Background
        when (outlineType) {
            0 -> {
                mpv.setOptionString("sub-border-style", "0")
                mpv.setOptionString("sub-shadow-offset", "0")
            }
            1 -> {
                mpv.setOptionString("sub-border-style", "1")
                mpv.setOptionString("sub-outline-size", "2.0")
                mpv.setOptionString("sub-shadow-offset", "0")
            }
            2 -> {
                mpv.setOptionString("sub-border-style", "2") // box background
                mpv.setOptionString("sub-shadow-offset", "3.0")
            }
        }
        
        val subFont = PrefManager.getCustomVal("mpv_sub_font", "sans-serif")
        mpv.setOptionString("sub-font", subFont)
        
        val subBlackBars = PrefManager.getCustomVal("mpv_sub_black_bars", true)
        val showBlackBars = if (subBlackBars) "yes" else "no"
        mpv.setOptionString("sub-ass-force-margins", showBlackBars)
        mpv.setOptionString("sub-use-margins", showBlackBars)
    }

    private fun Int.toColorHexString(): String {
        return String.format("#%08X", this)
    }

    override fun eventProperty(property: String) {
        handler.post {
            if (isExiting) return@post
        }
    }

    override fun eventProperty(property: String, value: Long) {
        handler.post {
            if (isExiting) return@post
        }
    }

    override fun eventProperty(property: String, value: Boolean) {
        handler.post {
            if (isExiting) return@post
            when (property) {
                "eof-reached" -> _eventFlow.tryEmit(Event.EOF(value))
            }
        }
    }

    override fun eventProperty(property: String, value: String) {
        handler.post {
            if (isExiting) return@post
            when (property.substringBeforeLast("/")) {
                "user-data/aniyomi" -> _eventFlow.tryEmit(Event.LuaEvent(property, value))
            }
        }
    }

    override fun eventProperty(property: String, value: Double) {
        handler.post {
            if (isExiting) return@post
        }
    }

    override fun eventProperty(property: String, value: MPVNode) {
        handler.post {
            if (isExiting) return@post
        }
    }

    override fun event(eventId: Int, data: MPVNode) {
        handler.post {
            if (isExiting) return@post
            when (eventId) {
                MPV.mpvEvent.MPV_EVENT_FILE_LOADED -> _eventFlow.tryEmit(Event.FileLoaded)
                MPV.mpvEvent.MPV_EVENT_PLAYBACK_RESTART -> isExiting = false
                MPV.mpvEvent.MPV_EVENT_END_FILE -> _eventFlow.tryEmit(Event.EndFile(data))
            }
        }
    }

    override fun logMessage(prefix: String, level: Int, text: String) {
        if (level == MPV.mpvLogLevel.MPV_LOG_LEVEL_ERROR) {
            if (text.startsWith(TRACK_LOAD_FAILURE)) {
                val url = text.removePrefix(TRACK_LOAD_FAILURE).substringBeforeLast(".")
                _eventFlow.tryEmit(Event.TrackLoadFailure(url))
            }
        }
        if (text.contains("HTTP error")) httpError = text.removePrefix("http: ")
    }

    override fun onAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                val oldRestore = restoreAudioFocus
                val wasPlayerPaused = mpv.getPropertyBoolean("pause") ?: true
                mpv.setPropertyBoolean("pause", true)
                restoreAudioFocus = {
                    oldRestore()
                    if (!wasPlayerPaused) mpv.setPropertyBoolean("pause", false)
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                mpv.command("multiply", "volume", "0.5")
                restoreAudioFocus = {
                    mpv.command("multiply", "volume", "2")
                }
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                restoreAudioFocus()
                restoreAudioFocus = {}
            }
            AudioManager.AUDIOFOCUS_REQUEST_FAILED -> {
            }
        }
    }

    fun onKey(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_MULTIPLE || KeyEvent.isModifierKey(event.keyCode)) {
            return false
        }

        var mapped = KeyMapping[event.keyCode]
        if (mapped == null) {
            if (!event.isPrintingKey) {
                return false
            }
            val ch = event.unicodeChar
            if (ch.and(KeyCharacterMap.COMBINING_ACCENT) != 0) {
                return false
            }
            mapped = ch.toChar().toString()
        }

        if (event.repeatCount > 0) {
            return true
        }

        val mod: MutableList<String> = mutableListOf()
        event.isShiftPressed && mod.add("shift")
        event.isCtrlPressed && mod.add("ctrl")
        event.isAltPressed && mod.add("alt")
        event.isMetaPressed && mod.add("meta")

        val action = if (event.action == KeyEvent.ACTION_DOWN) "keydown" else "keyup"
        mod.add(mapped)
        mpv.command(action, mod.joinToString("+"))

        return true
    }

    fun getHttpError(): String? {
        return httpError
    }

    fun resetHttpError() {
        httpError = null
    }

    fun release() {
        if (isExiting) return
        isExiting = true

        audioFocusRequest?.let {
            AudioManagerCompat.abandonAudioFocusRequest(audioManager, it)
        }
        audioFocusRequest = null

        handler.removeCallbacksAndMessages(null)
        mpv.removeObserver(this)
        mpv.removeLogObserver(this)
        mpv.close()
    }

    sealed interface Event {
        data object FileLoaded : Event
        data class EOF(val value: Boolean) : Event
        data class TrackLoadFailure(val url: String) : Event
        data class EndFile(val node: MPVNode) : Event
        data class LuaEvent(val property: String, val value: String) : Event
    }

    companion object {
        private const val MPV_DIR = "mpv"
        const val TRACK_LOAD_FAILURE = "Can not open external file "
    }
}
