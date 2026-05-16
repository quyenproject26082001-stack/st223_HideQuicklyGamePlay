package com.wanted.poster.hihi.activity_app.game

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.wanted.poster.hihi.activity_app.main.MainActivity
import androidx.lifecycle.lifecycleScope
import com.wanted.poster.hihi.R
import com.wanted.poster.hihi.core.extensions.hideNavigation
import com.wanted.poster.hihi.databinding.ActivityPlayingBinding
import com.wanted.poster.hihi.databinding.DialogEndGameLostSingleBinding
import com.wanted.poster.hihi.databinding.DialogEndGameWinSingleBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlayingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlayingBinding
    private lateinit var mapData: MapData

    private var shownIndices: List<Int> = emptyList()
    private var idxToDisplayNum: Map<Int, Int> = emptyMap()

    private var isPaused = false
    private var isCountdownRunning = false
    private var hasStartedReveal = false
    private var statusText: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        makeFullscreen()

        val mapIndex = intent.getIntExtra(ChooseNumberActivity.EXTRA_MAP, 1)
        val killerAssetPath = intent.getStringExtra(ChooseNumberActivity.EXTRA_KILLER_ASSET_PATH)
        val playerNumber = intent.getIntExtra(ChooseNumberActivity.EXTRA_SELECTED_NUMBER, -1)

        mapData = MapLoader.load(this, mapIndex)
        android.util.Log.d("PlayingActivity", "doors: ${mapData.doorLines.size} -> ${mapData.doorLines}")
        GameAudio.startGame(this)
        val allSpawns = mapData.hiderSpawns
        val passedIndices = intent.getIntArrayExtra(ChooseNumberActivity.EXTRA_SHOWN_SPAWN_INDICES)
            ?.map { it.toInt() }
            ?.filter { it in allSpawns.indices }
            .orEmpty()

        shownIndices = if (passedIndices.isNotEmpty()) {
            passedIndices
        } else {
            (0 until allSpawns.size).shuffled().take(minOf(10, allSpawns.size))
        }

        idxToDisplayNum = shownIndices.mapIndexed { i, idx -> idx to (i + 1) }.toMap()
        val shownSpawns = shownIndices.map { allSpawns[it] }

        binding.mapNumberView.loadAssets(mapIndex, killerAssetPath, shownSpawns, mapData.killerSpawn)
        binding.mapNumberView.phase = MapNumberView.Phase.PLAYING
        binding.mapNumberView.setDebugCollision(mapData.collisionBitmap)
        if (playerNumber != -1) binding.mapNumberView.highlightNumber(playerNumber)

        setupPauseUi()

        binding.btnBack.setOnClickListener { finish() }
        if (GameConfig.DEBUG_KILLER_COLLISION) {
            binding.mapNumberView.setOnLongClickListener {
                binding.mapNumberView.showDebugCollision = !binding.mapNumberView.showDebugCollision
                binding.mapNumberView.invalidate()
                true
            }
        }

        lifecycleScope.launch {
            val killerSpawn = mapData.killerSpawn
            val paths = withContext(Dispatchers.IO) {
                // Tạo 1 instance duy nhất (IntArray đã trích xuất sẵn), rồi tính song song.
                val finder = KillerPathfinder(mapData.collisionBitmap)
                coroutineScope {
                    idxToDisplayNum.entries.map { (spawnIdx, displayNum) ->
                        async {
                            val target = allSpawns.getOrNull(spawnIdx) ?: killerSpawn
                            val path = try {
                                finder.findPath(killerSpawn.x, killerSpawn.y, target.x, target.y)
                                    .ifEmpty { listOf(killerSpawn, target) }
                            } catch (_: Exception) { listOf(killerSpawn, target) }
                            displayNum to path
                        }
                    }.awaitAll().toMap()
                }
            }
            binding.mapNumberView.setIntroEntrancePaths(paths)
            binding.root.post {
                binding.mapNumberView.animatePlayingSpawnsIntro {
                    startCountdownAndReveal(playerNumber)
                }
            }
        }
    }

    private fun startReveal(playerNumber: Int) {
        if (hasStartedReveal) return
        hasStartedReveal = true
        binding.mapNumberView.clearGameState()
        binding.mapNumberView.highlightNumber(playerNumber)
        setStatusText("")

        val allSpawns = mapData.hiderSpawns
        if (allSpawns.isEmpty()) {
            showResultDialog(false, playerNumber)
            return
        }

        val playerSpawnIdx = shownIndices.getOrNull(playerNumber - 1)
        var playerCaught = false
        val aliveDisplayNums = idxToDisplayNum.values.toSet()

        val runner = KillerRunner(
            mapData = mapData,
            shownIndices = shownIndices,
            idxToDisplayNum = idxToDisplayNum,
            // O single, mọi số đang hiện trên map đều là player thật.
            // Người chơi chỉ "đóng vai" một số đã chọn, nên chỉ thua khi số đó bị bắt.
            playerDisplayNums = aliveDisplayNums,
            mapView = binding.mapNumberView,
            context = this,
            isPaused = { isPaused }
        )

        lifecycleScope.launch {
            runner.run(
                onKill = { spawnIdx, displayNum ->
                    if (spawnIdx == playerSpawnIdx || displayNum == playerNumber) {
                        playerCaught = true
                    }
                },
                shouldStop = { playerCaught },
                onStatusChange = { setStatusText(it) }
            )
            // Chờ scream kịp phát trước khi release() huỷ handler.
            delay(450L)
            GameAudio.release()
            setStatusText(null)
            showResultDialog(playerCaught, playerNumber)
        }
    }

    private fun setupPauseUi() {
        updatePauseUi()
        binding.btnPause.setOnClickListener {
            if (isCountdownRunning || !hasStartedReveal) return@setOnClickListener
            isPaused = !isPaused
            if (isPaused) {
                binding.mapNumberView.pauseAnimation()
                GameAudio.pause()
            } else {
                binding.mapNumberView.resumeAnimation()
                GameAudio.resume()
                statusText = null
            }
            updatePauseUi()
        }
    }

    private fun updatePauseUi() {
        binding.btnPause.setBackgroundResource(
            if (isPaused) R.drawable.ic_pause else R.drawable.ic_resume
        )
        binding.btnPause.alpha = if (isCountdownRunning || !hasStartedReveal) 0.55f else 1f
        if (isCountdownRunning) return
        if (isPaused) {
            showOverlayText("PAUSED", 22f)
            return
        }
        hideOverlay()
    }

    private fun setStatusText(message: String?) {
        statusText = message
        updatePauseUi()
    }

    private fun startCountdownAndReveal(playerNumber: Int) {
        if (isCountdownRunning || hasStartedReveal) return
        isCountdownRunning = true
        updatePauseUi()
        binding.countdownRadarView.startCountdown(3) {
            isCountdownRunning = false
            updatePauseUi()
            startReveal(playerNumber)
        }
    }

    private fun showOverlayText(message: String, sizeSp: Float) {
        binding.viewOverlayDim.apply {
            alpha = 0.8f
            visibility = View.VISIBLE
        }
        binding.tvOverLay.apply {
            text = message
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
            alpha = 1f
            scaleX = 1f
            scaleY = 1f
            translationY = 0f
            visibility = View.VISIBLE
        }
    }

    private fun hideOverlay() {
        binding.viewOverlayDim.visibility = View.GONE
        binding.tvOverLay.visibility = View.GONE
    }

    private fun showResultDialog(caught: Boolean, playerRoom: Int) {
        if (caught) GameAudio.playScream(this)
        if (caught) {
            showLostDialog(playerRoom)
        } else {
            showWinDialog(playerRoom)
        }
    }

    private fun showLostDialog(playerRoom: Int) {
        val dialogBinding = DialogEndGameLostSingleBinding.inflate(layoutInflater)
        dialogBinding.tvNumberLost.text = playerRoom.toString()
        dialogBinding.tvBody.text = getString(R.string.you_picked_the_wrong_number)
        showEndGameDialog(
            rootView = dialogBinding.root,
            homeButton = dialogBinding.btnHome,
            playAgainButton = dialogBinding.btnPlayAgain
        )
    }

    private fun showWinDialog(playerRoom: Int) {
        val dialogBinding = DialogEndGameWinSingleBinding.inflate(layoutInflater)
        dialogBinding.tvNumberWin.text = playerRoom.toString()
        dialogBinding.tvBody.text = getString(R.string.you_survived_this_round_successfully)
        showEndGameDialog(
            rootView = dialogBinding.root,
            homeButton = dialogBinding.btnHome,
            playAgainButton = dialogBinding.btnPlayAgain
        )
    }

    private fun showEndGameDialog(rootView: View, homeButton: View, playAgainButton: View) {
        val dialog = Dialog(this).apply {
            requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
            setContentView(rootView)
            setCancelable(false)
            window?.apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT
                )
            }
        }

        homeButton.setOnClickListener {
            dialog.dismiss()
            startActivity(
                Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
            )
            finish()
        }

        playAgainButton.setOnClickListener {
            dialog.dismiss()
            startActivity(
                Intent(this, ChooseKillerActivity::class.java).apply {
                    // Quay lại flow chọn killer hiện có trong back stack,
                    // đồng thời dọn các màn chọn map / chọn số / playing ở phía trên.
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    putExtra(
                        ChooseNumberActivity.EXTRA_KILLER_ASSET_PATH,
                        intent.getStringExtra(ChooseNumberActivity.EXTRA_KILLER_ASSET_PATH)
                    )
                }
            )
        }

        dialog.show()
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
        if (hasFocus) hideNavigation()
    }

    override fun onDestroy() {
        GameAudio.release()
        super.onDestroy()
    }
}
