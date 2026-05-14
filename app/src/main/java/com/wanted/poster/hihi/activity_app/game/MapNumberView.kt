package com.wanted.poster.hihi.activity_app.game

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.graphics.CornerPathEffect
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.res.ResourcesCompat
import kotlin.math.sin

class MapNumberView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private object NumberUiConfig {
        const val CHOOSE_BADGE_MIN_SIZE_DP = 28f // Kích thước cạnh vuông tối thiểu của ô số ở màn chooseNumber
        const val CHOOSE_BADGE_PADDING_DP = 2f // Khoảng đệm bên trong quanh text để nền vuông to thêm
        const val CHOOSE_BADGE_RADIUS_DP = 4f // Độ bo góc của nền vuông ở màn chooseNumber
        const val CHOOSE_SELECTED_BORDER_WIDTH_DP = 1f // Độ dày viền khi ô số đang được chọn ở màn chooseNumber

        const val NUMBER_TEXT_SIZE_SP = 25f // Cỡ chữ của số thứ tự
        const val NUMBER_STROKE_WIDTH_DP = 2f // Độ dày viền đen bao quanh chữ

        const val SELECTED_MARKER_WIDTH_DP = 18f // Chiều rộng icon item_select_player
        const val SELECTED_MARKER_HEIGHT_DP = 12f // Chiều cao icon item_select_player
        const val SELECTED_MARKER_GAP_DP = 4f // Khoảng cách giữa item_select_player và text số
        const val SELECTED_MARKER_OFFSET_X_DP = 0f // Tinh chỉnh item_select_player sang trái/phải
        const val SELECTED_MARKER_OFFSET_Y_DP = 0f // Tinh chỉnh item_select_player lên/xuống

        const val DEAD_MARKER_SIZE_DP = 18f // Kích thước icon ic_dead đè lên trên số đã chết
    }

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
    private val killerSpawnDefault = PointF(0.46f, 0.38f)
    private var killerAnimator: ValueAnimator? = null

    // Room number revealed by killer (shown in red after animation)
    var killerRevealedNumber = -1

    // Trail path (normalized coords) drawn on map
    private var killerTrail: List<PointF> = emptyList()

    // Rooms already visited/killed by killer → shown in gray
    val deadNumbers = mutableSetOf<Int>()

    // Sound indicator — shown at room position while killer is heading there
    private var soundIndicatorPos: PointF? = null
    private var soundRingPhase = 0f
    private var soundRingAnimator: ValueAnimator? = null

    // Kill animation — shake + red flash
    private var killerShakeOffset = 0f
    private var flashAlpha = 0f
    private var shakeAnimator: ValueAnimator? = null
    private var flashAnimator: ValueAnimator? = null

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CC111111")
    }
    private val selectedBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFCC00")
    }
    private val chooseNumberBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D9D9D9")
    }
    private val chooseNumberSelectedBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val chooseNumberSelectedBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#B4D334")
        style = Paint.Style.STROKE
        strokeWidth = dp(NumberUiConfig.CHOOSE_SELECTED_BORDER_WIDTH_DP)
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
    private val selectedPlayerMarker = AppCompatResources.getDrawable(
        context,
        com.wanted.poster.hihi.R.drawable.item_select_player
    )
    private val deadMarkerBitmap = BitmapFactory.decodeResource(
        resources,
        com.wanted.poster.hihi.R.drawable.ic_dead
    )
    private val numberTypeface = ResourcesCompat.getFont(context, com.wanted.poster.hihi.R.font.tradewinds_regular)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        setShadowLayer(0.1f, 0f, 6f, Color.parseColor("#80000000"))
        typeface = numberTypeface
    }
    private val numberStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeMiter = 10f
        strokeWidth = dp(NumberUiConfig.NUMBER_STROKE_WIDTH_DP)
        color = Color.BLACK
        setShadowLayer(0.1f, 0f, 6f, Color.parseColor("#80000000"))
        typeface = numberTypeface
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

    private val flashOverlayPaint = Paint().apply { color = Color.RED }

    private val soundRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val soundBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(210, 0xFF, 0x88, 0x00)
    }
    private val soundIconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        setShadowLayer(3f, 0f, 1f, Color.BLACK)
    }

    init {
        loadAssets(1, null)
    }

    fun animateKillerAlongPath(
        path: List<PointF>,
        durationMs: Long = 6500L,
        doorLines: List<Pair<PointF, PointF>> = emptyList(),
        onCrossDoor: (() -> Unit)? = null,
        onComplete: () -> Unit
    ) {
        killerAnimator?.cancel()
        if (path.size < 2) { onComplete(); return }

        val segments = path.size - 1
        val rng = java.util.Random()

        fun segmentsIntersect(p1: PointF, p2: PointF, p3: PointF, p4: PointF): Boolean {
            val d1x = p2.x - p1.x; val d1y = p2.y - p1.y
            val d2x = p4.x - p3.x; val d2y = p4.y - p3.y
            val cross = d1x * d2y - d1y * d2x
            if (cross == 0f) return false
            val dx = p3.x - p1.x; val dy = p3.y - p1.y
            val t = (dx * d2y - dy * d2x) / cross
            val u = (dx * d1y - dy * d1x) / cross
            return t in 0f..1f && u in 0f..1f
        }

        fun segmentNearDoor(a: PointF, b: PointF): Boolean =
            doorLines.any { (p3, p4) -> segmentsIntersect(a, b, p3, p4) }

        val weights = FloatArray(segments) { i ->
            if (segmentNearDoor(path[i], path[i + 1]))
                3.5f + rng.nextFloat() * 1.5f
            else
                0.4f + rng.nextFloat() * 1.8f
        }
        val total = weights.sum()

        val timeBounds = FloatArray(segments + 1)
        for (i in 0 until segments) timeBounds[i + 1] = timeBounds[i] + weights[i] / total

        // Track segment để fire door sound đúng lúc killer chạm door segment
        var prevSeg = -1
        var lastDoorFiredSeg = -1

        killerAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = durationMs
            interpolator = LinearInterpolator()
            addUpdateListener { va ->
                val t = va.animatedValue as Float
                var seg = segments - 1
                for (i in 0 until segments) {
                    if (t <= timeBounds[i + 1]) { seg = i; break }
                }
                if (seg != prevSeg) {
                    prevSeg = seg
                    if (onCrossDoor != null && seg != lastDoorFiredSeg && segmentNearDoor(path[seg], path[seg + 1])) {
                        lastDoorFiredSeg = seg
                        onCrossDoor()
                    }
                }
                val segStart = timeBounds[seg]
                val segEnd   = timeBounds[seg + 1]
                val frac = if (segEnd > segStart) ((t - segStart) / (segEnd - segStart)).coerceIn(0f, 1f) else 1f
                val from = path[seg]; val to = path[seg + 1]
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
        killerPos.set(killerSpawnDefault.x, killerSpawnDefault.y)
        killerRevealedNumber = -1
        invalidate()
    }

    fun cancelAnimation() {
        killerAnimator?.cancel()
    }

    fun pauseAnimation() {
        killerAnimator?.pause()
        soundRingAnimator?.pause()
        shakeAnimator?.pause()
        flashAnimator?.pause()
    }

    fun resumeAnimation() {
        killerAnimator?.resume()
        soundRingAnimator?.resume()
        shakeAnimator?.resume()
        flashAnimator?.resume()
    }

    fun showSoundIndicator(pos: PointF) {
        soundIndicatorPos = pos
        soundRingAnimator?.cancel()
        soundRingAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1100L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            addUpdateListener {
                soundRingPhase = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    fun clearSoundIndicator() {
        soundRingAnimator?.cancel()
        soundRingAnimator = null
        soundIndicatorPos = null
        invalidate()
    }

    fun animateKillShake(onComplete: () -> Unit) {
        shakeAnimator?.cancel()
        flashAnimator?.cancel()
        val amplitude = mapRect.width() * 0.03f
        shakeAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 380L
            interpolator = LinearInterpolator()
            addUpdateListener {
                val t = it.animatedValue as Float
                killerShakeOffset = (sin(t * 3 * Math.PI * 2) * amplitude).toFloat()
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    killerShakeOffset = 0f
                    flashAnimator = ValueAnimator.ofFloat(0.45f, 0f).apply {
                        duration = 250L
                        addUpdateListener {
                            flashAlpha = it.animatedValue as Float
                            invalidate()
                        }
                        addListener(object : AnimatorListenerAdapter() {
                            override fun onAnimationEnd(animation: Animator) {
                                flashAlpha = 0f
                                onComplete()
                            }
                        })
                        start()
                    }
                }
            })
            start()
        }
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
        clearSoundIndicator()
    }

    fun setDebugCollision(bitmap: Bitmap?) {
        debugCollisionBitmap = bitmap
        invalidate()
    }

    fun loadAssets(
        mapIndex: Int,
        killerAssetPath: String?,
        hiderSpawns: List<PointF> = emptyList(),
        killerSpawn: PointF? = null
    ) {
        try {
            mapBitmap = context.assets.open("Map/$mapIndex.jpg")
                .use { BitmapFactory.decodeStream(it) }
        } catch (_: Exception) {}
        try {
            val resolvedKillerAssetPath = killerAssetPath
                ?: context.assets.list("killer_removebg")?.firstOrNull()?.let { "killer_removebg/$it" }
            killerBitmap = resolvedKillerAssetPath?.let { path ->
                context.assets.open(path).use { BitmapFactory.decodeStream(it) }
            }
        } catch (_: Exception) {}
        val spawn = killerSpawn ?: PointF(0.46f, 0.38f)
        killerSpawnDefault.set(spawn.x, spawn.y)
        killerPos.set(spawn.x, spawn.y)
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

    private fun dp(value: Float) = value * resources.displayMetrics.density
    private fun sp(value: Float) = value * resources.displayMetrics.scaledDensity

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

        soundIndicatorPos?.let { drawSoundIndicator(canvas, it) }

        val isChoosePhase = phase == Phase.CHOOSE_NUMBER
        val badgeRadius = dp(NumberUiConfig.CHOOSE_BADGE_RADIUS_DP)
        textPaint.textSize = sp(NumberUiConfig.NUMBER_TEXT_SIZE_SP)
        numberStrokePaint.textSize = textPaint.textSize
        val selectedMarkerWidth = dp(NumberUiConfig.SELECTED_MARKER_WIDTH_DP)
        val selectedMarkerHeight = dp(NumberUiConfig.SELECTED_MARKER_HEIGHT_DP)
        val selectedMarkerGap = dp(NumberUiConfig.SELECTED_MARKER_GAP_DP)
        val selectedMarkerOffsetX = dp(NumberUiConfig.SELECTED_MARKER_OFFSET_X_DP)
        val selectedMarkerOffsetY = dp(NumberUiConfig.SELECTED_MARKER_OFFSET_Y_DP)
        val deadMarkerSize = dp(NumberUiConfig.DEAD_MARKER_SIZE_DP)
        val chooseBadgeMinSize = dp(NumberUiConfig.CHOOSE_BADGE_MIN_SIZE_DP)
        val chooseBadgePadding = dp(NumberUiConfig.CHOOSE_BADGE_PADDING_DP)
        val textBounds = Rect()

        numberPositions.forEach { (num, pos) ->
            val cx = nx(pos)
            val cy = ny(pos)
            val isLatestKill = num == killerRevealedNumber
            val isDead = deadNumbers.contains(num)
            val isPlayerChoice = num == selectedNumber
            val label = num.toString()
            val badgeSize = if (isChoosePhase) {
                val textHeight = textPaint.fontMetrics.run { bottom - top }
                maxOf(
                    chooseBadgeMinSize,
                    textPaint.measureText(label) + chooseBadgePadding * 2f,
                    textHeight + chooseBadgePadding * 2f
                )
            } else {
                0f
            }
            val badgeWidth = badgeSize
            val badgeHeight = badgeSize
            val left = cx - badgeWidth / 2f
            val top = cy - badgeHeight / 2f
            val right = cx + badgeWidth / 2f
            val bottom = cy + badgeHeight / 2f

            if (isChoosePhase) {
                val bg = if (isPlayerChoice) chooseNumberSelectedBgPaint else chooseNumberBgPaint
                if (isPlayerChoice) {
                    chooseNumberSelectedBgPaint.shader = LinearGradient(
                        cx,
                        top,
                        cx,
                        bottom,
                        Color.parseColor("#8ABABD"),
                        Color.parseColor("#031210"),
                        Shader.TileMode.CLAMP
                    )
                } else {
                    chooseNumberSelectedBgPaint.shader = null
                }
                canvas.drawRoundRect(left, top, right, bottom, badgeRadius, badgeRadius, bg)
                if (isPlayerChoice) {
                    canvas.drawRoundRect(left, top, right, bottom, badgeRadius, badgeRadius, chooseNumberSelectedBorderPaint)
                }
            }

            textPaint.color = when {
                isChoosePhase -> Color.WHITE
                isLatestKill -> Color.parseColor("#E53935")
                isDead -> Color.parseColor("#AAAAAA")
                else   -> Color.WHITE
            }
            val textY = cy - (textPaint.descent() + textPaint.ascent()) / 2f
            canvas.drawText(label, cx, textY, numberStrokePaint)
            canvas.drawText(label, cx, textY, textPaint)

            if (!isChoosePhase && isPlayerChoice) {
                textPaint.getTextBounds(label, 0, label.length, textBounds)
                val textTop = textY + textBounds.top
                val markerCenterX = cx + selectedMarkerOffsetX
                val markerTop = textTop - selectedMarkerHeight - selectedMarkerGap + selectedMarkerOffsetY
                selectedPlayerMarker?.setBounds(
                    (markerCenterX - selectedMarkerWidth / 2f).toInt(),
                    markerTop.toInt(),
                    (markerCenterX + selectedMarkerWidth / 2f).toInt(),
                    (markerTop + selectedMarkerHeight).toInt()
                )
                selectedPlayerMarker?.draw(canvas)
            }

            if (!isChoosePhase && isDead && deadMarkerBitmap != null) {
                val deadHalf = deadMarkerSize / 2f
                val deadRect = RectF(
                    cx - deadHalf,
                    cy - deadHalf,
                    cx + deadHalf,
                    cy + deadHalf
                )
                canvas.drawBitmap(deadMarkerBitmap, null, deadRect, null)
            }
        }

        // Red flash overlay on kill
        if (flashAlpha > 0f) {
            flashOverlayPaint.alpha = (flashAlpha * 255).toInt()
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), flashOverlayPaint)
        }
    }

    private fun drawSoundIndicator(canvas: Canvas, pos: PointF) {
        val cx = nx(pos)
        val cy = ny(pos)
        val r = radius()

        // 3 staggered expanding rings
        for (i in 0 until 3) {
            val phase = (soundRingPhase + i / 3f) % 1f
            val ringR = r * (0.9f + phase * 2.2f)
            val alpha = ((1f - phase) * 220).toInt()
            soundRingPaint.color = Color.argb(alpha, 0xFF, 0x99, 0x00)
            soundRingPaint.strokeWidth = r * 0.18f
            canvas.drawCircle(cx, cy, ringR, soundRingPaint)
        }

        // Background circle
        canvas.drawCircle(cx, cy, r * 0.85f, soundBgPaint)

        // Music note icon
        soundIconPaint.textSize = r * 1.1f
        canvas.drawText("♪", cx, cy + r * 0.38f, soundIconPaint)
    }

    private fun drawKiller(canvas: Canvas) {
        val bmp = killerBitmap ?: return
        val kw = mapRect.width() * 0.15f
        val kh = kw * bmp.height.toFloat() / bmp.width
        val cx = nx(killerPos) + killerShakeOffset
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
