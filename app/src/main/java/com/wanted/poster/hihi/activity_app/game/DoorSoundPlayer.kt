package com.wanted.poster.hihi.activity_app.game

import android.content.Context
import android.media.MediaPlayer

object DoorSoundPlayer {

    private const val DOOR_FOLDER = "sound_killer/door_sound"
    private const val DOOR_RADIUS = 0.04f  // ngưỡng khoảng cách normalized để trigger âm thanh cửa

    private var player: MediaPlayer? = null
    private var doorFiles: List<String>? = null

    fun preload(context: Context) {
        doorFiles = context.assets.list(DOOR_FOLDER)
            ?.filter { it.endsWith(".mp3") }
            ?.map { "$DOOR_FOLDER/$it" }
            ?.takeIf { it.isNotEmpty() }
    }

    fun playIfNearDoor(context: Context, killerX: Float, killerY: Float, doors: List<android.graphics.PointF>) {
        val near = doors.any { d ->
            val dx = d.x - killerX
            val dy = d.y - killerY
            dx * dx + dy * dy <= DOOR_RADIUS * DOOR_RADIUS
        }
        if (!near) return
        playRandom(context)
    }

    fun playRandom(context: Context) {
        val files = doorFiles ?: return
        val file = SoundConfig.DOOR?.let { "$DOOR_FOLDER/$it" } ?: files.random()
        release()
        try {
            val afd = context.assets.openFd(file)
            player = MediaPlayer().apply {
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                prepare()
                start()
            }
        } catch (e: Exception) {
            android.util.Log.w("DoorSoundPlayer", "Cannot play: $file")
        }
    }

    fun release() {
        player?.release()
        player = null
    }

    fun pause() {
        player?.takeIf { it.isPlaying }?.pause()
    }

    fun resume() {
        try { player?.takeIf { !it.isPlaying }?.start() } catch (_: Exception) {}
    }
}
