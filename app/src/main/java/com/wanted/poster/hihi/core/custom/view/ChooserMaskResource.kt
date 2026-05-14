package com.wanted.poster.hihi.core.custom.view

import android.content.Context
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Path
import androidx.core.graphics.PathParser
import com.wanted.poster.hihi.R
import org.xmlpull.v1.XmlPullParser

internal object ChooserMaskResource {

    private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    private const val COLOR_GRADIENT = 0xFF00FF00.toInt()
    private const val COLOR_IMAGE = 0xFFFF0000.toInt()

    private data class PathSpec(val pathData: String, val strokeColor: Int?)

    private data class ChooserMaskSpec(
        val viewportWidth: Float,
        val viewportHeight: Float,
        val gradientSpec: PathSpec,
        val imageSpec: PathSpec
    )

    @Volatile
    private var cachedSpec: ChooserMaskSpec? = null

    fun createGradientPath(context: Context, width: Int, height: Int): Path =
        createScaledPath(getSpec(context).gradientSpec.pathData, getSpec(context).viewportWidth, getSpec(context).viewportHeight, width, height)

    fun createImagePath(context: Context, width: Int, height: Int): Path =
        createScaledPath(getSpec(context).imageSpec.pathData, getSpec(context).viewportWidth, getSpec(context).viewportHeight, width, height)

    private fun createScaledPath(pathData: String, vpW: Float, vpH: Float, width: Int, height: Int): Path {
        val source = PathParser.createPathFromPathData(pathData) ?: Path()
        val scaled = Path()
        Matrix().apply { setScale(width / vpW, height / vpH) }.let { source.transform(it, scaled) }
        return scaled
    }

    private fun getSpec(context: Context): ChooserMaskSpec {
        cachedSpec?.let { return it }
        return synchronized(this) {
            cachedSpec ?: parseSpec(context).also { cachedSpec = it }
        }
    }

    private fun parseSpec(context: Context): ChooserMaskSpec {
        val parser = context.resources.getXml(R.drawable.item_choose_multi_mask)
        val paths = mutableListOf<PathSpec>()
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
                            paths += PathSpec(
                                pathData = pathData,
                                strokeColor = parser.getAttributeValue(ANDROID_NS, "strokeColor")?.toColorIntOrNull()
                            )
                        }
                    }
                }
            }
            parser.next()
        }

        require(viewportWidth > 0f && viewportHeight > 0f)
        require(paths.size >= 2)

        val gradient = paths.firstOrNull { it.strokeColor == COLOR_GRADIENT } ?: paths[0]
        val image = paths.firstOrNull { it.strokeColor == COLOR_IMAGE } ?: paths[1]

        return ChooserMaskSpec(viewportWidth, viewportHeight, gradient, image)
    }

    private fun String.toColorIntOrNull(): Int? =
        try { Color.parseColor(this) } catch (_: IllegalArgumentException) { null }
}
