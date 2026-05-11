package com.wanted.poster.maker.activity_app.game

import android.graphics.Bitmap
import android.graphics.PointF

data class MapData(
    val collisionBitmap: Bitmap,
    val killerSpawn: PointF,
    val hiderSpawns: List<PointF>,
    val rooms: List<RoomInfo>
)

data class RoomInfo(val position: PointF, val type: RoomType)

enum class RoomType(val assetFile: String) {
    BEDROOM ("sounds/bedroom.mp3"),
    BATHROOM("sounds/bathroom.mp3"),
    TOILET  ("sounds/toilet.mp3"),
    KITCHEN ("sounds/kitchen.mp3"),
    LIVING  ("sounds/living.mp3")
}
