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

enum class RoomType(val soundFolder: String) {
    BEDROOM("âm thanh phòng ngủ"),
    BATHROOM("âm thanh phòng tắm"),
    TOILET("âm thanh nhà vệ sinh"),
    KITCHEN("âm thanh phòng bếp"),
    LIVING("âm thanh phòng khách")
}
