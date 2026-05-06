package com.wanted.poster.maker.activity_app.game

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import android.graphics.PathEffect
import android.graphics.CornerPathEffect

class MapNumberView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    enum class Phase { CHOOSE_NUMBER, PLAYING }

    var phase = Phase.CHOOSE_NUMBER
        set(value) { field = value; invalidate() }

    var selectedNumber = -1
        private set

    var onNumberSelected: ((Int) -> Unit)? = null

    private var mapBitmap: Bitmap? = null
    private var killerBitmap: Bitmap? = null
    private var debugCollisionBitmap: Bitmap? = null
    var showDebugCollision = false
    private val debugPaint = Paint().apply { alpha = 140 }
    private val mapRect = RectF()

    // Vị trí số trên map 1 (normalized 0..1 so với map image)
    private var numberPositions = defaultPositionsForMap1()

    // Killer position — updated during animation
    private val killerPos = PointF(0.46f, 0.38f)
    private var killerAnimator: ValueAnimator? = null

    // Room number revealed by killer (shown in red after animation)
    var killerRevealedNumber = -1

    // Trail path (normalized coords) drawn on map
    private var killerTrail: List<PointF> = emptyList()

    // Rooms already visited/killed by killer → shown in gray
    val deadNumbers = mutableSetOf<Int>()

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CC111111")
    }
    private val selectedBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFCC00")
    }
    private val killerRevealPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E53935")
    }
    private val deadBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#99444444")
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFCC00")
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val deadBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#99888888")
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        setShadowLayer(3f, 0f, 1f, Color.BLACK)
    }
    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CCFF3D00")
        style = Paint.Style.STROKE
        strokeWidth = 10f
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        pathEffect = CornerPathEffect(30f)
    }
    private val trailAndroidPath = Path()

    init {
        loadAssets(1, 1)
    }

    fun animateKillerAlongPath(path: List<PointF>, durationMs: Long = 3500L, onComplete: () -> Unit) {
        killerAnimator?.cancel()
        if (path.size < 2) { onComplete(); return }
        killerAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = durationMs
            interpolator = LinearInterpolator()
            addUpdateListener { va ->
                val progress = va.animatedValue as Float
                val segments = path.size - 1
                val segProgress = progress * segments
                val idx = segProgress.toInt().coerceIn(0, segments - 1)
                val frac = segProgress - idx
                val from = path[idx]; val to = path[(idx + 1).coerceAtMost(path.size - 1)]
                killerPos.x = from.x + (to.x - from.x) * frac
                killerPos.y = from.y + (to.y - from.y) * frac
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) { onComplete() }
            })
            start()
        }
    }

    fun resetKillerPos() {
        killerAnimator?.cancel()
        killerPos.set(0.46f, 0.38f)
        killerRevealedNumber = -1
        invalidate()
    }

    fun cancelAnimation() {
        killerAnimator?.cancel()
    }

    fun setKillerTrail(points: List<PointF>) {
        killerTrail = points
        invalidate()
    }

    fun killNumber(room: Int) {
        deadNumbers.add(room)
        killerRevealedNumber = room
        invalidate()
    }

    fun clearGameState() {
        killerTrail = emptyList()
        deadNumbers.clear()
        killerRevealedNumber = -1
        resetKillerPos()
    }

    fun setDebugCollision(bitmap: Bitmap?) {
        debugCollisionBitmap = bitmap
        invalidate()
    }

    fun loadAssets(mapIndex: Int, killerIndex: Int, hiderSpawns: List<PointF> = emptyList()) {
        try {
            mapBitmap = context.assets.open("Map/$mapIndex.jpg")
                .use { BitmapFactory.decodeStream(it) }
        } catch (_: Exception) {}
        try {
            killerBitmap = context.assets.open("killer_removebg/$killerIndex.png")
                .use { BitmapFactory.decodeStream(it) }
        } catch (_: Exception) {}
        numberPositions = if (hiderSpawns.isNotEmpty())
            hiderSpawns.mapIndexed { i, p -> (i + 1) to p }.toMap()
        else
            defaultPositionsForMap1()
        if (width > 0) computeMapRect(width, height)
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        computeMapRect(w, h)
    }

    private fun computeMapRect(w: Int, h: Int) {
        val bmp = mapBitmap ?: run {
            mapRect.set(0f, 0f, w.toFloat(), h.toFloat()); return
        }
        val bmpRatio = bmp.width.toFloat() / bmp.height
        val viewRatio = w.toFloat() / h
        if (bmpRatio < viewRatio) {
            val mh = h.toFloat()
            val mw = mh * bmpRatio
            mapRect.set((w - mw) / 2f, 0f, (w + mw) / 2f, mh)
        } else {
            val mw = w.toFloat()
            val mh = mw / bmpRatio
            mapRect.set(0f, (h - mh) / 2f, mw, (h + mh) / 2f)
        }
    }

    private fun radius() = mapRect.width() * 0.055f

    private fun nx(p: PointF) = mapRect.left + p.x * mapRect.width()
    private fun ny(p: PointF) = mapRect.top + p.y * mapRect.height()

    override fun onDraw(canvas: Canvas) {
        mapBitmap?.let { canvas.drawBitmap(it, null, mapRect, null) }

        // Debug: collision overlay chồng lên map
        if (showDebugCollision) {
            debugCollisionBitmap?.let { canvas.drawBitmap(it, null, mapRect, debugPaint) }
        }

        // Draw killer trail
        if (killerTrail.size >= 2) {
            trailAndroidPath.reset()
            trailAndroidPath.moveTo(nx(killerTrail[0]), ny(killerTrail[0]))
            for (i in 1 until killerTrail.size) {
                trailAndroidPath.lineTo(nx(killerTrail[i]), ny(killerTrail[i]))
            }
            canvas.drawPath(trailAndroidPath, trailPaint)
        }

        if (phase == Phase.PLAYING) drawKiller(canvas)

        val r = radius()
        textPaint.textSize = r * 1.1f

        numberPositions.forEach { (num, pos) ->
            val cx = nx(pos)
            val cy = ny(pos)
            val isLatestKill = num == killerRevealedNumber
            val isDead = deadNumbers.contains(num)
            val isPlayerChoice = num == selectedNumber

            val bg = when {
                isLatestKill -> killerRevealPaint
                isDead       -> deadBgPaint
                isPlayerChoice -> selectedBgPaint
                else         -> bgPaint
            }
            canvas.drawCircle(cx, cy, r, bg)
            when {
                isLatestKill   -> canvas.drawCircle(cx, cy, r, borderPaint)
                isDead         -> canvas.drawCircle(cx, cy, r, deadBorderPaint)
                isPlayerChoice -> canvas.drawCircle(cx, cy, r, borderPaint)
            }

            textPaint.color = when {
                isLatestKill || isPlayerChoice -> Color.BLACK
                isDead -> Color.parseColor("#AAAAAA")
                else   -> Color.WHITE
            }
            canvas.drawText(num.toString(), cx, cy + r * 0.38f, textPaint)

            // Draw X on dead numbers (not the latest kill)
            if (isDead && !isLatestKill) {
                val xPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#AAAAAA")
                    style = Paint.Style.STROKE
                    strokeWidth = r * 0.25f
                    strokeCap = Paint.Cap.ROUND
                }
                val d = r * 0.5f
                canvas.drawLine(cx - d, cy - d, cx + d, cy + d, xPaint)
                canvas.drawLine(cx + d, cy - d, cx - d, cy + d, xPaint)
            }
        }
    }

    private fun drawKiller(canvas: Canvas) {
        val bmp = killerBitmap ?: return
        val kw = mapRect.width() * 0.28f
        val kh = kw * bmp.height.toFloat() / bmp.width
        val cx = nx(killerPos)
        val cy = ny(killerPos)
        canvas.drawBitmap(bmp, null, RectF(cx - kw / 2, cy - kh / 2, cx + kw / 2, cy + kh / 2), null)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (phase != Phase.CHOOSE_NUMBER || event.action != MotionEvent.ACTION_DOWN) return false
        val tapRadius = radius() * 1.8f
        numberPositions.forEach { (num, pos) ->
            val dx = event.x - nx(pos)
            val dy = event.y - ny(pos)
            if (dx * dx + dy * dy <= tapRadius * tapRadius) {
                selectedNumber = num
                onNumberSelected?.invoke(num)
                invalidate()
                return true
            }
        }
        return true
    }

    fun highlightNumber(number: Int) {
        selectedNumber = number
        invalidate()
    }

    companion object {
        fun defaultPositionsForMap1() = mapOf(
            1  to PointF(0.14f, 0.83f),
            2  to PointF(0.33f, 0.90f),
            3  to PointF(0.73f, 0.90f),
            4  to PointF(0.84f, 0.73f),
            5  to PointF(0.46f, 0.50f),
            6  to PointF(0.80f, 0.48f),
            7  to PointF(0.44f, 0.15f),
            8  to PointF(0.80f, 0.18f),
            9  to PointF(0.13f, 0.50f),
            10 to PointF(0.42f, 0.68f),
            11 to PointF(0.82f, 0.62f),
            12 to PointF(0.09f, 0.18f),
        )
    }
}
