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
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.res.ResourcesCompat
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class MapNumberView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private data class ThrowableAnim(
        val toolType: ResistanceToolType,
        val from: PointF,
        val to: PointF,
        val progress: Float,
        val rotationDeg: Float
    )

    private data class DisplayMoveAnim(
        val displayNum: Int,
        val path: List<PointF>,
        val progress: Float
    )

    private object NumberUiConfig {

        object Badge {
            val MIN_SIZE_DP get() = GameConfig.SPAWN_BADGE_MIN_SIZE_DP
            const val PADDING_DP = 2f
            const val RADIUS_DP = 4f
            const val SELECTED_BORDER_WIDTH_DP = 1f
            const val TAKEN_BORDER_WIDTH_DP = 1f
            const val AVATAR_CELL_RADIUS_DP = 5f
            const val LOCK_ICON_SIZE_DP = 14f
            const val DEAD_OVER_AVATAR_RATIO = 0.5f
        }

        object Text {
            const val SIZE_SP = 25f
            const val STROKE_WIDTH_DP = 2f
        }

        object Marker {
            const val SELECTED_WIDTH_DP = 18f
            const val SELECTED_HEIGHT_DP = 12f
            const val SELECTED_GAP_DP = 4f
            const val SELECTED_OFFSET_X_DP = 0f
            const val SELECTED_OFFSET_Y_DP = 0f
            const val DEAD_SIZE_DP = 18f
        }

        object Avatar {
            const val INNER_RATIO = 0.93f
            const val TAP_RADIUS_RATIO = 1.8f
        }

        object Sound {
            const val BG_RADIUS_RATIO = 0.85f
            const val ICON_SIZE_RATIO = 1.1f
            const val ICON_Y_OFFSET_RATIO = 0.38f
            const val RING_STROKE_RATIO = 0.18f
            const val RING_EXPAND_START = 0.9f
            const val RING_EXPAND_RANGE = 2.2f
            const val RING_ALPHA_FACTOR = 220
        }

        object KillAnim {
            const val SHAKE_AMPLITUDE_RATIO = 0.03f
            const val FLASH_INITIAL_ALPHA = 0.45f
        }

        object Colors {
            val BG = Color.parseColor("#CC111111")
            val ACCENT = Color.parseColor("#FFCC00")
            val CHOOSE_NUMBER_BG = Color.parseColor("#D9D9D9")
            val CHOOSE_SELECTED_BORDER = Color.parseColor("#B4D334")
            val KILL_RED = Color.parseColor("#E53935")
            val DEAD_BG = Color.parseColor("#99444444")
            val DEAD_BORDER = Color.parseColor("#99888888")
            val TRAIL = Color.parseColor("#CCFF3D00")
            val SOUND_BG = Color.argb(210, 0xFF, 0x88, 0x00)
            val CHOOSE_GRADIENT_TOP = Color.parseColor("#8ABABD")
            val CHOOSE_GRADIENT_BOTTOM = Color.parseColor("#031210")
            val DEAD_TEXT = Color.parseColor("#AAAAAA")
            val SHADOW = Color.parseColor("#80000000")
        }
    }

    enum class Phase { CHOOSE_NUMBER, PLAYING }

    var phase = Phase.CHOOSE_NUMBER
        set(value) {
            field = value
            // Khi chuyển sang PLAYING, ẩn tất cả player (progress=0) cho đến khi
            // animatePlayingSpawnsIntro được gọi — tránh flash vị trí cuối trước animation.
            if (value == Phase.PLAYING) spawnIntroProgress = 0f
            invalidate()
        }

    var selectedNumber = -1
        private set

    var onNumberSelected: ((Int) -> Unit)? = null

    private var mapBitmap: Bitmap? = null
    private var killerBitmap: Bitmap? = null
    private var bombBitmap: Bitmap? = null
    private var explosionBitmap: Bitmap? = null
    private var debugCollisionBitmap: Bitmap? = null
    var showDebugCollision = GameConfig.DEBUG_KILLER_COLLISION
    private val debugPaint = Paint().apply { alpha = 140 }
    private val mapRect = RectF()

    // Mutable vì player có thể đổi sang spawn khác sau khi phản kháng.
    private var numberPositions: MutableMap<Int, PointF> = defaultPositionsForMap1().toMutableMap()

    private val killerPos = PointF(0.46f, 0.38f)
    private val killerSpawnDefault = PointF(0.46f, 0.38f)
    private var killerAnimator: ValueAnimator? = null

    var killerRevealedNumber = -1

    private var killerTrail: List<PointF> = emptyList()

    val deadNumbers = mutableSetOf<Int>()

    private val spawnAvatarBitmaps: MutableMap<Int, Bitmap?> = mutableMapOf()

    var takenNumbers: Set<Int> = emptySet()

    private var soundIndicatorPos: PointF? = null
    private var soundRingPhase = 0f
    private var soundRingAnimator: ValueAnimator? = null

    private var spawnIntroProgress = 1f
    private var spawnIntroAnimator: ValueAnimator? = null
    // Path thực (qua KillerPathfinder) cho từng player khi chạy từ killerSpawn vào hideSpawn.
    private var introEntrancePaths: Map<Int, List<PointF>> = emptyMap()
    // Tổng thời gian animation mở màn (ms), được tính từ tốc độ + stagger + độ dài path mỗi player.
    private var introTotalMs: Long = 0L
    // Thời điểm player bắt đầu rời killerSpawn, tính theo tỉ lệ [0..1] của introTotalMs.
    private var introPlayerStartFrac: Map<Int, Float> = emptyMap()
    // Phần thời gian player di chuyển, tính theo tỉ lệ [0..1] của introTotalMs.
    private var introPlayerTravelFrac: Map<Int, Float> = emptyMap()
    private var throwableAnim: ThrowableAnim? = null
    private var throwableAnimator: ValueAnimator? = null
    private var displayMoveAnim: DisplayMoveAnim? = null
    private var displayMoveAnimator: ValueAnimator? = null
    private var killerStunPhase = 0f
    private var killerStunActive = false
    private var killerRecoilAnimator: ValueAnimator? = null
    private var killerStunAnimator: ValueAnimator? = null

    private var killerShakeOffset = 0f
    private var flashAlpha = 0f
    private var shakeAnimator: ValueAnimator? = null
    private var flashAnimator: ValueAnimator? = null

    private var explosionPos: PointF? = null
    private var explosionAlpha = 0f
    private var explosionScale = 1f
    private var explosionAnimator: ValueAnimator? = null
    private val explosionPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = NumberUiConfig.Colors.BG
    }
    private val selectedBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = NumberUiConfig.Colors.ACCENT
    }
    private val chooseNumberBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = NumberUiConfig.Colors.CHOOSE_NUMBER_BG
    }
    private val chooseNumberSelectedBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val chooseNumberSelectedBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = NumberUiConfig.Colors.CHOOSE_SELECTED_BORDER
        style = Paint.Style.STROKE
        strokeWidth = dp(NumberUiConfig.Badge.SELECTED_BORDER_WIDTH_DP)
    }
    private val killerRevealPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = NumberUiConfig.Colors.KILL_RED
    }
    private val deadBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = NumberUiConfig.Colors.DEAD_BG
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = NumberUiConfig.Colors.ACCENT
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val deadBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = NumberUiConfig.Colors.DEAD_BORDER
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
    private val lockBitmap = try {
        BitmapFactory.decodeResource(resources, com.wanted.poster.hihi.R.drawable.ic_lock)
    } catch (_: Exception) { null }
    private val chooseTakenBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = dp(NumberUiConfig.Badge.TAKEN_BORDER_WIDTH_DP)
    }
    private val chooseTakenAvatarClipPath = Path()
    private val numberTypeface = ResourcesCompat.getFont(context, com.wanted.poster.hihi.R.font.tradewinds_regular)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        setShadowLayer(0.1f, 0f, 6f, NumberUiConfig.Colors.SHADOW)
        typeface = numberTypeface
    }
    private val numberStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeMiter = 10f
        strokeWidth = dp(NumberUiConfig.Text.STROKE_WIDTH_DP)
        color = Color.BLACK
        setShadowLayer(0.1f, 0f, 6f, NumberUiConfig.Colors.SHADOW)
        typeface = numberTypeface
    }
    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = NumberUiConfig.Colors.TRAIL
        style = Paint.Style.STROKE
        strokeWidth = 10f
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        pathEffect = CornerPathEffect(30f)
    }
    private val trailAndroidPath = Path()

    private val flashOverlayPaint = Paint().apply { color = Color.RED }

    private val avatarBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = NumberUiConfig.Colors.ACCENT
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val avatarDeadBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = NumberUiConfig.Colors.DEAD_BORDER
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val avatarBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
    }
    private val avatarClipPath = Path()

    private val soundRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val soundBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = NumberUiConfig.Colors.SOUND_BG
    }
    private val soundIconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        setShadowLayer(3f, 0f, 1f, Color.BLACK)
    }
    private val throwablePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E8DDD0")
        style = Paint.Style.FILL
    }
    private val throwableAccentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#5D4037")
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
        strokeCap = Paint.Cap.ROUND
    }
    private val stunPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFD54F")
        style = Paint.Style.FILL
        setShadowLayer(5f, 0f, 0f, Color.parseColor("#66FFEE58"))
    }

    init {
        loadAssets(1, null)
        try { bombBitmap = context.assets.open("bomb/bomb.png").use { BitmapFactory.decodeStream(it) } } catch (_: Exception) {}
        try { explosionBitmap = context.assets.open("bomb/explosion.png").use { BitmapFactory.decodeStream(it) } } catch (_: Exception) {}
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
        killerRecoilAnimator?.cancel()
        killerStunAnimator?.cancel()
        killerPos.set(killerSpawnDefault.x, killerSpawnDefault.y)
        killerStunActive = false
        killerStunPhase = 0f
        killerRevealedNumber = -1
        invalidate()
    }

    fun cancelAnimation() {
        killerAnimator?.cancel()
        killerRecoilAnimator?.cancel()
        killerStunAnimator?.cancel()
        throwableAnimator?.cancel()
        displayMoveAnimator?.cancel()
        explosionAnimator?.cancel()
    }

    fun pauseAnimation() {
        killerAnimator?.pause()
        killerRecoilAnimator?.pause()
        killerStunAnimator?.pause()
        soundRingAnimator?.pause()
        throwableAnimator?.pause()
        displayMoveAnimator?.pause()
        shakeAnimator?.pause()
        flashAnimator?.pause()
    }

    fun resumeAnimation() {
        killerAnimator?.resume()
        killerRecoilAnimator?.resume()
        killerStunAnimator?.resume()
        soundRingAnimator?.resume()
        throwableAnimator?.resume()
        displayMoveAnimator?.resume()
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
        val amplitude = mapRect.width() * NumberUiConfig.KillAnim.SHAKE_AMPLITUDE_RATIO
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
                    flashAnimator = ValueAnimator.ofFloat(NumberUiConfig.KillAnim.FLASH_INITIAL_ALPHA, 0f).apply {
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

    fun setSpawnAvatarBitmap(displayNum: Int, bitmap: Bitmap?) {
        spawnAvatarBitmaps[displayNum] = bitmap
        invalidate()
    }

    fun animatePlayingSpawnsIntro(onComplete: (() -> Unit)? = null) {
        if (phase != Phase.PLAYING || numberPositions.isEmpty()) {
            spawnIntroProgress = 1f
            invalidate()
            onComplete?.invoke()
            return
        }

        val totalMs = if (introTotalMs > 0L) introTotalMs else {
            val n = numberPositions.size
            GameConfig.SPAWN_ENTRANCE_STAGGER_MS * (n - 1) + GameConfig.SPAWN_ENTRANCE_MIN_TRAVEL_MS
        }

        spawnIntroAnimator?.cancel()
        val animator = ValueAnimator.ofFloat(0f, 1f)
        animator.duration = totalMs
        animator.interpolator = LinearInterpolator()
        animator.addUpdateListener {
            spawnIntroProgress = it.animatedValue as Float
            invalidate()
        }
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationCancel(animation: Animator) {
                if (spawnIntroAnimator === animator) {
                    spawnIntroAnimator = null
                }
                spawnIntroProgress = 1f
                invalidate()
            }

            override fun onAnimationEnd(animation: Animator) {
                if (spawnIntroAnimator === animator) {
                    spawnIntroAnimator = null
                    spawnIntroProgress = 1f
                    invalidate()
                    onComplete?.invoke()
                }
            }
        })
        spawnIntroProgress = 0f
        spawnIntroAnimator = animator
        animator.start()
    }

    fun setIntroEntrancePaths(paths: Map<Int, List<PointF>>) {
        introEntrancePaths = paths

        val staggerMs = GameConfig.SPAWN_ENTRANCE_STAGGER_MS.toFloat()
        val speed = GameConfig.SPAWN_ENTRANCE_SPEED
        val minMs = GameConfig.SPAWN_ENTRANCE_MIN_TRAVEL_MS.toFloat()

        val sortedNums = paths.keys.sorted()

        // Tính thời gian di chuyển của mỗi player dựa vào độ dài path thực.
        val travelMsMap: Map<Int, Float> = paths.mapValues { (_, path) ->
            var len = 0f
            for (i in 0 until path.lastIndex) {
                val dx = path[i + 1].x - path[i].x
                val dy = path[i + 1].y - path[i].y
                len += sqrt(dx * dx + dy * dy)
            }
            (len / speed * 1000f).coerceAtLeast(minMs)
        }

        // Tổng animation = max(stagger_start + travel) trên tất cả player.
        val totalMs = sortedNums.mapIndexed { i, num ->
            i * staggerMs + (travelMsMap[num] ?: minMs)
        }.maxOrNull()?.toLong()?.coerceAtLeast(minMs.toLong()) ?: minMs.toLong()

        introTotalMs = totalMs
        val totalF = totalMs.toFloat()

        introPlayerStartFrac = sortedNums.mapIndexed { i, num ->
            num to (i * staggerMs / totalF)
        }.toMap()

        introPlayerTravelFrac = travelMsMap.mapValues { (_, tMs) -> tMs / totalF }
    }

    fun currentDisplayPosition(displayNum: Int): PointF? {
        val base = numberPositions[displayNum] ?: return null
        val moving = displayMoveAnim
        return if (moving?.displayNum == displayNum) {
            pointOnPath(moving.path, moving.progress)
        } else {
            PointF(base.x, base.y)
        }
    }

    // Đây chỉ là animation vật ném. Logic game quyết định phản kháng có thành công hay không;
    // view chỉ chịu trách nhiệm vẽ đường ném từ player tới killer.
    fun animateResistanceThrow(
        from: PointF,
        to: PointF,
        toolType: ResistanceToolType,
        onComplete: () -> Unit
    ) {
        throwableAnimator?.cancel()
        val animator = ValueAnimator.ofFloat(0f, 1f)
        animator.duration = GameConfig.PLAYER_RESISTANCE_THROW_DURATION_MS
        animator.interpolator = LinearInterpolator()
        animator.addUpdateListener {
            val progress = it.animatedValue as Float
            throwableAnim = ThrowableAnim(
                toolType = toolType,
                from = PointF(from.x, from.y),
                to = PointF(to.x, to.y),
                progress = progress,
                rotationDeg = progress * 520f
            )
            invalidate()
        }
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationCancel(animation: Animator) {
                if (throwableAnimator === animator) throwableAnimator = null
                throwableAnim = null
                invalidate()
            }

            override fun onAnimationEnd(animation: Animator) {
                if (throwableAnimator === animator) throwableAnimator = null
                throwableAnim = null
                invalidate()
                onComplete()
            }
        })
        throwableAnimator = animator
        animator.start()
    }

    // Recoil và choáng được xử lý ngay trong view này để sprite killer
    // và hiệu ứng choáng luôn khớp với vị trí / trail hiện tại của killer.
    fun animateKillerRecoilAndStun(
        recoilTarget: PointF,
        recoilDurationMs: Long = GameConfig.PLAYER_RESISTANCE_RECOIL_DURATION_MS,
        stunDurationMs: Long = GameConfig.PLAYER_RESISTANCE_STUN_DURATION_MS,
        onComplete: () -> Unit
    ) {
        killerRecoilAnimator?.cancel()
        killerStunAnimator?.cancel()
        killerStunActive = false
        killerStunPhase = 0f

        val from = PointF(killerPos.x, killerPos.y)
        val animator = ValueAnimator.ofFloat(0f, 1f)
        animator.duration = recoilDurationMs
        animator.interpolator = DecelerateInterpolator()
        animator.addUpdateListener {
            val progress = it.animatedValue as Float
            killerPos.x = from.x + (recoilTarget.x - from.x) * progress
            killerPos.y = from.y + (recoilTarget.y - from.y) * progress
            invalidate()
        }
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationCancel(animation: Animator) {
                if (killerRecoilAnimator === animator) killerRecoilAnimator = null
                killerPos.set(recoilTarget.x, recoilTarget.y)
                invalidate()
            }

            override fun onAnimationEnd(animation: Animator) {
                if (killerRecoilAnimator === animator) killerRecoilAnimator = null
                killerPos.set(recoilTarget.x, recoilTarget.y)
                startKillerStun(stunDurationMs, onComplete)
            }
        })
        killerRecoilAnimator = animator
        animator.start()
    }

    fun animateExplosion(killerPos: PointF, onComplete: () -> Unit) {
        explosionAnimator?.cancel()
        explosionPos = PointF(killerPos.x, killerPos.y)
        explosionScale = 0.4f
        explosionAlpha = 1f
        invalidate()

        val animator = ValueAnimator.ofFloat(0f, 1f)
        animator.duration = GameConfig.BOMB_EXPLOSION_ANIMATION_DURATION_MS
        animator.interpolator = DecelerateInterpolator()
        animator.addUpdateListener {
            val t = it.animatedValue as Float
            explosionScale = 0.4f + t * 1.8f
            explosionAlpha = 1f - t
            invalidate()
        }
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                if (explosionAnimator === animator) { explosionAnimator = null; explosionPos = null }
                invalidate()
                onComplete()
            }
            override fun onAnimationCancel(animation: Animator) {
                if (explosionAnimator === animator) { explosionAnimator = null; explosionPos = null }
                invalidate()
            }
        })
        explosionAnimator = animator
        animator.start()
    }

    // Di chuyển một display number dọc theo path, rồi chốt lại vị trí spawn mới.
    fun animateDisplayRelocation(displayNum: Int, path: List<PointF>, onComplete: () -> Unit) {
        if (path.size < 2) {
            path.lastOrNull()?.let { numberPositions[displayNum] = PointF(it.x, it.y) }
            invalidate()
            onComplete()
            return
        }

        displayMoveAnimator?.cancel()
        val animator = ValueAnimator.ofFloat(0f, 1f)
        animator.duration = GameConfig.PLAYER_RESISTANCE_RELOCATE_DURATION_MS
        animator.interpolator = DecelerateInterpolator()
        animator.addUpdateListener {
            displayMoveAnim = DisplayMoveAnim(displayNum, path, it.animatedValue as Float)
            invalidate()
        }
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationCancel(animation: Animator) {
                if (displayMoveAnimator === animator) displayMoveAnimator = null
                displayMoveAnim = null
                invalidate()
            }

            override fun onAnimationEnd(animation: Animator) {
                if (displayMoveAnimator === animator) displayMoveAnimator = null
                val finalPos = path.last()
                numberPositions[displayNum] = PointF(finalPos.x, finalPos.y)
                displayMoveAnim = null
                invalidate()
                onComplete()
            }
        })
        displayMoveAnimator = animator
        animator.start()
    }

    private fun startKillerStun(stunDurationMs: Long = GameConfig.PLAYER_RESISTANCE_STUN_DURATION_MS, onComplete: () -> Unit) {
        killerStunActive = true
        killerStunPhase = 0f
        val animator = ValueAnimator.ofFloat(0f, 1f)
        animator.duration = stunDurationMs
        animator.interpolator = LinearInterpolator()
        animator.addUpdateListener {
            killerStunPhase = (it.animatedValue as Float) * 2f
            invalidate()
        }
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationCancel(animation: Animator) {
                if (killerStunAnimator === animator) killerStunAnimator = null
                killerStunActive = false
                killerStunPhase = 0f
                invalidate()
            }

            override fun onAnimationEnd(animation: Animator) {
                if (killerStunAnimator === animator) killerStunAnimator = null
                killerStunActive = false
                killerStunPhase = 0f
                invalidate()
                onComplete()
            }
        })
        killerStunAnimator = animator
        animator.start()
    }

    fun clearSpawnAvatars() {
        spawnAvatarBitmaps.clear()
        invalidate()
    }

    fun clearGameState() {
        spawnIntroAnimator?.cancel()
        spawnIntroAnimator = null
        spawnIntroProgress = 1f
        throwableAnimator?.cancel()
        throwableAnimator = null
        throwableAnim = null
        displayMoveAnimator?.cancel()
        displayMoveAnimator = null
        displayMoveAnim = null
        killerTrail = emptyList()
        deadNumbers.clear()
        killerRevealedNumber = -1
        resetKillerPos()
        clearSoundIndicator()
        // Don't clear spawnAvatarBitmaps — caller re-sets them after clearGameState
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
            hiderSpawns.mapIndexed { i, p -> (i + 1) to PointF(p.x, p.y) }.toMap().toMutableMap()
        else
            defaultPositionsForMap1().mapValues { (_, p) -> PointF(p.x, p.y) }.toMutableMap()
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

    private fun radius() = mapRect.width() * GameConfig.SPAWN_AVATAR_SIZE_RATIO

    private fun dp(value: Float) = value * resources.displayMetrics.density
    private fun sp(value: Float) = value * resources.displayMetrics.scaledDensity

    private fun nx(p: PointF) = mapRect.left + p.x * mapRect.width()
    private fun ny(p: PointF) = mapRect.top + p.y * mapRect.height()

    // Lấy ra một điểm chuẩn hóa tại progress bất kỳ trên một polyline path.
    private fun pointOnPath(path: List<PointF>, progress: Float): PointF {
        if (path.isEmpty()) return PointF()
        if (path.size == 1) return PointF(path[0].x, path[0].y)

        val clamped = progress.coerceIn(0f, 1f)
        if (clamped <= 0f) return PointF(path.first().x, path.first().y)
        if (clamped >= 1f) return PointF(path.last().x, path.last().y)

        var totalLength = 0f
        val segmentLengths = FloatArray(path.size - 1)
        for (i in segmentLengths.indices) {
            val dx = path[i + 1].x - path[i].x
            val dy = path[i + 1].y - path[i].y
            val length = sqrt(dx * dx + dy * dy)
            segmentLengths[i] = length
            totalLength += length
        }

        if (totalLength <= 0f) return PointF(path.last().x, path.last().y)

        var remaining = totalLength * clamped
        for (i in segmentLengths.indices) {
            val length = segmentLengths[i]
            if (remaining <= length || i == segmentLengths.lastIndex) {
                val local = if (length <= 0f) 1f else (remaining / length).coerceIn(0f, 1f)
                val from = path[i]
                val to = path[i + 1]
                return PointF(
                    from.x + (to.x - from.x) * local,
                    from.y + (to.y - from.y) * local
                )
            }
            remaining -= length
        }

        return PointF(path.last().x, path.last().y)
    }

    // Tiến trình cục bộ của player num trong animation mở màn (0=chưa rời killerSpawn, 1=đã đến hideSpawn).
    private fun spawnIntroLocalProgress(num: Int): Float {
        if (spawnIntroProgress >= 1f) return 1f
        val startFrac = introPlayerStartFrac[num] ?: 0f
        val travelFrac = (introPlayerTravelFrac[num] ?: 1f).coerceAtLeast(0.001f)
        return ((spawnIntroProgress - startFrac) / travelFrac).coerceIn(0f, 1f)
    }

    // Alpha trong suốt animation mở màn: player mờ dần từ 0 lên 1 trong 20% đầu hành trình.
    private fun spawnIntroAlpha(num: Int): Float {
        if (phase != Phase.PLAYING || spawnIntroProgress >= 1f) return 1f
        return (spawnIntroLocalProgress(num) / 0.20f).coerceIn(0f, 1f)
    }

    // Vị trí hiển thị là kết hợp của vị trí spawn hiện tại (sau khi phản kháng chạy chỗ)
    // và animation mở màn (tất cả player xuất phát từ killerSpawn, tỏa về hideSpawn).
    private fun animatedDisplayPosition(num: Int, target: PointF): PointF {
        val movedTarget = if (displayMoveAnim?.displayNum == num) {
            pointOnPath(displayMoveAnim!!.path, displayMoveAnim!!.progress)
        } else {
            target
        }

        if (phase != Phase.PLAYING || spawnIntroProgress >= 1f) return movedTarget

        val localProgress = spawnIntroLocalProgress(num)
        if (localProgress >= 1f) return movedTarget

        // Ease-out: giảm tốc khi gần đến nơi
        val eased = 1f - (1f - localProgress) * (1f - localProgress)

        val path = introEntrancePaths[num]
        return if (path != null && path.size >= 2) {
            pointOnPath(path, eased)
        } else {
            // Fallback đường thẳng nếu path chưa được tính (hiếm).
            PointF(
                killerSpawnDefault.x + (movedTarget.x - killerSpawnDefault.x) * eased,
                killerSpawnDefault.y + (movedTarget.y - killerSpawnDefault.y) * eased
            )
        }
    }

    override fun onDraw(canvas: Canvas) {
        mapBitmap?.let { canvas.drawBitmap(it, null, mapRect, null) }

        if (showDebugCollision) {
            debugCollisionBitmap?.let { canvas.drawBitmap(it, null, mapRect, debugPaint) }
        }

        if (GameConfig.SHOW_KILLER_TRAIL && killerTrail.size >= 2) {
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
        val badgeRadius = dp(NumberUiConfig.Badge.RADIUS_DP)
        textPaint.textSize = sp(NumberUiConfig.Text.SIZE_SP)
        numberStrokePaint.textSize = textPaint.textSize
        val selectedMarkerWidth  = dp(NumberUiConfig.Marker.SELECTED_WIDTH_DP)
        val selectedMarkerHeight = dp(NumberUiConfig.Marker.SELECTED_HEIGHT_DP)
        val selectedMarkerGap    = dp(NumberUiConfig.Marker.SELECTED_GAP_DP)
        val selectedMarkerOffsetX = dp(NumberUiConfig.Marker.SELECTED_OFFSET_X_DP)
        val selectedMarkerOffsetY = dp(NumberUiConfig.Marker.SELECTED_OFFSET_Y_DP)
        val deadMarkerSize    = dp(NumberUiConfig.Marker.DEAD_SIZE_DP)
        val chooseBadgeMinSize = dp(NumberUiConfig.Badge.MIN_SIZE_DP)
        val chooseBadgePadding = dp(NumberUiConfig.Badge.PADDING_DP)
        val avatarCellRadius   = dp(NumberUiConfig.Badge.AVATAR_CELL_RADIUS_DP)
        val textBounds = Rect()

        // Trong animation mở màn: vẽ theo thứ tự N→1 để player số 1 nằm trên cùng (rời đi trước).
        val isIntroActive = phase == Phase.PLAYING && spawnIntroProgress < 1f
        val drawEntries = if (isIntroActive)
            numberPositions.entries.sortedByDescending { it.key }
        else
            numberPositions.entries.sortedBy { it.key }

        drawEntries.forEach { (num, pos) ->
            val animatedPos = animatedDisplayPosition(num, pos)
            val introAlpha = if (isIntroActive) spawnIntroAlpha(num) else 1f
            val hasAlphaLayer = introAlpha < 1f
            if (hasAlphaLayer) canvas.saveLayerAlpha(0f, 0f, width.toFloat(), height.toFloat(), (introAlpha * 255).toInt())
            val cx = nx(animatedPos)
            val cy = ny(animatedPos)
            val isLatestKill = num == killerRevealedNumber
            val isDead = deadNumbers.contains(num)
            val isPlayerChoice = num == selectedNumber
            val label = num.toString()
            val avatarBitmapPeek = spawnAvatarBitmaps[num]
            val badgeSize = if (isChoosePhase || avatarBitmapPeek != null) {
                val textHeight = textPaint.fontMetrics.run { bottom - top }
                maxOf(
                    chooseBadgeMinSize,
                    textPaint.measureText(label) + chooseBadgePadding * 2f,
                    textHeight + chooseBadgePadding * 2f
                )
            } else {
                0f
            }
            val left   = cx - badgeSize / 2f
            val top    = cy - badgeSize / 2f
            val right  = cx + badgeSize / 2f
            val bottom = cy + badgeSize / 2f

            val avatarBitmap = avatarBitmapPeek

            if (isChoosePhase && takenNumbers.contains(num)) {
                if (avatarBitmap != null) {
                    chooseTakenAvatarClipPath.reset()
                    chooseTakenAvatarClipPath.addRoundRect(left, top, right, bottom, avatarCellRadius, avatarCellRadius, Path.Direction.CW)
                    canvas.save()
                    canvas.clipPath(chooseTakenAvatarClipPath)
                    canvas.drawBitmap(avatarBitmap, null, RectF(left, top, right, bottom), null)
                    canvas.restore()
                } else {
                    canvas.drawRoundRect(left, top, right, bottom, avatarCellRadius, avatarCellRadius, chooseNumberBgPaint)
                }
                canvas.drawRoundRect(left, top, right, bottom, avatarCellRadius, avatarCellRadius, chooseTakenBorderPaint)
                lockBitmap?.let {
                    val lockSize = dp(NumberUiConfig.Badge.LOCK_ICON_SIZE_DP)
                    val lLeft = right - lockSize / 2f
                    val lTop = top - lockSize / 2f
                    canvas.drawBitmap(it, null, RectF(lLeft, lTop, lLeft + lockSize, lTop + lockSize), null)
                }
            } else if (!isChoosePhase && avatarBitmap != null) {
                chooseTakenAvatarClipPath.reset()
                chooseTakenAvatarClipPath.addRoundRect(left, top, right, bottom, avatarCellRadius, avatarCellRadius, Path.Direction.CW)
                canvas.save()
                canvas.clipPath(chooseTakenAvatarClipPath)
                canvas.drawRoundRect(left, top, right, bottom, avatarCellRadius, avatarCellRadius, avatarBgPaint)
                canvas.drawBitmap(avatarBitmap, null, RectF(left, top, right, bottom), null)
                canvas.restore()
                canvas.drawRoundRect(left, top, right, bottom, avatarCellRadius, avatarCellRadius, chooseTakenBorderPaint)
                if (isDead && deadMarkerBitmap != null) {
                    val sz = badgeSize * NumberUiConfig.Badge.DEAD_OVER_AVATAR_RATIO
                    canvas.drawBitmap(deadMarkerBitmap, null, RectF(cx - sz/2, cy - sz/2, cx + sz/2, cy + sz/2), null)
                }
            } else {
                if (isChoosePhase) {
                    val bg = if (isPlayerChoice) chooseNumberSelectedBgPaint else chooseNumberBgPaint
                    if (isPlayerChoice) {
                        chooseNumberSelectedBgPaint.shader = LinearGradient(
                            cx, top, cx, bottom,
                            NumberUiConfig.Colors.CHOOSE_GRADIENT_TOP,
                            NumberUiConfig.Colors.CHOOSE_GRADIENT_BOTTOM,
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
                    isLatestKill  -> NumberUiConfig.Colors.KILL_RED
                    isDead        -> NumberUiConfig.Colors.DEAD_TEXT
                    else          -> Color.WHITE
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
                    canvas.drawBitmap(deadMarkerBitmap, null, RectF(cx - deadHalf, cy - deadHalf, cx + deadHalf, cy + deadHalf), null)
                }
            }
            if (hasAlphaLayer) canvas.restore()
        }

        if (killerStunActive) {
            drawKillerStun(canvas)
        }

        throwableAnim?.let { drawThrowable(canvas, it) }

        if (explosionPos != null) drawExplosion(canvas)

        if (flashAlpha > 0f) {
            flashOverlayPaint.alpha = (flashAlpha * 255).toInt()
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), flashOverlayPaint)
        }
    }

    private fun drawSpawnAvatar(canvas: Canvas, cx: Float, cy: Float, r: Float, bitmap: Bitmap, isDead: Boolean) {
        val bgPaintToUse = if (isDead) deadBgPaint else bgPaint
        canvas.drawCircle(cx, cy, r, bgPaintToUse)

        val innerR = r * NumberUiConfig.Avatar.INNER_RATIO
        canvas.save()
        avatarClipPath.reset()
        avatarClipPath.addCircle(cx, cy, innerR, Path.Direction.CW)
        canvas.clipPath(avatarClipPath)
        canvas.drawBitmap(bitmap, null, RectF(cx - innerR, cy - innerR, cx + innerR, cy + innerR), null)
        canvas.restore()

        val borderPaintToUse = if (isDead) avatarDeadBorderPaint else avatarBorderPaint
        canvas.drawCircle(cx, cy, r, borderPaintToUse)
    }

    private fun drawSoundIndicator(canvas: Canvas, pos: PointF) {
        val cx = nx(pos)
        val cy = ny(pos)
        val r = radius()

        for (i in 0 until 3) {
            val phase = (soundRingPhase + i / 3f) % 1f
            val ringR = r * (NumberUiConfig.Sound.RING_EXPAND_START + phase * NumberUiConfig.Sound.RING_EXPAND_RANGE)
            val alpha = ((1f - phase) * NumberUiConfig.Sound.RING_ALPHA_FACTOR).toInt()
            soundRingPaint.color = Color.argb(alpha, 0xFF, 0x99, 0x00)
            soundRingPaint.strokeWidth = r * NumberUiConfig.Sound.RING_STROKE_RATIO
            canvas.drawCircle(cx, cy, ringR, soundRingPaint)
        }

        canvas.drawCircle(cx, cy, r * NumberUiConfig.Sound.BG_RADIUS_RATIO, soundBgPaint)

        soundIconPaint.textSize = r * NumberUiConfig.Sound.ICON_SIZE_RATIO
        canvas.drawText("♪", cx, cy + r * NumberUiConfig.Sound.ICON_Y_OFFSET_RATIO, soundIconPaint)
    }

    private fun drawKiller(canvas: Canvas) {
        val bmp = killerBitmap ?: return
        val kw = mapRect.width() * GameConfig.KILLER_SIZE_RATIO
        val kh = kw * bmp.height.toFloat() / bmp.width
        val cx = nx(killerPos) + killerShakeOffset
        val cy = ny(killerPos)
        canvas.drawBitmap(bmp, null, RectF(cx - kw / 2, cy - kh / 2, cx + kw / 2, cy + kh / 2), null)
    }

    private fun drawKillerStun(canvas: Canvas) {
        val cx = nx(killerPos) + killerShakeOffset
        val cy = ny(killerPos) - mapRect.width() * 0.08f
        val orbit = mapRect.width() * 0.035f
        val dotRadius = mapRect.width() * 0.009f
        repeat(3) { index ->
            val phase = killerStunPhase + index / 3f
            val angle = phase * Math.PI * 2.0
            val x = cx + (cos(angle) * orbit).toFloat()
            val y = cy + (sin(angle) * orbit * 0.45f).toFloat()
            canvas.drawCircle(x, y, dotRadius, stunPaint)
        }
    }

    private fun drawThrowable(canvas: Canvas, anim: ThrowableAnim) {
        val current = PointF(
            anim.from.x + (anim.to.x - anim.from.x) * anim.progress,
            anim.from.y + (anim.to.y - anim.from.y) * anim.progress
        )
        val cx = nx(current)
        val cy = ny(current)
        val size = mapRect.width() * GameConfig.PLAYER_RESISTANCE_THROWABLE_SIZE_RATIO

        canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(anim.rotationDeg)
        when (anim.toolType) {
            ResistanceToolType.PAN -> {
                canvas.drawOval(RectF(-size * 0.35f, -size * 0.30f, size * 0.35f, size * 0.30f), throwablePaint)
                canvas.drawOval(RectF(-size * 0.35f, -size * 0.30f, size * 0.35f, size * 0.30f), throwableAccentPaint)
                canvas.drawLine(size * 0.25f, 0f, size * 0.70f, 0f, throwableAccentPaint)
            }

            ResistanceToolType.BOWL -> {
                val bowlRect = RectF(-size * 0.40f, -size * 0.18f, size * 0.40f, size * 0.28f)
                canvas.drawArc(bowlRect, 0f, 180f, true, throwablePaint)
                canvas.drawArc(bowlRect, 0f, 180f, false, throwableAccentPaint)
            }

            ResistanceToolType.CHOPSTICKS -> {
                canvas.drawLine(-size * 0.55f, -size * 0.12f, size * 0.55f, -size * 0.28f, throwableAccentPaint)
                canvas.drawLine(-size * 0.50f, size * 0.18f, size * 0.60f, 0f, throwableAccentPaint)
            }

            ResistanceToolType.BOTTLE -> {
                val bodyRect = RectF(-size * 0.22f, -size * 0.42f, size * 0.22f, size * 0.36f)
                canvas.drawRoundRect(bodyRect, size * 0.12f, size * 0.12f, throwablePaint)
                canvas.drawRoundRect(bodyRect, size * 0.12f, size * 0.12f, throwableAccentPaint)
                canvas.drawRect(-size * 0.10f, -size * 0.56f, size * 0.10f, -size * 0.38f, throwablePaint)
                canvas.drawRect(-size * 0.10f, -size * 0.56f, size * 0.10f, -size * 0.38f, throwableAccentPaint)
            }

            ResistanceToolType.BOMB -> {
                val bmp = bombBitmap
                if (bmp != null) {
                    val half = size * 0.45f
                    canvas.drawBitmap(bmp, null, RectF(-half, -half, half, half), null)
                } else {
                    canvas.drawCircle(0f, 0f, size * 0.32f, throwablePaint)
                    canvas.drawCircle(0f, 0f, size * 0.32f, throwableAccentPaint)
                }
            }
        }
        canvas.restore()
    }

    private fun drawExplosion(canvas: Canvas) {
        val pos = explosionPos ?: return
        val bmp = explosionBitmap ?: return
        val cx = nx(pos)
        val cy = ny(pos)
        val size = mapRect.width() * 0.5f * explosionScale
        explosionPaint.alpha = (explosionAlpha * 255).toInt().coerceIn(0, 255)
        canvas.drawBitmap(bmp, null, RectF(cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2), explosionPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (phase != Phase.CHOOSE_NUMBER || event.action != MotionEvent.ACTION_DOWN) return false
        val tapRadius = radius() * NumberUiConfig.Avatar.TAP_RADIUS_RATIO
        numberPositions.forEach { (num, pos) ->
            val dx = event.x - nx(pos)
            val dy = event.y - ny(pos)
            if (dx * dx + dy * dy <= tapRadius * tapRadius) {
                if (takenNumbers.contains(num)) return true
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
