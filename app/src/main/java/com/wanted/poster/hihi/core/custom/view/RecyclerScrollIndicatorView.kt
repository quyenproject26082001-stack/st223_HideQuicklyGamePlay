package com.wanted.poster.hihi.core.custom.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.recyclerview.widget.RecyclerView

class RecyclerScrollIndicatorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x999F9F9F.toInt()
        style = Paint.Style.FILL
    }

    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.FILL
    }

    private val trackRect = RectF()
    private val thumbRect = RectF()

    private var scrollOffset = 0
    private var scrollRange = 0
    private var scrollExtent = 0

    fun attachTo(recyclerView: RecyclerView) {
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                syncWith(recyclerView)
            }
        })
        recyclerView.post { syncWith(recyclerView) }
    }

    fun syncWith(recyclerView: RecyclerView) {
        scrollOffset = recyclerView.computeHorizontalScrollOffset()
        scrollRange = recyclerView.computeHorizontalScrollRange()
        scrollExtent = recyclerView.computeHorizontalScrollExtent()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val width = width.toFloat()
        val height = height.toFloat()
        if (width <= 0f || height <= 0f) return

        val radius = height / 2f
        trackRect.set(0f, 0f, width, height)
        canvas.drawRoundRect(trackRect, radius, radius, trackPaint)

        if (scrollRange <= 0 || scrollExtent <= 0) {
            thumbRect.set(0f, 0f, width, height)
            canvas.drawRoundRect(thumbRect, radius, radius, thumbPaint)
            return
        }

        val visibleFraction = (scrollExtent.toFloat() / scrollRange.toFloat()).coerceIn(0f, 1f)
        val minThumbWidth = height
        val thumbWidth = maxOf(width * visibleFraction, minThumbWidth)
        val maxOffset = (scrollRange - scrollExtent).coerceAtLeast(0)
        val travel = (width - thumbWidth).coerceAtLeast(0f)
        val thumbLeft = if (maxOffset == 0) 0f else travel * (scrollOffset.toFloat() / maxOffset.toFloat())

        thumbRect.set(thumbLeft, 0f, thumbLeft + thumbWidth, height)
        canvas.drawRoundRect(thumbRect, radius, radius, thumbPaint)
    }
}
