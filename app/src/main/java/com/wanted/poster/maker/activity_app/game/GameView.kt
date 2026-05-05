package com.wanted.poster.maker.activity_app.game

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import kotlin.math.sqrt

class GameView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : SurfaceView(context, attrs), SurfaceHolder.Callback {

    private var gameThread: GameThread? = null

    private lateinit var mapBitmap: Bitmap
    private lateinit var collisionBitmap: Bitmap
    private var debugBitmap: Bitmap? = null
    // Killer = người chơi điều khiển, hiders = người trốn
    // Vị trí normalized khớp với spawn points (chấm xanh) trong 1_collision.png
    val killer = Player(x = 0.30f, y = 0.27f, isKiller = true, name = "Killer")
    val hiders = listOf(
        Player(x = 0.14f, y = 0.46f, name = "Player 1"),  // phòng 1 trái
        Player(x = 0.14f, y = 0.66f, name = "Player 2"),  // phòng 2 trái
        Player(x = 0.74f, y = 0.61f, name = "Player 3")   // nhà tắm phải
    )

    // Điểm đích khi tap
    private var targetX = killer.x
    private var targetY = killer.y

    var showDebug = false

    // --- Paints ---
    private val mapPaint = Paint(Paint.FILTER_BITMAP_FLAG)

    private val killerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.RED
    }
    private val hiderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2196F3")
    }
    private val killerBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val hiderBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 30f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        setShadowLayer(4f, 0f, 2f, Color.BLACK)
    }
    private val pathPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(180, 255, 220, 0)
        style = Paint.Style.STROKE
        strokeWidth = 5f
        pathEffect = DashPathEffect(floatArrayOf(18f, 10f), 0f)
    }
    private val targetPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(150, 255, 220, 0)
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val debugPaint = Paint().apply { alpha = 140 }
    private val doorLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF5722")
        textSize = 22f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    init {
        holder.addCallback(this)
        isFocusable = true
    }

    override fun surfaceCreated(h: SurfaceHolder) {
        loadAssets()
        gameThread = GameThread(h, this).apply { start() }
    }

    override fun surfaceChanged(h: SurfaceHolder, fmt: Int, w: Int, ht: Int) {
        debugBitmap = null // reset khi màn hình thay đổi
    }

    override fun surfaceDestroyed(h: SurfaceHolder) {
        gameThread?.apply { running = false; join() }
        gameThread = null
    }

    private fun loadAssets() {
        mapBitmap = context.assets.open("Map/1.jpg").use { BitmapFactory.decodeStream(it) }
        collisionBitmap = loadCollisionBitmap()
    }

    // Load collision PNG từ assets (Đen=tường, Trắng=đi được, Đỏ=cửa, Xanh=spawn)
    private fun loadCollisionBitmap(): Bitmap {
        return try {
            context.assets.open("Map/1_collision.png").use {
                BitmapFactory.decodeStream(it).also { bmp ->
                    // Đảm bảo format ARGB_8888 để getPixel() hoạt động đúng
                    if (bmp.config != Bitmap.Config.ARGB_8888) {
                        return bmp.copy(Bitmap.Config.ARGB_8888, false)
                    }
                }
            }
        } catch (e: Exception) {
            // Fallback: tạo bitmap trống nếu file không tồn tại
            Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        }
    }

    // Kiểm tra 1 pixel (normalized) có phải tường không
    private fun isWalkable(nx: Float, ny: Float): Boolean {
        if (!::collisionBitmap.isInitialized) return true
        val bx = (nx * collisionBitmap.width).toInt().coerceIn(0, collisionBitmap.width - 1)
        val by = (ny * collisionBitmap.height).toInt().coerceIn(0, collisionBitmap.height - 1)
        return collisionBitmap.getPixel(bx, by) != Color.BLACK
    }

    // Kiểm tra toàn bộ vùng nhân vật (trung tâm + 4 điểm rìa theo bán kính)
    fun canMoveTo(nx: Float, ny: Float): Boolean {
        val r = if (width > 0) 26f / width else 0.025f
        return isWalkable(nx, ny) &&
               isWalkable(nx - r, ny) &&
               isWalkable(nx + r, ny) &&
               isWalkable(nx, ny - r) &&
               isWalkable(nx, ny + r)
    }

    fun isDoor(nx: Float, ny: Float): Boolean {
        if (!::collisionBitmap.isInitialized) return false
        val bx = (nx * collisionBitmap.width).toInt().coerceIn(0, collisionBitmap.width - 1)
        val by = (ny * collisionBitmap.height).toInt().coerceIn(0, collisionBitmap.height - 1)
        return collisionBitmap.getPixel(bx, by) == Color.RED
    }

    fun distanceTo(a: Player, b: Player): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return sqrt(dx * dx + dy * dy)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                targetX = event.x / width
                targetY = event.y / height
            }
        }
        return true
    }

    fun update() {
        val speed = 0.004f
        val dx = targetX - killer.x
        val dy = targetY - killer.y
        val dist = sqrt(dx * dx + dy * dy)

        if (dist > 0.008f) {
            val nx = killer.x + (dx / dist) * speed
            val ny = killer.y + (dy / dist) * speed
            if (canMoveTo(nx, ny)) {
                killer.x = nx
                killer.y = ny
            } else {
                // Thử trượt theo trục X
                if (canMoveTo(nx, killer.y)) killer.x = nx
                // Thử trượt theo trục Y
                else if (canMoveTo(killer.x, ny)) killer.y = ny
            }
        }
    }

    fun render(canvas: Canvas) {
        if (!::mapBitmap.isInitialized) return

        // 1. Vẽ map nền
        canvas.drawBitmap(mapBitmap, null, RectF(0f, 0f, width.toFloat(), height.toFloat()), mapPaint)

        // 2. Debug: hiện collision overlay
        if (showDebug) drawCollisionOverlay(canvas)

        // 3. Đường đi (dashed line từ killer đến target)
        val kx = killer.x * width
        val ky = killer.y * height
        val tx = targetX * width
        val ty = targetY * height
        canvas.drawLine(kx, ky, tx, ty, pathPaint)
        canvas.drawCircle(tx, ty, 10f, targetPaint)

        // 4. Hiders
        hiders.forEach { p ->
            val px = p.x * width
            val py = p.y * height
            canvas.drawCircle(px, py, 22f, hiderPaint)
            canvas.drawCircle(px, py, 22f, hiderBorderPaint)
            canvas.drawText(p.name, px, py - 30f, namePaint)

            // Hiển thị khoảng cách đến killer
            val d = (distanceTo(killer, p) * 1000).toInt()
            canvas.drawText("${d}m", px, py + 38f, doorLabelPaint)
        }

        // 5. Killer
        canvas.drawCircle(kx, ky, 26f, killerPaint)
        canvas.drawCircle(kx, ky, 26f, killerBorderPaint)
        canvas.drawText("☠ Killer", kx, ky - 34f, namePaint)
    }

    private fun drawCollisionOverlay(canvas: Canvas) {
        if (debugBitmap == null) {
            debugBitmap = Bitmap.createScaledBitmap(collisionBitmap, width, height, false)
        }
        canvas.drawBitmap(debugBitmap!!, 0f, 0f, debugPaint)
    }

    fun toggleDebug() {
        showDebug = !showDebug
    }
}
