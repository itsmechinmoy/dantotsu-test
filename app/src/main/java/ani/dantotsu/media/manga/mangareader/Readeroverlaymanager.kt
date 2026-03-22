package ani.dantotsu.media.manga.mangareader

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.isVisible
import ani.dantotsu.settings.saving.PrefManager
import ani.dantotsu.settings.saving.PrefName

class ReaderOverlayManager(private val root: FrameLayout) {

    private val dimView: View = View(root.context).apply {
        setBackgroundColor(android.graphics.Color.BLACK)
        alpha = 0f
        isClickable = false
        isFocusable = false
        elevation = 1000f
        layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }

    private val colorFilterView: View = View(root.context).apply {
        isClickable = false
        isFocusable = false
        elevation = 1001f
        isVisible = false
        layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }

    fun attach() {
        root.addView(dimView)
        root.addView(colorFilterView)
    }
    
    fun setBrightnessLevel(level: Int) {
        if (level >= 0) {
            dimView.alpha = 0f
        } else {
            val opacity = (level.coerceIn(-75, 0).toFloat().unaryMinus() / 75f).coerceIn(0f, 1f)
            dimView.alpha = opacity
        }
    }
    
    fun setColorFilter(enabled: Boolean, argbColor: Int) {
        if (!enabled) {
            colorFilterView.isVisible = false
            return
        }
        colorFilterView.isVisible = true
        colorFilterView.setBackgroundColor(argbColor)
    }

    fun applyColorEffects(targetView: View, grayscale: Boolean, invert: Boolean) {
        if (!grayscale && !invert) {
            targetView.setLayerType(View.LAYER_TYPE_NONE, null)
            return
        }

        val paint = Paint()

        val matrix = ColorMatrix()

        if (grayscale) {
            matrix.setSaturation(0f)
        }

        if (invert) {
            val invertMatrix = ColorMatrix(
                floatArrayOf(
                    -1f, 0f, 0f, 0f, 255f,
                     0f,-1f, 0f, 0f, 255f,
                     0f, 0f,-1f, 0f, 255f,
                     0f, 0f, 0f, 1f,   0f,
                )
            )
            if (grayscale) {
                matrix.postConcat(invertMatrix)
            } else {
                matrix.set(invertMatrix)
            }
        }

        paint.colorFilter = ColorMatrixColorFilter(matrix)
        targetView.setLayerType(View.LAYER_TYPE_HARDWARE, paint)
    }

    companion object {
        const val PREF_BRIGHTNESS_LEVEL   = "extra_reader_brightness_level"
        const val PREF_BRIGHTNESS_ENABLED = "extra_reader_brightness_enabled"
        const val PREF_COLOR_FILTER_ENABLED = "extra_reader_color_filter_enabled"
        const val PREF_COLOR_FILTER_ARGB  = "extra_reader_color_filter_argb"
        const val PREF_GRAYSCALE          = "extra_reader_grayscale"
        const val PREF_INVERT_COLORS      = "extra_reader_invert_colors"
    }
}
