package com.wanted.poster.hihi.activity_app.game

import android.content.Context

object GameAudio {

    fun startGame(context: Context) {
        DoorSoundPlayer.preload(context)
        HighQuickGamePlayer.start(context)
        BgMusicPlayer.start(context)
    }

    fun playKill(context: Context) = SfxPlayer.playKill(context)

    fun playScream(context: Context) = SfxPlayer.playScream(context)

    fun playRoom(context: Context, roomType: RoomType) = RoomSoundPlayer.play(context, roomType)

    fun stopRoom() = RoomSoundPlayer.release()

    fun playDoor(context: Context) = DoorSoundPlayer.playRandom(context)

    fun playExplosion(context: Context) = SfxPlayer.playExplosion(context)

    fun pause() {
        RoomSoundPlayer.pause()
        DoorSoundPlayer.pause()
        HighQuickGamePlayer.pause()
        BgMusicPlayer.pause()
    }

    fun resume() {
        RoomSoundPlayer.resume()
        DoorSoundPlayer.resume()
        HighQuickGamePlayer.resume()
        BgMusicPlayer.resume()
    }

    fun release() {
        RoomSoundPlayer.release()
        DoorSoundPlayer.release()
        HighQuickGamePlayer.release()
        BgMusicPlayer.release()
        SfxPlayer.release()
    }
}
