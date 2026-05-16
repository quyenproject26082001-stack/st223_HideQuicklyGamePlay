package com.wanted.poster.hihi.activity_app.game

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.wanted.poster.hihi.R
import com.wanted.poster.hihi.activity_app.main.MainActivity
import com.wanted.poster.hihi.core.extensions.hideNavigation
import com.wanted.poster.hihi.databinding.ActivityPlayingMultiBinding
import com.wanted.poster.hihi.databinding.DialogEndGameWinMultiBinding
import com.wanted.poster.hihi.databinding.ItemSurvivorMultiBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlayingMultiActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlayingMultiBinding
    private lateinit var mapData: MapData

    private var shownIndices: List<Int> = emptyList()
    private var idxToDisplayNum: Map<Int, Int> = emptyMap()

    private var isPaused = false
    private var isCountdownRunning = false
    private var hasStartedReveal = false

    private data class RankedPlayerResult(
        val player: PlayerSetupModel,
        val displayNum: Int,
        val isSurvivor: Boolean,
        val killOrder: Int?
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayingMultiBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        makeFullscreen()

        lifecycleScope.launch {
            mapData = withContext(Dispatchers.IO) {
                MapLoader.load(this@PlayingMultiActivity, GameSession.mapIndex)
            }

            GameAudio.startGame(this@PlayingMultiActivity)

            val allSpawns = mapData.hiderSpawns

            if (GameSession.shownSpawnIndices.isEmpty()) {
                val maxSpots = minOf(10, allSpawns.size, GameSession.players.size)
                val randomIndices = (0 until allSpawns.size).shuffled().take(maxSpots)
                GameSession.shownSpawnIndices = randomIndices.toIntArray()
                randomIndices.forEachIndexed { i, spawnIdx ->
                    if (i < GameSession.playerAssignments.size) {
                        GameSession.playerAssignments[i] = i + 1
                    }
                }
            }

            shownIndices = GameSession.shownSpawnIndices.toList()
            idxToDisplayNum = shownIndices.mapIndexed { i, idx -> idx to (i + 1) }.toMap()
            val shownSpawns = shownIndices.map { allSpawns[it] }

            binding.mapNumberView.loadAssets(
                GameSession.mapIndex,
                GameSession.killerAssetPath,
                shownSpawns,
                mapData.killerSpawn
            )
            binding.mapNumberView.phase = MapNumberView.Phase.PLAYING
            binding.mapNumberView.setDebugCollision(mapData.collisionBitmap)

            loadAndSetAvatars()

            setupPauseUi()
            binding.btnBack.setOnClickListener { finish() }
            if (GameConfig.DEBUG_KILLER_COLLISION) {
                binding.mapNumberView.setOnLongClickListener {
                    binding.mapNumberView.showDebugCollision = !binding.mapNumberView.showDebugCollision
                    binding.mapNumberView.invalidate()
                    true
                }
            }

            val killerSpawn = mapData.killerSpawn
            val paths = withContext(Dispatchers.IO) {
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
                    startCountdownAndReveal()
                }
            }
        }
    }

    private suspend fun loadAndSetAvatars() {
        GameSession.playerAssignments.forEachIndexed { playerIdx, displayNum ->
            if (displayNum == -1) return@forEachIndexed
            val player = GameSession.players.getOrNull(playerIdx) ?: return@forEachIndexed
            val bmp = withContext(Dispatchers.IO) { AvatarLoader.load(this@PlayingMultiActivity, player) }
            binding.mapNumberView.setSpawnAvatarBitmap(displayNum, bmp)
        }
    }

    private fun startReveal() {
        if (hasStartedReveal) return
        hasStartedReveal = true
        binding.mapNumberView.clearGameState()

        lifecycleScope.launch { loadAndSetAvatars() }

        val allSpawns = mapData.hiderSpawns
        if (allSpawns.isEmpty()) {
            GameAudio.release()
            showResultDialog(emptyList())
            return
        }

        val caughtDisplayNums = mutableSetOf<Int>()
        val killOrderDisplayNums = mutableListOf<Int>()
        val assignedPlayerCount = GameSession.playerAssignments.count { it != -1 }
        val maxSurvivors = minOf(3, (assignedPlayerCount - 1).coerceAtLeast(1))
        val survivors = (1..maxSurvivors).random()
        val killsNeededToFinish = (assignedPlayerCount - survivors).coerceAtLeast(0)

        if (killsNeededToFinish == 0) {
            GameAudio.release()
            showResultDialog(emptyList())
            return
        }

        val runner = KillerRunner(
            mapData = mapData,
            shownIndices = shownIndices,
            idxToDisplayNum = idxToDisplayNum,
            playerDisplayNums = GameSession.assignedDisplayNums(),
            mapView = binding.mapNumberView,
            context = this,
            isPaused = { isPaused }
        )

        lifecycleScope.launch {
            runner.run(
                onKill = { _, displayNum ->
                    if (displayNum != null && caughtDisplayNums.add(displayNum)) {
                        killOrderDisplayNums.add(displayNum)
                        val playerIdx = GameSession.playerAssignments.indexOfFirst { it == displayNum }
                        val playerName = GameSession.players.getOrNull(playerIdx)?.name ?: ""
                        binding.tvPlayerDeadName.text = getString(R.string.has_dead, playerName)
                        binding.layoutPlayerDead.visibility = View.VISIBLE
                        lifecycleScope.launch {
                            delay(2500L)
                            binding.layoutPlayerDead.visibility = View.GONE
                        }
                    }
                },
                shouldStop = { caughtDisplayNums.size >= killsNeededToFinish }
            )
            // Chờ scream kịp phát trước khi release() huỷ handler.
            delay(450L)
            GameAudio.release()
            showResultDialog(killOrderDisplayNums)
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
            }
            updatePauseUi()
        }
    }

    private fun updatePauseUi() {
        binding.btnPause.setBackgroundResource(if (isPaused) R.drawable.ic_pause else R.drawable.ic_resume)
        binding.btnPause.alpha = if (isCountdownRunning || !hasStartedReveal) 0.55f else 1f
        if (isCountdownRunning) return
        if (isPaused) {
            binding.viewOverlayDim.alpha = 0.8f
            binding.viewOverlayDim.visibility = View.VISIBLE
            binding.tvOverLay.text = "PAUSED"
            binding.tvOverLay.setTextSize(TypedValue.COMPLEX_UNIT_SP, 42f)
            binding.tvOverLay.visibility = View.VISIBLE
        } else {
            binding.viewOverlayDim.visibility = View.GONE
            binding.tvOverLay.visibility = View.GONE
        }
    }

    private fun startCountdownAndReveal() {
        if (isCountdownRunning || hasStartedReveal) return
        isCountdownRunning = true
        updatePauseUi()
        binding.countdownRadarView.startCountdown(3) {
            isCountdownRunning = false
            updatePauseUi()
            startReveal()
        }
    }

    private fun showResultDialog(killOrderDisplayNums: List<Int>) {
        val dialogBinding = DialogEndGameWinMultiBinding.inflate(layoutInflater)
        val killOrderByDisplayNum = killOrderDisplayNums.withIndex().associate { (index, displayNum) ->
            displayNum to index
        }
        val rankedResults = GameSession.players.mapIndexed { idx, player ->
            val displayNum = GameSession.playerAssignments.getOrElse(idx) { -1 }
            val killOrder = killOrderByDisplayNum[displayNum]
            RankedPlayerResult(
                player = player,
                displayNum = displayNum,
                isSurvivor = killOrder == null,
                killOrder = killOrder
            )
        }.sortedWith(
            compareByDescending<RankedPlayerResult> { it.isSurvivor }
                .thenByDescending { it.killOrder ?: -1 }
                .thenBy { if (it.displayNum == -1) Int.MAX_VALUE else it.displayNum }
                .thenBy { it.player.name.lowercase() }
        )

        val dialog = Dialog(this).apply {
            requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
            setContentView(dialogBinding.root)
            setCancelable(false)
            window?.apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            }
        }

        class ResultViewHolder(val binding: ItemSurvivorMultiBinding) :
            RecyclerView.ViewHolder(binding.root)

        dialogBinding.rvSurvivors.layoutManager = LinearLayoutManager(this)
        dialogBinding.rvSurvivors.adapter = object : RecyclerView.Adapter<ResultViewHolder>() {
            override fun getItemCount() = rankedResults.size
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ResultViewHolder {
                val b = ItemSurvivorMultiBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                return ResultViewHolder(b)
            }
            override fun onBindViewHolder(holder: ResultViewHolder, position: Int) {
                val result = rankedResults[position]
                val b = holder.binding
                b.tvRank.text = (position + 1).toString()
                b.tvName.text = result.player.name
                b.tvStatus.text = getString(
                    if (result.isSurvivor) R.string.result_survived else R.string.result_caught
                )
                b.tvStatus.setTextColor(
                    ContextCompat.getColor(
                        this@PlayingMultiActivity,
                        if (result.isSurvivor) R.color.result_survived_green else R.color.color_app
                    )
                )
                CoroutineScope(Dispatchers.Main).launch {
                    val bmp = withContext(Dispatchers.IO) {
                        AvatarLoader.load(this@PlayingMultiActivity, result.player)
                    }
                    if (bmp != null) b.ivAvatar.setImageBitmap(bmp)
                }
            }
        }

        dialogBinding.btnHome.setOnClickListener {
            dialog.dismiss()
            startActivity(Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            finish()
        }

        dialogBinding.btnPlayAgain.setOnClickListener {
            dialog.dismiss()
            startActivity(Intent(this, SetupGameActivity::class.java).apply {
                // Quay lại màn setup cũ trong task hiện tại để back từ action bar
                // trở về Main thay vì thoát app vì task đã bị clear.
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            })
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
