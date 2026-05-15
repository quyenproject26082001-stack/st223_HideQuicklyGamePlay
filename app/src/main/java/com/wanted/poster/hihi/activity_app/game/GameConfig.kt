package com.wanted.poster.hihi.activity_app.game

import android.graphics.PointF
import kotlin.math.roundToLong
import kotlin.math.sqrt

object GameConfig {

    const val SWEEP_KILL_CHANCE = 0.80f

    const val KILLER_SPEED_MULTIPLIER = 1.0f

    private const val BASE_KILLER_PATH_SPEED_PER_SECOND = 0.16f
    private const val BASE_DEBUG_KILLER_STEP_PER_TICK = 0.004f
    private const val MIN_KILLER_ANIMATION_DURATION_MS = 900L

    fun debugKillerStep(): Float =
        BASE_DEBUG_KILLER_STEP_PER_TICK * KILLER_SPEED_MULTIPLIER

    fun killerAnimationDurationMs(path: List<PointF>): Long {
        if (path.size < 2) return MIN_KILLER_ANIMATION_DURATION_MS

        var totalDistance = 0f
        for (i in 0 until path.lastIndex) {
            val from = path[i]
            val to = path[i + 1]
            val dx = to.x - from.x
            val dy = to.y - from.y
            totalDistance += sqrt(dx * dx + dy * dy)
        }

        if (totalDistance <= 0f) return MIN_KILLER_ANIMATION_DURATION_MS

        val speedPerSecond =
            BASE_KILLER_PATH_SPEED_PER_SECOND * KILLER_SPEED_MULTIPLIER

        return ((totalDistance / speedPerSecond) * 1000f)
            .roundToLong()
            .coerceAtLeast(MIN_KILLER_ANIMATION_DURATION_MS)
    }
}
