package com.wanted.poster.hihi.activity_app.game

import android.content.Context
import android.media.MediaPlayer

object RoomSoundPlayer {

    private var player: MediaPlayer? = null

    fun play(context: Context, roomType: RoomType) {
        val files = context.assets.list(roomType.folder)
            ?.filter { it.endsWith(".mp3") }
            ?.takeIf { it.isNotEmpty() } ?: return
        val configured = when (roomType) {
            RoomType.BEDROOM  -> SoundConfig.ROOM_BEDROOM
            RoomType.BATHROOM -> SoundConfig.ROOM_BATHROOM
            RoomType.TOILET   -> SoundConfig.ROOM_TOILET
            RoomType.KITCHEN  -> SoundConfig.ROOM_KITCHEN
            RoomType.LIVING   -> SoundConfig.ROOM_LIVING
        }
        val file = configured?.let { "${roomType.folder}/$it" } ?: "${roomType.folder}/${files.random()}"
        release()
        try {
            val afd = context.assets.openFd(file)
            player = MediaPlayer().apply {
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            android.util.Log.w("RoomSoundPlayer", "Sound not found: $file")
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
        try {
            player?.takeIf { !it.isPlaying }?.start()
        } catch (_: Exception) {
        }
    }
}
