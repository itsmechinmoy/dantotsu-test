package ani.dantotsu.media.anime.mpv

import android.content.Context
import java.io.File
import java.io.FileOutputStream

class Anime4KManager(private val context: Context) {

    companion object {
        private const val SHADER_DIR = "shaders"
    }

    enum class Quality(val suffix: String) {
        FAST("S"),
        BALANCED("M"),
        HIGH("L")
    }

    enum class Mode {
        OFF,
        A,
        B,
        C,
        A_PLUS,
        B_PLUS,
        C_PLUS
    }

    private var shaderDir: File? = null
    private var isInitialized = false

    fun initialize(): Boolean {
        if (isInitialized) {
            return true
        }

        return try {
            shaderDir = File(context.filesDir, SHADER_DIR)
            if (!shaderDir!!.exists()) {
                val created = shaderDir!!.mkdirs()
                if (!created) {
                    return false
                }
            }

            val shaderFiles = context.assets.list(SHADER_DIR) ?: emptyArray()

            for (fileName in shaderFiles) {
                if (fileName.endsWith(".glsl")) {
                    copyShaderFromAssets(fileName)
                }
            }

            isInitialized = true
            true
        } catch (e: Exception) {
            isInitialized = false
            false
        }
    }

    private fun copyShaderFromAssets(fileName: String): Boolean {
        val destFile = File(shaderDir, fileName)

        if (destFile.exists() && destFile.length() > 0) {
            return false
        }

        return try {
            context.assets.open("$SHADER_DIR/$fileName").use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun getShaderChain(mode: Mode, quality: Quality): String {
        if (mode == Mode.OFF) {
            return ""
        }

        if (!isInitialized) {
            initialize()
        }

        if (shaderDir == null || !shaderDir!!.exists()) {
            return ""
        }

        val shaders = mutableListOf<String>()
        val q = quality.suffix

        shaders.add(getShaderPath("Anime4K_Clamp_Highlights.glsl"))

        when (mode) {
            Mode.A -> {
                shaders.add(getShaderPath("Anime4K_Restore_CNN_$q.glsl"))
                shaders.add(getShaderPath("Anime4K_Upscale_CNN_x2_$q.glsl"))
                shaders.add(getShaderPath("Anime4K_AutoDownscalePre_x2.glsl"))
                shaders.add(getShaderPath("Anime4K_Upscale_CNN_x2_$q.glsl"))
            }
            Mode.B -> {
                shaders.add(getShaderPath("Anime4K_Restore_CNN_Soft_$q.glsl"))
                shaders.add(getShaderPath("Anime4K_Upscale_CNN_x2_$q.glsl"))
                shaders.add(getShaderPath("Anime4K_AutoDownscalePre_x2.glsl"))
                shaders.add(getShaderPath("Anime4K_Upscale_CNN_x2_$q.glsl"))
            }
            Mode.C -> {
                shaders.add(getShaderPath("Anime4K_Upscale_Denoise_CNN_x2_$q.glsl"))
                shaders.add(getShaderPath("Anime4K_AutoDownscalePre_x2.glsl"))
                shaders.add(getShaderPath("Anime4K_Upscale_CNN_x2_$q.glsl"))
            }
            Mode.A_PLUS -> {
                shaders.add(getShaderPath("Anime4K_Restore_CNN_$q.glsl"))
                shaders.add(getShaderPath("Anime4K_Upscale_CNN_x2_$q.glsl"))
                shaders.add(getShaderPath("Anime4K_AutoDownscalePre_x2.glsl"))
                shaders.add(getShaderPath("Anime4K_Restore_CNN_$q.glsl"))
                shaders.add(getShaderPath("Anime4K_Upscale_CNN_x2_$q.glsl"))
            }
            Mode.B_PLUS -> {
                shaders.add(getShaderPath("Anime4K_Restore_CNN_Soft_$q.glsl"))
                shaders.add(getShaderPath("Anime4K_Upscale_CNN_x2_$q.glsl"))
                shaders.add(getShaderPath("Anime4K_AutoDownscalePre_x2.glsl"))
                shaders.add(getShaderPath("Anime4K_Restore_CNN_Soft_$q.glsl"))
                shaders.add(getShaderPath("Anime4K_Upscale_CNN_x2_$q.glsl"))
            }
            Mode.C_PLUS -> {
                shaders.add(getShaderPath("Anime4K_Upscale_Denoise_CNN_x2_$q.glsl"))
                shaders.add(getShaderPath("Anime4K_AutoDownscalePre_x2.glsl"))
                shaders.add(getShaderPath("Anime4K_Restore_CNN_$q.glsl"))
                shaders.add(getShaderPath("Anime4K_Upscale_CNN_x2_$q.glsl"))
            }
            Mode.OFF -> {}
        }

        val missingShaders = shaders.filter { path ->
            !File(path).exists()
        }

        if (missingShaders.isNotEmpty()) {
            return ""
        }

        return shaders.joinToString(":")
    }

    private fun getShaderPath(fileName: String): String {
        return File(shaderDir, fileName).absolutePath
    }
}
