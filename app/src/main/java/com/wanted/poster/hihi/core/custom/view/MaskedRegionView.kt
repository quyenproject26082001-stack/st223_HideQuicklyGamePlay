package com.wanted.poster.hihi.core.custom.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.withStyledAttributes
import com.wanted.poster.hihi.R

class MaskedRegionView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private enum class MaskType { OUTER, MAP, OVERLAY }

    private val drawPath = Path()
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private var maskType = MaskType.OVERLAY
    private var fillDrawable: Drawable? = null

    init {
        context.withStyledAttributes(attrs, R.styleable.MaskedRegionView) {
            maskType = when (getInt(R.styleable.MaskedRegionView_maskType, 1)) {
                0 -> MaskType.OUTER
                1 -> MaskType.MAP
                else -> MaskType.OVERLAY
            }
            fillPaint.color = getColor(R.styleable.MaskedRegionView_fillColor, Color.TRANSPARENT)
            val fillDrawableRes = getResourceId(R.styleable.MaskedRegionView_fillDrawable, 0)
            if (fillDrawableRes != 0) {
                fillDrawable = AppCompatResources.getDrawable(context, fillDrawableRes)
            }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        drawPath.set(
            when (maskType) {
                MaskType.OUTER -> ItemMaskResource.createOuterPath(context, w, h)
                MaskType.MAP -> ItemMaskResource.createMapPath(context, w, h)
                MaskType.OVERLAY -> ItemMaskResource.createOverlayPath(context, w, h)
            }
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val drawable = fillDrawable
        if (drawable != null) {
            val saveCount = canvas.save()
            canvas.clipPath(drawPath)
            drawable.bounds.set(0, 0, width, height)
            drawable.draw(canvas)
            canvas.restoreToCount(saveCount)
            return
        }
        canvas.drawPath(drawPath, fillPaint)
    }
}
