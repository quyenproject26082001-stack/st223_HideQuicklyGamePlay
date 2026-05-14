package com.wanted.poster.hihi.core.custom.text

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.withStyledAttributes
import com.wanted.poster.hihi.R

class DoubleStrokeTextView : AppCompatTextView {

    // app:outerStrokeColor + app:outerStrokeWidth
    private var outerStrokeWidth = 0f
    private var outerStrokeColor: Int = Color.BLUE

    // app:innerStrokeColor + app:innerStrokeWidth
    private var innerStrokeWidth = 0f
    private var innerStrokeColor: Int = Color.WHITE

    // app:strokeJoinStyle + app:strokeMiter
    private var strokeJoin: Paint.Join = Paint.Join.ROUND
    private var strokeMiter = 10f

    constructor(context: Context) : super(context) {
        init(null)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init(attrs)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        init(attrs)
    }

    private fun init(attrs: AttributeSet?) {
        if (attrs != null) {
            context.withStyledAttributes(attrs, R.styleable.DoubleStrokeTextView) {
                outerStrokeColor =
                    getColor(R.styleable.DoubleStrokeTextView_outerStrokeColor, Color.TRANSPARENT)
                outerStrokeWidth = getDimension(R.styleable.DoubleStrokeTextView_outerStrokeWidth, 0f)

                innerStrokeColor =
                    getColor(R.styleable.DoubleStrokeTextView_innerStrokeColor, Color.TRANSPARENT)
                innerStrokeWidth = getDimension(R.styleable.DoubleStrokeTextView_innerStrokeWidth, 0f)

                strokeMiter = getDimension(R.styleable.DoubleStrokeTextView_strokeMiter, 10f)

                strokeJoin = when (getInt(R.styleable.DoubleStrokeTextView_strokeJoinStyle, 2)) {
                    0 -> Paint.Join.MITER
                    1 -> Paint.Join.BEVEL
                    else -> Paint.Join.ROUND
                }
            }
        }
    }

    fun setDoubleStroke(
        outerColor: Int,
        outerWidth: Float,
        innerColor: Int,
        innerWidth: Float,
        join: Paint.Join = Paint.Join.ROUND,
        miter: Float = 10f
    ) {
        this.outerStrokeColor = outerColor
        this.outerStrokeWidth = outerWidth
        this.innerStrokeColor = innerColor
        this.innerStrokeWidth = innerWidth
        this.strokeJoin = join
        this.strokeMiter = miter
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val paint = paint

        // android:textColor
        val originalColor = currentTextColor
        val originalStyle = paint.style
        val originalJoin = paint.strokeJoin
        val originalMiter = paint.strokeMiter
        val originalStrokeWidth = paint.strokeWidth

        // android:shadowColor + android:shadowDx + android:shadowDy + android:shadowRadius
        val originalShadowRadius = shadowRadius
        val originalShadowDx = shadowDx
        val originalShadowDy = shadowDy
        val originalShadowColor = shadowColor

        paint.clearShadowLayer()
        paint.strokeJoin = strokeJoin
        paint.strokeMiter = strokeMiter

        // Thu tu ve:
        // 1. Shadow layer lech xuong duoi
        // 2. Outer stroke
        // 3. Inner stroke
        // 4. Fill
        //
        // Shadow duoc ve thanh mot lop text rieng, khong dung setShadowLayer mac dinh,
        // de no nam duoi cung va khong de len cac lop stroke.
        if (Color.alpha(originalShadowColor) > 0 &&
            (originalShadowDx != 0f || originalShadowDy != 0f || originalShadowRadius > 0f)
        ) {
            val shadowStrokeWidth = maxOf(outerStrokeWidth, innerStrokeWidth, originalShadowRadius * 0.35f)
            canvas.save()
            canvas.translate(originalShadowDx, originalShadowDy)
            paint.style = if (shadowStrokeWidth > 0f) Paint.Style.FILL_AND_STROKE else Paint.Style.FILL
            paint.strokeWidth = shadowStrokeWidth
            setTextColor(originalShadowColor)
            super.onDraw(canvas)
            canvas.restore()
        }

        // Draw outer stroke.
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = outerStrokeWidth
        setTextColor(outerStrokeColor)
        super.onDraw(canvas)

        // Draw inner stroke.
        paint.strokeWidth = innerStrokeWidth
        setTextColor(innerStrokeColor)
        super.onDraw(canvas)

        // Draw the final fill on top.
        paint.style = Paint.Style.FILL
        paint.strokeWidth = 0f
        setTextColor(originalColor)
        super.onDraw(canvas)

        paint.style = originalStyle
        paint.strokeJoin = originalJoin
        paint.strokeMiter = originalMiter
        paint.strokeWidth = originalStrokeWidth
        paint.setShadowLayer(originalShadowRadius, originalShadowDx, originalShadowDy, originalShadowColor)
    }
}
