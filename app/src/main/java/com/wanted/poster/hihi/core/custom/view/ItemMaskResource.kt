package com.wanted.poster.hihi.core.custom.view

import android.content.Context
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Path
import androidx.core.graphics.PathParser
import com.wanted.poster.hihi.R
import org.xmlpull.v1.XmlPullParser

internal object ItemMaskResource {

    private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    private const val COLOR_OVERLAY = 0xFF000000.toInt()
    private const val COLOR_MAP = 0xFFFF0000.toInt()

    private data class MaskPathSpec(
        val pathData: String,
        val strokeColor: Int?
    )

    private data class ItemMaskSpec(
        val viewportWidth: Float,
        val viewportHeight: Float,
        val outer: MaskPathSpec,
        val overlay: MaskPathSpec,
        val map: MaskPathSpec
    )

    private enum class MaskKind { OUTER, OVERLAY, MAP }

    @Volatile
    private var cachedSpec: ItemMaskSpec? = null

    fun createOuterPath(context: Context, width: Int, height: Int): Path =
        createScaledPath(context, MaskKind.OUTER, width, height)

    fun createOverlayPath(context: Context, width: Int, height: Int): Path =
        createScaledPath(context, MaskKind.OVERLAY, width, height)

    fun createMapPath(context: Context, width: Int, height: Int): Path =
        createScaledPath(context, MaskKind.MAP, width, height)

    private fun createScaledPath(context: Context, kind: MaskKind, width: Int, height: Int): Path {
        val spec = getSpec(context)
        val pathData = when (kind) {
            MaskKind.OUTER -> spec.outer.pathData
            MaskKind.OVERLAY -> spec.overlay.pathData
            MaskKind.MAP -> spec.map.pathData
        }
        val source = PathParser.createPathFromPathData(pathData) ?: Path()
        val scaled = Path()
        val matrix = Matrix().apply {
            setScale(width / spec.viewportWidth, height / spec.viewportHeight)
        }
        source.transform(matrix, scaled)
        return scaled
    }

    private fun getSpec(context: Context): ItemMaskSpec {
        cachedSpec?.let { return it }
        return synchronized(this) {
            cachedSpec ?: parseSpec(context).also { cachedSpec = it }
        }
    }

    private fun parseSpec(context: Context): ItemMaskSpec {
        val parser = context.resources.getXml(R.drawable.item_mask)
        val paths = mutableListOf<MaskPathSpec>()
        var viewportWidth = 0f
        var viewportHeight = 0f

        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "vector" -> {
                        viewportWidth = parser.getAttributeValue(ANDROID_NS, "viewportWidth")?.toFloatOrNull() ?: 0f
                        viewportHeight = parser.getAttributeValue(ANDROID_NS, "viewportHeight")?.toFloatOrNull() ?: 0f
                    }
                    "path" -> {
                        val pathData = parser.getAttributeValue(ANDROID_NS, "pathData")
                        if (!pathData.isNullOrBlank()) {
                            paths += MaskPathSpec(
                                pathData = pathData,
                                strokeColor = parser.getAttributeValue(ANDROID_NS, "strokeColor")?.toColorIntOrNull()
                            )
                        }
                    }
                }
            }
            parser.next()
        }

        require(viewportWidth > 0f && viewportHeight > 0f) { "item_mask.xml must define viewportWidth/viewportHeight" }
        require(paths.size >= 3) { "item_mask.xml must contain at least 3 paths" }

        val overlay = paths.firstOrNull { it.strokeColor == COLOR_OVERLAY } ?: paths.getOrElse(1) { paths.first() }
        val map = paths.firstOrNull { it.strokeColor == COLOR_MAP } ?: paths.last()
        val outer = paths.firstOrNull { it !== overlay && it !== map } ?: paths.first()

        return ItemMaskSpec(
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight,
            outer = outer,
            overlay = overlay,
            map = map
        )
    }

    private fun String.toColorIntOrNull(): Int? =
        try {
            Color.parseColor(this)
        } catch (_: IllegalArgumentException) {
            null
        }
}
