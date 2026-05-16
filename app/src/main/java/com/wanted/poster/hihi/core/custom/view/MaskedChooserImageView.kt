package com.wanted.poster.hihi.core.custom.view

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import com.wanted.poster.hihi.R

class MaskedChooserImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private enum class ScaleMode { CENTER_CROP, FIT_CENTER }

    private var gradientPath = Path()
    private var imagePath = Path()
    private val gradientPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val imageBounds = RectF()
    private val destRect = RectF()

    private var imageBitmap: Bitmap? = null
    private var scaleMode = ScaleMode.CENTER_CROP

    init {
        context.obtainStyledAttributes(
            attrs,
            R.styleable.MaskedChooserImageView,
            defStyleAttr,
            0
        ).use { typedArray ->
            scaleMode = when (
                typedArray.getInt(
                    R.styleable.MaskedChooserImageView_chooserScaleType,
                    0
                )
            ) {
                1 -> ScaleMode.FIT_CENTER
                else -> ScaleMode.CENTER_CROP
            }
        }
    }

    fun setImageBitmap(bitmap: Bitmap?) {
        if (imageBitmap === bitmap) return
        imageBitmap = bitmap
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        gradientPath = ChooserMaskResource.createGradientPath(context, w, h)
        imagePath = ChooserMaskResource.createImagePath(context, w, h)
        imagePath.computeBounds(imageBounds, true)
        gradientPaint.shader = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            Color.WHITE, 0xFF8F8F8F.toInt(),
            Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw gradient background (below image)
        val save1 = canvas.save()
        canvas.clipPath(gradientPath)
        canvas.drawPaint(gradientPaint)
        canvas.restoreToCount(save1)

        // Draw image on top
        val bitmap = imageBitmap ?: return
        val save2 = canvas.save()
        canvas.clipPath(imagePath)
        val bitmapW = bitmap.width.toFloat()
        val bitmapH = bitmap.height.toFloat()
        val scale = when (scaleMode) {
            ScaleMode.CENTER_CROP -> maxOf(
                imageBounds.width() / bitmapW,
                imageBounds.height() / bitmapH
            )
            ScaleMode.FIT_CENTER -> minOf(
                imageBounds.width() / bitmapW,
                imageBounds.height() / bitmapH
            )
        }
        val drawW = bitmapW * scale
        val drawH = bitmapH * scale
        destRect.set(
            imageBounds.centerX() - drawW / 2f,
            imageBounds.centerY() - drawH / 2f,
            imageBounds.centerX() + drawW / 2f,
            imageBounds.centerY() + drawH / 2f
        )
        canvas.drawBitmap(bitmap, null, destRect, bitmapPaint)
        canvas.restoreToCount(save2)
    }
}
