package com.wanted.poster.hihi.activity_app.game

import android.content.Context
import android.media.MediaPlayer

object SfxPlayer {

    private const val FOLDER_KILL   = "sound_killer/kill_sound"
    private const val FOLDER_SCREAM = "sound_killer/scream_sound"

    private var killPlayer:   MediaPlayer? = null
    private var screamPlayer: MediaPlayer? = null

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    fun playKill(context: Context) {
        killPlayer?.release(); killPlayer = null
        screamPlayer?.release(); screamPlayer = null
        val file = resolveFile(context, FOLDER_KILL, SoundConfig.KILL) ?: return
        try {
            val afd = context.assets.openFd(file)
            killPlayer = MediaPlayer().apply {
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                setOnCompletionListener {
                    // post lên main thread sau khi kill player xong hẳn
                    handler.post { playScream(context) }
                }
                prepare()
                start()
            }
        } catch (e: Exception) {
            android.util.Log.w("SfxPlayer", "Cannot play kill: $file")
        }
    }

    fun playScream(context: Context) {
        screamPlayer?.release(); screamPlayer = null
        val file = resolveFile(context, FOLDER_SCREAM, SoundConfig.SCREAM) ?: return
        try {
            val afd = context.assets.openFd(file)
            screamPlayer = MediaPlayer().apply {
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                prepare()
                start()
            }
        } catch (e: Exception) {
            android.util.Log.w("SfxPlayer", "Cannot play scream: $file")
        }
    }

    private fun resolveFile(context: Context, folder: String, configured: String?): String? {
        if (configured != null) return "$folder/$configured"
        val files = context.assets.list(folder)
            ?.filter { it.endsWith(".mp3") }
            ?.takeIf { it.isNotEmpty() } ?: return null
        return "$folder/${files.random()}"
    }

    fun release() {
        handler.removeCallbacksAndMessages(null)
        killPlayer?.release(); killPlayer = null
        screamPlayer?.release(); screamPlayer = null
    }
}
