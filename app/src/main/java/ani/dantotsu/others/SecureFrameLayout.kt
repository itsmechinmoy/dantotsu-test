package ani.dantotsu.others

import android.content.Context
import android.graphics.PixelFormat
import android.util.AttributeSet
import android.view.SurfaceView
import android.widget.FrameLayout
import ani.dantotsu.settings.saving.PrefManager
import ani.dantotsu.settings.saving.PrefName

/**
 * A container FrameLayout that embeds a hardware-level secure SurfaceView with setSecure(true).
 *
 * Behavior:
 * - On-screen display: The SurfaceView has a translucent pixel format and media overlay Z-order,
 *   rendering wrapped child views (e.g. extension logos and names) with 100% fidelity to the user.
 * - Screenshots / Screen recording / Casting: Android's SurfaceFlinger hardware compositor marks
 *   only this element's rectangular bounds as SECURE and blocks/redacts it in captured buffers,
 *   while the rest of the application UI remains fully visible.
 */
class SecureFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private var secureSurfaceView: SurfaceView? = null

    init {
        setupSecureSurface()
    }

    private fun setupSecureSurface() {
        val isSecureEnabled = try {
            PrefManager.getVal<Boolean>(PrefName.SecureExtensionScreenshots, true)
        } catch (_: Throwable) {
            true
        }

        if (isSecureEnabled && secureSurfaceView == null) {
            val surface = SurfaceView(context).apply {
                setSecure(true)
                setZOrderMediaOverlay(true)
                holder.setFormat(PixelFormat.TRANSLUCENT)
                isClickable = false
                isFocusable = false
                layoutParams = LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    LayoutParams.MATCH_PARENT
                )
            }
            secureSurfaceView = surface
            addView(surface)
        }
    }
}
