package com.wanted.poster.hihi.activity_app.game

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory

object AvatarLoader {
    fun load(context: Context, player: PlayerSetupModel): Bitmap? {
        val path = player.avatarPath ?: return null
        if (path.startsWith("flag:")) return null
        return try {
            context.assets.open(path).use { BitmapFactory.decodeStream(it) }
        } catch (_: Exception) { null }
    }
}
