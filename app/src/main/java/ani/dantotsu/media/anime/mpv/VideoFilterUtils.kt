package ani.dantotsu.media.anime.mpv

import ani.dantotsu.settings.saving.PrefManager
import `is`.xyz.mpv.MPV

enum class Debanding {
    None,
    CPU,
    GPU
}

enum class VideoFilters(
    val mpvProperty: String,
    val prefKey: String,
    val defaultValue: Int,
    val min: Int = -100,
    val max: Int = 100
) {
    BRIGHTNESS("brightness", "mpv_filter_brightness", 0),
    SATURATION("saturation", "mpv_filter_saturation", 0),
    CONTRAST("contrast", "mpv_filter_contrast", 0),
    GAMMA("gamma", "mpv_filter_gamma", 0),
    HUE("hue", "mpv_filter_hue", 0),
    SHARPEN("sharpen", "mpv_filter_sharpen", 0, min = -5, max = 5)
}

enum class DebandSettings(
    val mpvProperty: String,
    val prefKey: String,
    val defaultValue: Int,
    val start: Int = 0,
    val end: Int = 100
) {
    ITERATIONS("deband-iterations", "mpv_deband_iterations", 1, start = 1, end = 4),
    THRESHOLD("deband-threshold", "mpv_deband_threshold", 32, start = 0, end = 100),
    RANGE("deband-range", "mpv_deband_range", 16, start = 0, end = 100),
    GRAIN("deband-grain", "mpv_deband_grain", 48, start = 0, end = 100)
}

enum class VideoFilterTheme(
    val brightness: Int = 0,
    val contrast: Int = 0,
    val saturation: Int = 0,
    val gamma: Int = 0,
    val hue: Int = 0,
    val sharpen: Int = 0
) {
    Default(0, 0, 0, 0, 0, 0),
    Vivid(5, 15, 20, 0, 0, 1),
    Cinema(-5, 15, -10, 0, 0, 0),
    Vintage(0, 0, -30, -10, -5, 0)
}

fun applyFilter(mpv: MPV, filter: VideoFilters, value: Int) {
    PrefManager.setCustomVal(filter.prefKey, value)
    mpv.setPropertyInt(filter.mpvProperty, value)
    updateDecoderState(mpv)
}

fun applyDebandMode(mpv: MPV, mode: Debanding) {
    PrefManager.setCustomVal("mpv_debanding_mode", mode.name)
    when (mode) {
        Debanding.None -> {
            mpv.setOptionString("deband", "no")
            mpv.command("vf", "remove", "@deband")
        }
        Debanding.CPU -> {
            mpv.setOptionString("deband", "no")
            mpv.command("vf", "add", "@deband:gradfun=radius=12")
        }
        Debanding.GPU -> {
            mpv.setOptionString("deband", "yes")
            mpv.command("vf", "remove", "@deband")
            DebandSettings.entries.forEach {
                val value = PrefManager.getCustomVal(it.prefKey, it.defaultValue)
                mpv.setPropertyInt(it.mpvProperty, value)
            }
        }
    }
    updateDecoderState(mpv)
}

fun applyDebandSetting(mpv: MPV, setting: DebandSettings, value: Int) {
    PrefManager.setCustomVal(setting.prefKey, value)
    mpv.setPropertyInt(setting.mpvProperty, value)
}

fun updateDecoderState(mpv: MPV) {
    val tryHW = PrefManager.getCustomVal("mpv_try_hw_dec", true)
    if (!tryHW) {
        mpv.setPropertyString("hwdec", "no")
        return
    }
    
    val anime4kEnabled = PrefManager.getCustomVal("mpv_enable_anime4k", false)
    val gpuDeband = PrefManager.getCustomVal("mpv_debanding_mode", Debanding.None.name) == Debanding.GPU.name
    
    if (anime4kEnabled || gpuDeband) {
        mpv.setPropertyString("hwdec", "mediacodec-copy")
    } else {
        mpv.setPropertyString("hwdec", "mediacodec")
    }
}

fun buildVFChain(): String {
    val useYuv420p = PrefManager.getCustomVal("mpv_use_yuv420p", false)
    return if (useYuv420p) "format=yuv420p" else ""
}

fun applyTheme(mpv: MPV, theme: VideoFilterTheme) {
    PrefManager.setCustomVal(VideoFilters.BRIGHTNESS.prefKey, theme.brightness)
    PrefManager.setCustomVal(VideoFilters.CONTRAST.prefKey, theme.contrast)
    PrefManager.setCustomVal(VideoFilters.SATURATION.prefKey, theme.saturation)
    PrefManager.setCustomVal(VideoFilters.GAMMA.prefKey, theme.gamma)
    PrefManager.setCustomVal(VideoFilters.HUE.prefKey, theme.hue)
    PrefManager.setCustomVal(VideoFilters.SHARPEN.prefKey, theme.sharpen)

    mpv.setPropertyInt("brightness", theme.brightness)
    mpv.setPropertyInt("contrast", theme.contrast)
    mpv.setPropertyInt("saturation", theme.saturation)
    mpv.setPropertyInt("gamma", theme.gamma)
    mpv.setPropertyInt("hue", theme.hue)
    mpv.setPropertyInt("sharpen", theme.sharpen)

    mpv.setPropertyString("vf", buildVFChain())

    mpv.setPropertyBoolean("deband", false)
    mpv.setPropertyInt("deband-iterations", 1)
    mpv.setPropertyInt("deband-threshold", 32)
    mpv.setPropertyInt("deband-range", 16)
    mpv.setPropertyInt("deband-grain", 48)
}

fun applyAnime4K(mpv: MPV, manager: Anime4KManager, isInit: Boolean = false) {
    val enabled = PrefManager.getCustomVal("mpv_enable_anime4k", false)
    val gpuNext = PrefManager.getCustomVal("mpv_gpu_next", false)
    
    if (enabled && gpuNext) {
        if (!isInit) mpv.setPropertyString("glsl-shaders", "")
        return
    }

    val modeStr = PrefManager.getCustomVal("mpv_anime4k_mode", Anime4KManager.Mode.OFF.name)
    val qualityStr = PrefManager.getCustomVal("mpv_anime4k_quality", Anime4KManager.Quality.BALANCED.name)

    val mode = try {
        Anime4KManager.Mode.valueOf(modeStr)
    } catch (e: Exception) {
        Anime4KManager.Mode.OFF
    }
    val quality = try {
        Anime4KManager.Quality.valueOf(qualityStr)
    } catch (e: Exception) {
        Anime4KManager.Quality.BALANCED
    }

    manager.initialize()
    val chain = if (enabled) manager.getShaderChain(mode, quality) else ""

    if (chain.isNotEmpty()) {
        if (isInit) {
            mpv.setOptionString("glsl-shaders", chain)
        } else {
            mpv.setPropertyString("glsl-shaders", chain)
        }
    } else {
        if (isInit) {
            mpv.setOptionString("glsl-shaders", "")
        } else {
            mpv.setPropertyString("glsl-shaders", "")
        }
    }
    updateDecoderState(mpv)
}
