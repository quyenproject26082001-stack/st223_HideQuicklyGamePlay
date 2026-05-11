package com.wanted.poster.maker.activity_app.game

import android.content.Context
import android.media.MediaPlayer

object RoomSoundPlayer {

    private var player: MediaPlayer? = null

    fun play(context: Context, roomType: RoomType) {
        release()
        try {
            val afd = context.assets.openFd(roomType.assetFile)
            player = MediaPlayer().apply {
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                prepare()
                start()
            }
        } catch (e: Exception) {
            android.util.Log.w("RoomSoundPlayer", "Sound not found: ${roomType.assetFile}")
        }
    }

    fun release() {
        player?.release()
        player = null
    }
}
