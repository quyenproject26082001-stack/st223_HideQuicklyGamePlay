package com.wanted.poster.hihi.activity_app.game

import android.graphics.*
import java.util.PriorityQueue
import kotlin.math.sqrt

class KillerPathfinder(collision: Bitmap) {

    private val bmp: Bitmap = collision
    private val W = bmp.width
    private val H = bmp.height

    private data class Node(
        val x: Int, val y: Int,
        val g: Float, val h: Float,
        val parent: Node?
    ) : Comparable<Node> {
        val f = g + h
        override fun compareTo(other: Node) = f.compareTo(other.f)
    }

    fun findPath(sx: Float, sy: Float, ex: Float, ey: Float): List<PointF> {
        val x0 = (sx * W).toInt().coerceIn(0, W - 1)
        val y0 = (sy * H).toInt().coerceIn(0, H - 1)
        val x1 = (ex * W).toInt().coerceIn(0, W - 1)
        val y1 = (ey * H).toInt().coerceIn(0, H - 1)

        val open = PriorityQueue<Node>()
        val visited = HashSet<Int>()
        val gMap = HashMap<Int, Float>()

        fun key(x: Int, y: Int) = x * H + y

        open += Node(x0, y0, 0f, h(x0, y0, x1, y1), null)
        gMap[key(x0, y0)] = 0f

        val dirs = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1, -1 to -1, 1 to -1, -1 to 1, 1 to 1)

        while (open.isNotEmpty()) {
            val cur = open.poll()!!
            val ck = key(cur.x, cur.y)
            if (ck in visited) continue
            visited += ck

            if (cur.x == x1 && cur.y == y1) return buildPath(cur)

            for ((dx, dy) in dirs) {
                val nx = cur.x + dx; val ny = cur.y + dy
                if (nx < 0 || nx >= W || ny < 0 || ny >= H) continue
                val nk = key(nx, ny)
                if (nk in visited || !walkable(nx, ny)) continue
                val cost = if (dx != 0 && dy != 0) 1.414f else 1f
                val ng = cur.g + cost
                if (ng < (gMap[nk] ?: Float.MAX_VALUE)) {
                    gMap[nk] = ng
                    open += Node(nx, ny, ng, h(nx, ny, x1, y1), cur)
                }
            }
        }
        // No path: fallback to straight line
        return listOf(PointF(sx, sy), PointF(ex, ey))
    }

    private fun walkable(x: Int, y: Int): Boolean {
        val px = bmp.getPixel(x, y)
        if (Color.alpha(px) < 80) return true        // transparent → walkable
        val r = Color.red(px); val g = Color.green(px); val b = Color.blue(px)
        return r > 80 || g > 80 || b > 80            // bright-colored pixel → walkable
    }

    private fun h(x: Int, y: Int, ex: Int, ey: Int): Float {
        val dx = (x - ex).toFloat(); val dy = (y - ey).toFloat()
        return sqrt(dx * dx + dy * dy)
    }

    private fun buildPath(end: Node): List<PointF> {
        val raw = ArrayDeque<PointF>()
        var n: Node? = end
        while (n != null) {
            raw.addFirst(PointF(n.x.toFloat() / W, n.y.toFloat() / H))
            n = n.parent
        }
        // Thin to ~30 waypoints for smooth animation
        val step = maxOf(1, raw.size / 30)
        val out = mutableListOf(raw.first())
        var i = step
        while (i < raw.size - 1) { out += raw[i]; i += step }
        out += raw.last()
        return out
    }
}
