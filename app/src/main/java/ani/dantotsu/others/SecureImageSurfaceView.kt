package ani.dantotsu.others

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.request.target.CustomViewTarget
import com.bumptech.glide.request.transition.Transition
import kotlin.math.max
import kotlin.math.min

/**
 * A secure SurfaceView drop-in replacement for ImageView.
 *
 * Renders bitmaps/drawables directly onto a hardware-secured Surface layer so that they
 * are automatically redacted in screenshots and screen recordings without UI glitching.
 */
class SecureImageSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : BaseSecureSurfaceView(context, attrs, defStyleAttr) {

    enum class ScaleType {
        FIT_CENTER,
        CENTER_CROP,
        FIT_XY,
        CENTER,
        CENTER_INSIDE
    }

    private val imagePaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val drawMatrix: Matrix = Matrix()
    private val srcRect = RectF()
    private val dstRect = RectF()

    var bitmap: Bitmap? = null
        private set

    var scaleType: ScaleType = ScaleType.FIT_CENTER
        set(value) {
            if (field != value) {
                field = value
                redraw()
            }
        }

    fun setImageBitmap(bm: Bitmap?) {
        this.bitmap = bm
        requestLayout()
        redraw()
    }

    fun setImageDrawable(drawable: Drawable?) {
        if (drawable == null) {
            setImageBitmap(null)
            return
        }
        val converted = try {
            if (drawable is BitmapDrawable && drawable.bitmap != null) {
                drawable.bitmap
            } else {
                val w = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 96
                val h = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 96
                drawable.toBitmap(w, h, Bitmap.Config.ARGB_8888)
            }
        } catch (_: Throwable) {
            null
        }
        setImageBitmap(converted)
    }

    fun setImageResource(@DrawableRes resId: Int) {
        if (resId == 0) {
            setImageBitmap(null)
            return
        }
        val drawable = ContextCompat.getDrawable(context, resId)
        setImageDrawable(drawable)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)

        val bm = bitmap
        val bmWidth = bm?.width ?: 0
        val bmHeight = bm?.height ?: 0

        val horizPadding = paddingLeft + paddingRight
        val vertPadding = paddingTop + paddingBottom

        var desiredWidth = bmWidth + horizPadding
        var desiredHeight = bmHeight + vertPadding

        if (bmWidth > 0 && bmHeight > 0) {
            if (widthMode == MeasureSpec.EXACTLY && heightMode != MeasureSpec.EXACTLY) {
                val availWidth = widthSize - horizPadding
                val computedHeight = (availWidth.toFloat() * bmHeight / bmWidth).toInt()
                desiredHeight = computedHeight + vertPadding
            } else if (heightMode == MeasureSpec.EXACTLY && widthMode != MeasureSpec.EXACTLY) {
                val availHeight = heightSize - vertPadding
                val computedWidth = (availHeight.toFloat() * bmWidth / bmHeight).toInt()
                desiredWidth = computedWidth + horizPadding
            }
        }

        val resolvedWidth = when (widthMode) {
            MeasureSpec.EXACTLY -> widthSize
            MeasureSpec.AT_MOST -> min(desiredWidth, widthSize)
            else -> desiredWidth
        }

        val resolvedHeight = when (heightMode) {
            MeasureSpec.EXACTLY -> heightSize
            MeasureSpec.AT_MOST -> min(desiredHeight, heightSize)
            else -> desiredHeight
        }

        setMeasuredDimension(
            resolveSize(resolvedWidth, widthMeasureSpec),
            resolveSize(resolvedHeight, heightMeasureSpec)
        )
    }

    override fun onDrawSurface(canvas: Canvas) {
        val bm = bitmap ?: return
        if (bm.isRecycled) return

        val contentLeft = paddingLeft.toFloat()
        val contentTop = paddingTop.toFloat()
        val contentWidth = max(0f, (width - paddingLeft - paddingRight).toFloat())
        val contentHeight = max(0f, (height - paddingTop - paddingBottom).toFloat())

        if (contentWidth <= 0 || contentHeight <= 0) return

        val bmWidth = bm.width.toFloat()
        val bmHeight = bm.height.toFloat()

        drawMatrix.reset()
        srcRect.set(0f, 0f, bmWidth, bmHeight)
        dstRect.set(contentLeft, contentTop, contentLeft + contentWidth, contentTop + contentHeight)

        when (scaleType) {
            ScaleType.FIT_XY -> {
                drawMatrix.setRectToRect(srcRect, dstRect, Matrix.ScaleToFit.FILL)
            }
            ScaleType.FIT_CENTER -> {
                drawMatrix.setRectToRect(srcRect, dstRect, Matrix.ScaleToFit.CENTER)
            }
            ScaleType.CENTER -> {
                val dx = contentLeft + (contentWidth - bmWidth) * 0.5f
                val dy = contentTop + (contentHeight - bmHeight) * 0.5f
                drawMatrix.setTranslate(dx, dy)
            }
            ScaleType.CENTER_CROP -> {
                val scale: Float
                var dx = contentLeft
                var dy = contentTop
                if (bmWidth * contentHeight > contentWidth * bmHeight) {
                    scale = contentHeight / bmHeight
                    dx += (contentWidth - bmWidth * scale) * 0.5f
                } else {
                    scale = contentWidth / bmWidth
                    dy += (contentHeight - bmHeight * scale) * 0.5f
                }
                drawMatrix.setScale(scale, scale)
                drawMatrix.postTranslate(dx, dy)
            }
            ScaleType.CENTER_INSIDE -> {
                val scale = if (bmWidth <= contentWidth && bmHeight <= contentHeight) {
                    1.0f
                } else {
                    min(contentWidth / bmWidth, contentHeight / bmHeight)
                }
                val dx = contentLeft + (contentWidth - bmWidth * scale) * 0.5f
                val dy = contentTop + (contentHeight - bmHeight * scale) * 0.5f
                drawMatrix.setScale(scale, scale)
                drawMatrix.postTranslate(dx, dy)
            }
        }

        canvas.save()
        canvas.clipRect(dstRect)
        canvas.drawBitmap(bm, drawMatrix, imagePaint)
        canvas.restore()
    }
}

/**
 * Glide extension to seamlessly load into SecureImageSurfaceView.
 */
fun RequestBuilder<Drawable>.into(secureView: SecureImageSurfaceView) {
    into(object : CustomViewTarget<SecureImageSurfaceView, Drawable>(secureView) {
        override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
            view.setImageDrawable(resource)
        }
        override fun onResourceCleared(placeholder: Drawable?) {
            view.setImageDrawable(placeholder)
        }
        override fun onLoadFailed(errorDrawable: Drawable?) {
            view.setImageDrawable(errorDrawable)
        }
    })
}
