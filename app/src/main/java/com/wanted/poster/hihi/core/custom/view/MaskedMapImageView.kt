package com.wanted.poster.hihi.core.custom.view

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class MaskedMapImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val drawPath = Path()
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val mapBounds = RectF()
    private val tempRect = RectF()

    private var mapBitmap: Bitmap? = null

    fun setMapBitmap(bitmap: Bitmap?) {
        if (mapBitmap === bitmap) return
        mapBitmap = bitmap
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        drawPath.set(ItemMaskResource.createMapPath(context, w, h))
        drawPath.computeBounds(mapBounds, true)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bitmap = mapBitmap ?: return
        val saveCount = canvas.save()
        canvas.clipPath(drawPath)

        tempRect.set(mapBounds)
        val bitmapWidth = bitmap.width.toFloat()
        val bitmapHeight = bitmap.height.toFloat()
        val scale = maxOf(tempRect.width() / bitmapWidth, tempRect.height() / bitmapHeight)
        val drawWidth = bitmapWidth * scale
        val drawHeight = bitmapHeight * scale
        val left = tempRect.centerX() - drawWidth / 2f
        val top = tempRect.centerY() - drawHeight / 2f
        tempRect.set(left, top, left + drawWidth, top + drawHeight)

        canvas.drawBitmap(bitmap, null, tempRect, bitmapPaint)
        canvas.restoreToCount(saveCount)
    }
}
