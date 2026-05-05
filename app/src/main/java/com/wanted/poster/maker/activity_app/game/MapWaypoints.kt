package com.wanted.poster.maker.activity_app.game

import android.graphics.PointF

object MapWaypoints {

    // Killer spawn for Map 1
    private val SPAWN = PointF(0.46f, 0.38f)

    // Hand-defined paths for Map 1 (waypoints approach)
    // Intermediate points simulate going through corridors/doors
    // Based on Map 1 layout: center hallway at ~(0.46, 0.38)
    //   Left corridor: x~0.20, Right corridor: x~0.78
    //   Top corridor: y~0.18, Bottom corridor: y~0.72
    fun getPath(targetRoom: Int): List<PointF> {
        val target = MapNumberView.defaultPositionsForMap1()[targetRoom] ?: return listOf(SPAWN)
        val via = corridorFor(targetRoom)
        return if (via != null) listOf(SPAWN, via, target) else listOf(SPAWN, target)
    }

    private fun corridorFor(room: Int): PointF? = when (room) {
        1  -> PointF(0.20f, 0.60f)  // left side → bottom-left garage
        2  -> PointF(0.46f, 0.70f)  // down center → bottom bedroom
        3  -> PointF(0.60f, 0.70f)  // right-center → bottom-right bedroom
        4  -> PointF(0.78f, 0.60f)  // right corridor → bathroom right
        5  -> null                   // center — direct
        6  -> PointF(0.78f, 0.42f)  // right corridor → right bedroom
        7  -> PointF(0.44f, 0.22f)  // up center → top bedroom
        8  -> PointF(0.78f, 0.22f)  // right → top-right bedroom
        9  -> PointF(0.20f, 0.42f)  // left corridor → left bedroom
        10 -> PointF(0.46f, 0.58f)  // down slightly → center-lower
        11 -> PointF(0.78f, 0.55f)  // right corridor → right room
        12 -> PointF(0.20f, 0.25f)  // left → top-left corner
        else -> null
    }
}
