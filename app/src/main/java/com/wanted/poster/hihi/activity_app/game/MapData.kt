package com.wanted.poster.hihi.activity_app.game

import android.graphics.Bitmap
import android.graphics.PointF

data class MapData(
    val collisionBitmap: Bitmap,
    val killerSpawn: PointF,
    val hiderSpawns: List<PointF>,
    val rooms: List<RoomInfo>,
    val doorLines: List<Pair<PointF, PointF>> = emptyList()
)

data class RoomInfo(
    val position: PointF,
    val type: RoomType,
    val radius: Float
)

enum class RoomType(val folder: String) {
    BEDROOM ("sound_killer/room_bedroom"),
    BATHROOM("sound_killer/room_bathroom"),
    TOILET  ("sound_killer/room_toilet"),
    KITCHEN ("sound_killer/room_kitchen"),
    LIVING  ("sound_killer/room_living")
}
