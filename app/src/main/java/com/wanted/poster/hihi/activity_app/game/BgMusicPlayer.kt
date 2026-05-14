package com.wanted.poster.hihi.activity_app.game

import android.content.Context
import android.media.MediaPlayer

object BgMusicPlayer {

    private const val BG_FOLDER = "sound_killer/bg_music"

    private var player: MediaPlayer? = null

    fun start(context: Context) {
        release()
        val raw = context.assets.list(BG_FOLDER)
        android.util.Log.d("BgMusic", "files: ${raw?.toList()}")
        val files = raw?.filter { it.endsWith(".mp3") }
            ?.takeIf { it.isNotEmpty() } ?: return

        val file = "$BG_FOLDER/${SoundConfig.BG_MUSIC ?: files.random()}"
        try {
            val afd = context.assets.openFd(file)
            player = MediaPlayer().apply {
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            android.util.Log.w("BgMusicPlayer", "Cannot play: $file")
        }
    }

    fun pause() {
        player?.takeIf { it.isPlaying }?.pause()
    }

    fun resume() {
        try { player?.takeIf { !it.isPlaying }?.start() } catch (_: Exception) {}
    }

    fun release() {
        player?.release()
        player = null
    }
}
