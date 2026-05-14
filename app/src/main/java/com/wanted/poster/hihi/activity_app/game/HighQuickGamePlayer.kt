package com.wanted.poster.hihi.activity_app.game

import android.content.Context
import android.media.MediaPlayer

object HighQuickGamePlayer {

    private const val FOLDER = "sound_killer/high quick game"

    private var player: MediaPlayer? = null

    fun start(context: Context) {
        release()
        val files = context.assets.list(FOLDER)
            ?.filter { it.endsWith(".mp3", ignoreCase = true) }
            ?.takeIf { it.isNotEmpty() }
            ?: return

        val file = "$FOLDER/${files.random()}"
        try {
            val afd = context.assets.openFd(file)
            player = MediaPlayer().apply {
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                isLooping = false
                setOnCompletionListener {
                    it.release()
                    if (player === it) player = null
                }
                prepare()
                start()
            }
        } catch (_: Exception) {
            release()
        }
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

    fun release() {
        player?.release()
        player = null
    }
}
