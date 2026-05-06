package com.wanted.poster.maker.activity_app.game

import android.graphics.BitmapFactory
import android.graphics.PointF
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.wanted.poster.maker.databinding.ActivityPlayingBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume



class PlayingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlayingBinding
    private lateinit var mapData: MapData
    private var isPaused = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        makeFullscreen()

        val mapIndex     = intent.getIntExtra(ChooseNumberActivity.EXTRA_MAP, 1)
        val killerIndex  = intent.getIntExtra(ChooseNumberActivity.EXTRA_KILLER, 1)
        val playerNumber = intent.getIntExtra(ChooseNumberActivity.EXTRA_SELECTED_NUMBER, -1)
        val pathMode     = intent.getStringExtra(ChooseMapActivity.EXTRA_PATH_MODE)
                          ?: ChooseMapActivity.PATH_MODE_ASTAR

        mapData = MapLoader.load(this, mapIndex)

        binding.mapNumberView.loadAssets(mapIndex, killerIndex, mapData.hiderSpawns)
        binding.mapNumberView.phase = MapNumberView.Phase.PLAYING
        binding.mapNumberView.setDebugCollision(mapData.collisionBitmap)
        if (playerNumber != -1) binding.mapNumberView.highlightNumber(playerNumber)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnPause.setOnClickListener {
            isPaused = !isPaused
            binding.btnPause.text = if (isPaused) "▶" else "⏸"
        }
        binding.btnReveal.setOnClickListener {
            startReveal(mapIndex, playerNumber, pathMode)
        }

        // Debug: tap góc trên-phải để bật/tắt collision overlay
        binding.mapNumberView.setOnLongClickListener {
            binding.mapNumberView.showDebugCollision = !binding.mapNumberView.showDebugCollision
            binding.mapNumberView.invalidate()
            true
        }
    }

    private fun startReveal(mapIndex: Int, playerNumber: Int, pathMode: String) {
        binding.btnReveal.visibility = View.GONE
        binding.tvStatus.visibility = View.VISIBLE
        binding.mapNumberView.clearGameState()
        binding.mapNumberView.highlightNumber(playerNumber)

        val roomCount = mapData.hiderSpawns.size.coerceAtLeast(1)
        val shuffled = (1..roomCount).shuffled()
        val playerIdx = shuffled.indexOf(playerNumber)
        val caught = playerIdx < 11
        // If caught: animate until killer reaches player's room; else: animate through 11 rooms
        val roomsToVisit = if (caught) shuffled.subList(0, playerIdx + 1)
                           else        shuffled.subList(0, 11)

        lifecycleScope.launch {
            var currentPos = mapData.killerSpawn
            val accumulatedTrail = mutableListOf<PointF>()

            for (room in roomsToVisit) {
                binding.tvStatus.text = "Killer entering room $room…"
                val from = currentPos
                val path = withContext(Dispatchers.IO) { computePath(from, room, pathMode) }

                // Accumulate trail (skip duplicate start point after first segment)
                if (accumulatedTrail.isEmpty()) accumulatedTrail.addAll(path)
                else accumulatedTrail.addAll(path.drop(1))
                binding.mapNumberView.setKillerTrail(accumulatedTrail.toList())

                suspendCancellableCoroutine<Unit> { cont ->
                    binding.mapNumberView.animateKillerAlongPath(path, 2500L) { cont.resume(Unit) }
                    cont.invokeOnCancellation { binding.mapNumberView.cancelAnimation() }
                }

                binding.mapNumberView.killNumber(room)
                currentPos = path.last()
                delay(500L)
            }

            binding.tvStatus.visibility = View.GONE
            showResultDialog(caught, playerNumber)
        }
    }

    private fun computePath(fromPos: PointF, targetRoom: Int, pathMode: String): List<PointF> {
        val target = mapData.hiderSpawns.getOrNull(targetRoom - 1) ?: return listOf(fromPos)

        return when (pathMode) {
            ChooseMapActivity.PATH_MODE_ASTAR -> {
                try {
                    val result = KillerPathfinder(mapData.collisionBitmap)
                        .findPath(fromPos.x, fromPos.y, target.x, target.y)
                    result.ifEmpty { listOf(fromPos, target) }
                } catch (_: Exception) {
                    listOf(fromPos, target)
                }
            }
            else -> listOf(fromPos, target)
        }
    }

    private fun showResultDialog(caught: Boolean, playerRoom: Int) {
        val title   = if (caught) "OOPS! You're caught!" else "YOU SURVIVED!"
        val message = if (caught)
            "Killer found you in room $playerRoom!\nBetter luck next time!"
        else
            "You were the last one standing!\nYou hid in room $playerRoom.\nEveryone else was eliminated."

        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton("Play Again") { _, _ -> finish() }
            .setNegativeButton("Quit") { _, _ -> finishAffinity() }
            .show()
    }

    private fun makeFullscreen() {
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) makeFullscreen()
    }
}
