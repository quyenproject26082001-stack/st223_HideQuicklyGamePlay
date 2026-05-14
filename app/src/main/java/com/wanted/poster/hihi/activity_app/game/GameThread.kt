package com.wanted.poster.hihi.activity_app.game

import android.graphics.Canvas
import android.view.SurfaceHolder

class GameThread(
    private val holder: SurfaceHolder,
    private val gameView: GameView
) : Thread() {

    var running = false
    private val targetFps = 60
    private val frameTime = 1000L / targetFps

    override fun run() {
        running = true
        while (running) {
            val start = System.currentTimeMillis()

            gameView.update()

            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas()
                if (canvas != null) {
                    synchronized(holder) {
                        gameView.render(canvas)
                    }
                }
            } finally {
                if (canvas != null) holder.unlockCanvasAndPost(canvas)
            }

            val elapsed = System.currentTimeMillis() - start
            val sleep = frameTime - elapsed
            if (sleep > 0) sleep(sleep)
        }
    }
}
