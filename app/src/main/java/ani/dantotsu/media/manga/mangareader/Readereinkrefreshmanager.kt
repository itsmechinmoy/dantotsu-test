package ani.dantotsu.media.manga.mangareader

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.isVisible

class ReaderEinkRefreshManager(private val root: FrameLayout) {

    private val flashView: View = View(root.context).apply {
        setBackgroundColor(android.graphics.Color.BLACK)
        isVisible = false
        isClickable = false
        isFocusable = false
        elevation = 2000f
        layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }

    private var flashIntervalCount = 0
    var flashDurationMs: Int = 200
    var flashWhite: Boolean = false
    var flashEveryNPages: Int = 1

    fun attach() {
        root.addView(flashView)
    }

    fun flash() {
        flashIntervalCount++
        if (flashIntervalCount % flashEveryNPages != 0) return

        flashView.setBackgroundColor(
            if (flashWhite) android.graphics.Color.WHITE else android.graphics.Color.BLACK
        )
        flashView.alpha = 1f
        flashView.isVisible = true

        ObjectAnimator.ofFloat(flashView, "alpha", 1f, 0f)
            .apply {
                duration = flashDurationMs.toLong()
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        flashView.isVisible = false
                    }
                })
                start()
            }
    }

    fun destroy() {
        root.removeView(flashView)
    }
}
