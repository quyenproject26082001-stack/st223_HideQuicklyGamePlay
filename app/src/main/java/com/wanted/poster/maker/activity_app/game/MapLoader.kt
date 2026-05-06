package com.wanted.poster.maker.activity_app.game

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PointF
import androidx.appcompat.content.res.AppCompatResources
import org.xmlpull.v1.XmlPullParser

object MapLoader {

    private const val VIEWPORT_W = 1997f
    private const val VIEWPORT_H = 3258f
    private const val BITMAP_W   = 400
    private const val BITMAP_H   = 653

    private val COLOR_KILLER  = Color.parseColor("#FF00FF00")
    private val COLOR_HIDER   = Color.parseColor("#FF0080FF")
    private val COLOR_BEDROOM = Color.parseColor("#FFFFFF00")
    private val COLOR_BATH    = Color.parseColor("#FF00FFFF")
    private val COLOR_TOILET  = Color.parseColor("#FFFF00FF")
    private val COLOR_KITCHEN = Color.parseColor("#FFFF8000")
    private val COLOR_LIVING  = Color.parseColor("#FFFF69B4")

    private val dotPattern = Regex("""M\s*([\d.]+)\s+([\d.]+)""")

    fun load(context: Context, mapIndex: Int): MapData {
        val resId = context.resources.getIdentifier(
            "map$mapIndex", "drawable", context.packageName
        )
        if (resId == 0) return fallback(context, mapIndex)

        val killerSpawns = mutableListOf<PointF>()
        val hiderSpawns  = mutableListOf<PointF>()
        val rooms        = mutableListOf<RoomInfo>()

        parseXmlDots(context, resId, killerSpawns, hiderSpawns, rooms)

        val bitmap = renderToBitmap(context, resId)

        android.util.Log.d("MapLoader", "map$mapIndex → killer=${killerSpawns.size} hider=${hiderSpawns.size} rooms=${rooms.size}")

        return MapData(
            collisionBitmap = bitmap,
            killerSpawn     = killerSpawns.firstOrNull() ?: PointF(0.5f, 0.38f),
            hiderSpawns     = hiderSpawns,
            rooms           = rooms
        )
    }

    private fun parseXmlDots(
        context: Context,
        resId: Int,
        killerSpawns: MutableList<PointF>,
        hiderSpawns: MutableList<PointF>,
        rooms: MutableList<RoomInfo>
    ) {
        val parser = context.resources.getXml(resId)
        try {
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && parser.name == "path") {
                    var colorInt  = 0
                    var pathData  = ""
                    var isRound   = false

                    // Đọc attributes theo index để tránh vấn đề namespace binary XML
                    for (i in 0 until parser.attributeCount) {
                        val name  = parser.getAttributeName(i)
                        val value = parser.getAttributeValue(i)
                        when {
                            name.endsWith("strokeColor")  -> colorInt  = parseColor(value)
                            name.endsWith("pathData")     -> pathData  = value
                            name.endsWith("strokeLineCap") -> isRound  = (value == "round" || value == "1")
                        }
                    }

                    // Chỉ xử lý chấm tròn (dot marker)
                    if (isRound && pathData.isNotEmpty()) {
                        val match = dotPattern.find(pathData) ?: run { event = parser.next(); continue }
                        val nx = match.groupValues[1].toFloat() / VIEWPORT_W
                        val ny = match.groupValues[2].toFloat() / VIEWPORT_H
                        val pos = PointF(nx, ny)

                        when (colorInt) {
                            COLOR_KILLER  -> killerSpawns.add(pos)
                            COLOR_HIDER   -> hiderSpawns.add(pos)
                            COLOR_BEDROOM -> rooms.add(RoomInfo(pos, RoomType.BEDROOM))
                            COLOR_BATH    -> rooms.add(RoomInfo(pos, RoomType.BATHROOM))
                            COLOR_TOILET  -> rooms.add(RoomInfo(pos, RoomType.TOILET))
                            COLOR_KITCHEN -> rooms.add(RoomInfo(pos, RoomType.KITCHEN))
                            COLOR_LIVING  -> rooms.add(RoomInfo(pos, RoomType.LIVING))
                        }
                    }
                }
                event = parser.next()
            }
        } catch (_: Exception) {
        } finally {
            parser.close()
        }
    }

    // Xử lý cả "#AARRGGBB" lẫn integer string từ binary XML
    private fun parseColor(value: String): Int {
        return when {
            value.startsWith("#") -> try { Color.parseColor(value) } catch (_: Exception) { 0 }
            else -> value.toIntOrNull() ?: 0
        }
    }

    private fun renderToBitmap(context: Context, resId: Int): Bitmap {
        return try {
            val drawable = AppCompatResources.getDrawable(context, resId)
                ?: return Bitmap.createBitmap(BITMAP_W, BITMAP_H, Bitmap.Config.ARGB_8888)
            val bmp = Bitmap.createBitmap(BITMAP_W, BITMAP_H, Bitmap.Config.ARGB_8888)
            drawable.setBounds(0, 0, BITMAP_W, BITMAP_H)
            drawable.draw(Canvas(bmp))
            bmp
        } catch (_: Exception) {
            Bitmap.createBitmap(BITMAP_W, BITMAP_H, Bitmap.Config.ARGB_8888)
        }
    }

    // Fallback nếu không có XML: trả về MapData rỗng có thể chơi được
    private fun fallback(context: Context, mapIndex: Int): MapData {
        val empty = Bitmap.createBitmap(BITMAP_W, BITMAP_H, Bitmap.Config.ARGB_8888)
        return MapData(
            collisionBitmap = empty,
            killerSpawn     = PointF(0.46f, 0.38f),
            hiderSpawns     = MapNumberView.defaultPositionsForMap1().values.toList(),
            rooms           = emptyList()
        )
    }
}
