package ani.dantotsu.others

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import androidx.annotation.ColorInt
import androidx.core.content.res.ResourcesCompat
import kotlin.math.max
import kotlin.math.min

/**
 * A secure SurfaceView drop-in replacement for TextView.
 *
 * Renders text directly onto a hardware-secured Surface layer so that it is
 * automatically redacted in screenshots and recordings without glitching the UI.
 */
class SecureTextSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : BaseSecureSurfaceView(context, attrs, defStyleAttr) {

    val textPaint: TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = resolveThemeTextColor(context)
        textSize = spToPx(15f)
    }

    var text: CharSequence? = ""
        set(value) {
            val nonNull = value ?: ""
            if (field != nonNull) {
                field = nonNull
                requestLayout()
                redraw()
            }
        }

    @get:ColorInt
    @setparam:ColorInt
    var textColor: Int
        get() = textPaint.color
        set(value) {
            textPaint.color = value
            redraw()
        }

    var textSizeSp: Float = 15f
        set(value) {
            field = value
            textPaint.textSize = spToPx(value)
            requestLayout()
            redraw()
        }

    var typeface: Typeface?
        get() = textPaint.typeface
        set(value) {
            textPaint.typeface = value
            requestLayout()
            redraw()
        }

    var alignment: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL
        set(value) {
            field = value
            redraw()
        }

    var maxLines: Int = 1
        set(value) {
            field = value
            requestLayout()
            redraw()
        }

    var ellipsize: TextUtils.TruncateAt? = TextUtils.TruncateAt.END
        set(value) {
            field = value
            requestLayout()
            redraw()
        }

    private var staticLayout: StaticLayout? = null

    init {
        if (attrs != null) {
            val typedArray = context.obtainStyledAttributes(
                attrs,
                intArrayOf(
                    android.R.attr.text,
                    android.R.attr.textSize,
                    android.R.attr.textColor,
                    android.R.attr.fontFamily,
                    android.R.attr.maxLines
                )
            )
            try {
                if (typedArray.hasValue(0)) {
                    text = typedArray.getText(0)
                }
                if (typedArray.hasValue(1)) {
                    textPaint.textSize = typedArray.getDimension(1, spToPx(15f))
                }
                if (typedArray.hasValue(2)) {
                    textPaint.color = typedArray.getColor(2, resolveThemeTextColor(context))
                }
                if (typedArray.hasValue(3)) {
                    val fontResId = typedArray.getResourceId(3, 0)
                    if (fontResId != 0) {
                        try {
                            textPaint.typeface = ResourcesCompat.getFont(context, fontResId)
                        } catch (_: Exception) {}
                    }
                }
                if (typedArray.hasValue(4)) {
                    maxLines = typedArray.getInt(4, 1)
                }
            } finally {
                typedArray.recycle()
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)

        val horizPadding = paddingLeft + paddingRight
        val vertPadding = paddingTop + paddingBottom

        val currentText = text ?: ""
        val desiredTextWidth = if (currentText.isNotEmpty()) {
            textPaint.measureText(currentText.toString()).toInt()
        } else {
            0
        }

        val resolvedWidth = when (widthMode) {
            MeasureSpec.EXACTLY -> widthSize
            MeasureSpec.AT_MOST -> min(desiredTextWidth + horizPadding, widthSize)
            else -> desiredTextWidth + horizPadding
        }

        val availableContentWidth = max(0, resolvedWidth - horizPadding)
        staticLayout = createStaticLayout(currentText, availableContentWidth)

        val desiredContentHeight = (staticLayout?.height ?: textPaint.fontSpacing.toInt())
        val resolvedHeight = when (heightMode) {
            MeasureSpec.EXACTLY -> heightSize
            MeasureSpec.AT_MOST -> min(desiredContentHeight + vertPadding, heightSize)
            else -> desiredContentHeight + vertPadding
        }

        setMeasuredDimension(
            resolveSize(resolvedWidth, widthMeasureSpec),
            resolveSize(resolvedHeight, heightMeasureSpec)
        )
    }

    override fun onDrawSurface(canvas: Canvas) {
        val currentText = text ?: ""
        val availableWidth = max(0, width - paddingLeft - paddingRight)
        if (staticLayout == null || staticLayout?.width != availableWidth) {
            staticLayout = createStaticLayout(currentText, availableWidth)
        }

        staticLayout?.let { layout ->
            canvas.save()
            canvas.translate(paddingLeft.toFloat(), paddingTop.toFloat())
            layout.draw(canvas)
            canvas.restore()
        }
    }

    private fun createStaticLayout(source: CharSequence, targetWidth: Int): StaticLayout {
        val safeWidth = max(1, targetWidth)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            StaticLayout.Builder.obtain(source, 0, source.length, textPaint, safeWidth)
                .setAlignment(alignment)
                .setIncludePad(true)
                .setMaxLines(maxLines)
                .setEllipsize(ellipsize)
                .build()
        } else {
            @Suppress("DEPRECATION")
            StaticLayout(
                source,
                textPaint,
                safeWidth,
                alignment,
                1.0f,
                0.0f,
                true
            )
        }
    }

    private fun spToPx(sp: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            sp,
            context.resources.displayMetrics
        )
    }

    private fun resolveThemeTextColor(context: Context): Int {
        val typedValue = TypedValue()
        val theme = context.theme
        if (theme.resolveAttribute(android.R.attr.textColorPrimary, typedValue, true)) {
            if (typedValue.type >= TypedValue.TYPE_FIRST_COLOR_INT && typedValue.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                return typedValue.data
            }
        }
        return Color.WHITE
    }
}
