package com.wanted.poster.hihi.activity_app.game

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.content.res.ResourcesCompat
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class CountdownRadarView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private var sweepAngle = 0f
    private var displayNumber = 3

    private val bgPaint = Paint().apply { color = Color.argb(204, 0, 0, 0) }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.argb(55, 0, 220, 80)
        strokeWidth = 1.5f
    }

    private val outerRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.argb(180, 0, 220, 80)
        strokeWidth = 2.5f
    }

    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(50, 0, 220, 80)
    }

    private val trailBrightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(100, 0, 220, 80)
    }

    private val armGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(70, 0, 255, 100)
        strokeWidth = 12f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val armPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(230, 0, 255, 100)
        strokeWidth = 3f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val tipDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(255, 100, 255, 140)
    }

    private val numberTypeface by lazy {
        ResourcesCompat.getFont(context, com.wanted.poster.hihi.R.font.tradewinds_regular)
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        setShadowLayer(24f, 0f, 0f, Color.argb(180, 0, 255, 80))
    }

    private val textStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(100, 0, 40, 0)
        textAlign = Paint.Align.CENTER
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
    }

    private val oval = RectF()
    private var currentAnimator: ValueAnimator? = null

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = min(cx, cy) * 0.50f

        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // Grid: concentric circles + crosshairs
        canvas.drawCircle(cx, cy, r * 0.33f, gridPaint)
        canvas.drawCircle(cx, cy, r * 0.66f, gridPaint)
        canvas.drawLine(cx - r, cy, cx + r, cy, gridPaint)
        canvas.drawLine(cx, cy - r, cx, cy + r, gridPaint)

        // Radar trail
        oval.set(cx - r, cy - r, cx + r, cy + r)
        if (sweepAngle > 0f) {
            canvas.drawArc(oval, -90f, sweepAngle, true, trailPaint)
            val brightSweep = minOf(sweepAngle, 90f)
            val brightStart = -90f + sweepAngle - brightSweep
            canvas.drawArc(oval, brightStart, brightSweep, true, trailBrightPaint)
        }

        // Outer ring
        canvas.drawCircle(cx, cy, r, outerRingPaint)

        // Sweep arm
        val armRad = Math.toRadians(-90.0 + sweepAngle)
        val tipX = cx + (cos(armRad) * r).toFloat()
        val tipY = cy + (sin(armRad) * r).toFloat()
        canvas.drawLine(cx, cy, tipX, tipY, armGlowPaint)
        canvas.drawLine(cx, cy, tipX, tipY, armPaint)
        canvas.drawCircle(tipX, tipY, 5f, tipDotPaint)

        // Number
        textPaint.typeface = numberTypeface
        textStrokePaint.typeface = numberTypeface
        val textSize = r * 1.05f
        textPaint.textSize = textSize
        textStrokePaint.textSize = textSize
        textStrokePaint.strokeWidth = textSize * 0.06f
        val label = displayNumber.toString()
        val textY = cy - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(label, cx, textY, textStrokePaint)
        canvas.drawText(label, cx, textY, textPaint)
    }

    fun startCountdown(from: Int, onComplete: () -> Unit) {
        visibility = VISIBLE
        var remaining = from

        fun runNext() {
            if (remaining <= 0) {
                currentAnimator = null
                visibility = GONE
                onComplete()
                return
            }
            displayNumber = remaining
            sweepAngle = 0f
            invalidate()

            val animator = ValueAnimator.ofFloat(0f, 360f).apply {
                duration = 1000L
                interpolator = LinearInterpolator()
                addUpdateListener {
                    sweepAngle = it.animatedValue as Float
                    invalidate()
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        if (currentAnimator === this@apply) {
                            remaining--
                            runNext()
                        }
                    }
                })
            }
            currentAnimator = animator
            animator.start()
        }

        runNext()
    }

    fun cancel() {
        currentAnimator?.cancel()
        currentAnimator = null
        visibility = GONE
    }
}
