package ani.dantotsu.others

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.annotation.ColorInt

/**
 * Base SurfaceView following Google Grafika's architecture.
 *
 * Enforces hardware setSecure(true) on the Surface layer before window attachment,
 * manages lifecycle callbacks, and renders a solid background color to completely
 * prevent wallpaper/desktop punch-through.
 */
abstract class BaseSecureSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : SurfaceView(context, attrs, defStyleAttr), SurfaceHolder.Callback {

    companion object {
        private const val TAG = "BaseSecureSurfaceView"
    }

    protected var isSurfaceAvailable: Boolean = false
        private set

    protected var surfaceWidth: Int = 0
        private set

    protected var surfaceHeight: Int = 0
        private set

    @ColorInt
    var surfaceBackgroundColor: Int = resolveThemeBackgroundColor(context)
        set(value) {
            field = value
            redraw()
        }

    init {
        // Crucial Grafika rule: setSecure(true) must be called before window attachment
        setSecure(true)
        holder.addCallback(this)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        isSurfaceAvailable = true
        redraw()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        isSurfaceAvailable = true
        surfaceWidth = width
        surfaceHeight = height
        redraw()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        isSurfaceAvailable = false
    }

    /**
     * Safely locks the canvas, pre-fills with background color,
     * calls custom drawing, and unlocks and posts the buffer.
     */
    fun redraw() {
        if (!isSurfaceAvailable) return
        val surface = holder.surface
        if (surface == null || !surface.isValid) return

        var canvas: Canvas? = null
        try {
            canvas = holder.lockCanvas()
            if (canvas != null) {
                // Prevent wallpaper punch-through by drawing solid theme background
                canvas.drawColor(surfaceBackgroundColor)
                onDrawSurface(canvas)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error drawing on secure surface", e)
        } finally {
            if (canvas != null) {
                try {
                    holder.unlockCanvasAndPost(canvas)
                } catch (e: Exception) {
                    Log.e(TAG, "Error unlocking canvas and posting", e)
                }
            }
        }
    }

    /**
     * Subclasses implement their custom rendering here.
     */
    protected abstract fun onDrawSurface(canvas: Canvas)

    private fun resolveThemeBackgroundColor(context: Context): Int {
        val typedValue = TypedValue()
        val theme = context.theme
        if (theme.resolveAttribute(com.google.android.material.R.attr.colorSurface, typedValue, true)) {
            if (typedValue.type >= TypedValue.TYPE_FIRST_COLOR_INT && typedValue.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                return typedValue.data
            }
        }
        if (theme.resolveAttribute(android.R.attr.colorBackground, typedValue, true)) {
            if (typedValue.type >= TypedValue.TYPE_FIRST_COLOR_INT && typedValue.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                return typedValue.data
            }
        }
        return Color.BLACK
    }
}
