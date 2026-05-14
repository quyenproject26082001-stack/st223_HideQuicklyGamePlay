package com.wanted.poster.hihi.activity_app.game

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.PointF
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import com.wanted.poster.hihi.activity_app.main.MainActivity
import androidx.lifecycle.lifecycleScope
import com.wanted.poster.hihi.R
import com.wanted.poster.hihi.core.extensions.hideNavigation
import com.wanted.poster.hihi.databinding.ActivityPlayingBinding
import com.wanted.poster.hihi.databinding.DialogEndGameLostSingleBinding
import com.wanted.poster.hihi.databinding.DialogEndGameWinSingleBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

class PlayingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlayingBinding
    private lateinit var mapData: MapData

    private var shownIndices: List<Int> = emptyList()
    private var idxToDisplayNum: Map<Int, Int> = emptyMap()

    private val rng = java.util.Random()
    private val soundChance = 0.35f

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
        DoorSoundPlayer.preload(this)
        HighQuickGamePlayer.start(this)
        BgMusicPlayer.start(this)
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
        binding.mapNumberView.setOnLongClickListener {
            binding.mapNumberView.showDebugCollision = !binding.mapNumberView.showDebugCollision
            binding.mapNumberView.invalidate()
            true
        }

        binding.root.post { startCountdownAndReveal(playerNumber) }
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
        val killerOrder = (0 until allSpawns.size).shuffled()
        val visitLimit = minOf(11, allSpawns.size)

        val caughtAt = if (playerSpawnIdx != null) killerOrder.indexOf(playerSpawnIdx) else -1
        val caught = caughtAt in 0 until visitLimit
        val toVisit = killerOrder.take(if (caught) caughtAt + 1 else visitLimit)

        lifecycleScope.launch {
            var currentPos = mapData.killerSpawn
            val trail = mutableListOf<PointF>()
            val killedSpawnIndices = mutableSetOf<Int>()

            for (spawnIdx in toVisit) {
                waitIfPaused()

                val spawnPos = allSpawns[spawnIdx]
                val displayNum = idxToDisplayNum[spawnIdx]
                val soundInfo = if (mapData.rooms.isNotEmpty() && rng.nextFloat() < soundChance) {
                    mapData.rooms.random(rng.asKotlinRandom())
                } else {
                    null
                }

                if (soundInfo != null) {
                    waitIfPaused()
                    RoomSoundPlayer.play(this@PlayingActivity, soundInfo.type)
                    binding.mapNumberView.showSoundIndicator(soundInfo.position)

                    val pathToSound = withContext(Dispatchers.IO) {
                        computePathToPoint(currentPos, soundInfo.position)
                    }
                    appendTrail(trail, pathToSound)
                    binding.mapNumberView.setKillerTrail(trail.toList())
                    awaitAnimation(pathToSound)
                    currentPos = soundInfo.position
                    binding.mapNumberView.clearSoundIndicator()
                    pausedDelay(400L)

                    // 100%: killer kills the nearest hider after hearing sound
                    waitIfPaused()
                    val nearestIdx = shownIndices
                        .filter { it !in killedSpawnIndices && it in allSpawns.indices }
                        .minByOrNull { idx ->
                            val p = allSpawns[idx]
                            val dx = p.x - currentPos.x; val dy = p.y - currentPos.y
                            dx * dx + dy * dy
                        }
                    if (nearestIdx != null) {
                        val nearestPos = allSpawns[nearestIdx]
                        val nearestNum = idxToDisplayNum[nearestIdx]
                        val pathToHider = withContext(Dispatchers.IO) {
                            computePathToPoint(currentPos, nearestPos)
                        }
                        appendTrail(trail, pathToHider)
                        binding.mapNumberView.setKillerTrail(trail.toList())
                        awaitAnimation(pathToHider)
                        RoomSoundPlayer.release()
                        awaitKillShake()
                        binding.mapNumberView.killNumber(nearestNum ?: 0)
                        SfxPlayer.playKill(this@PlayingActivity)
                        killedSpawnIndices.add(nearestIdx)
                        currentPos = nearestPos
                    } else {
                        RoomSoundPlayer.release()
                    }

                    pausedDelay(500L)
                    continue
                }

                setStatusText(
                    if (displayNum != null) "Killer vao phong $displayNum..."
                    else "Killer kiem tra..."
                )
                val path = withContext(Dispatchers.IO) {
                    computePathToPoint(currentPos, spawnPos)
                }
                appendTrail(trail, path)
                binding.mapNumberView.setKillerTrail(trail.toList())
                awaitAnimation(path, onCrossDoor = { DoorSoundPlayer.playRandom(this@PlayingActivity) })
                currentPos = spawnPos

                if (displayNum != null) {
                    awaitKillShake()
                    binding.mapNumberView.killNumber(displayNum)
                    SfxPlayer.playKill(this@PlayingActivity)
                    killedSpawnIndices.add(spawnIdx)
                } else if (rng.nextFloat() < GameConfig.SWEEP_KILL_CHANCE) {
                    val nearestIdx = shownIndices
                        .filter { it !in killedSpawnIndices && it in allSpawns.indices }
                        .minByOrNull { idx ->
                            val p = allSpawns[idx]
                            val dx = p.x - currentPos.x; val dy = p.y - currentPos.y
                            dx * dx + dy * dy
                        }
                    if (nearestIdx != null) {
                        val nearestPos = allSpawns[nearestIdx]
                        val nearestNum = idxToDisplayNum[nearestIdx]
                        val sweepPath = withContext(Dispatchers.IO) {
                            computePathToPoint(currentPos, nearestPos)
                        }
                        appendTrail(trail, sweepPath)
                        binding.mapNumberView.setKillerTrail(trail.toList())
                        awaitAnimation(sweepPath)
                        if (nearestNum != null) {
                            awaitKillShake()
                            binding.mapNumberView.killNumber(nearestNum)
                            SfxPlayer.playKill(this@PlayingActivity)
                            killedSpawnIndices.add(nearestIdx)
                        }
                        currentPos = nearestPos
                    }
                }
                pausedDelay(500L)
            }

            RoomSoundPlayer.release()
            DoorSoundPlayer.release()
            HighQuickGamePlayer.release()
            BgMusicPlayer.release()
            SfxPlayer.release()
            setStatusText(null)
            showResultDialog(caught, playerNumber)
        }
    }

    private fun appendTrail(trail: MutableList<PointF>, path: List<PointF>) {
        if (trail.isEmpty()) {
            trail.addAll(path)
        } else {
            trail.addAll(path.drop(1))
        }
    }

    private suspend fun awaitKillShake() = suspendCancellableCoroutine<Unit> { cont ->
        binding.mapNumberView.animateKillShake { cont.resume(Unit) }
    }

    private suspend fun awaitAnimation(path: List<PointF>, onCrossDoor: (() -> Unit)? = null) {
        waitIfPaused()
        suspendCancellableCoroutine<Unit> { cont ->
            binding.mapNumberView.animateKillerAlongPath(
                path,
                doorLines = mapData.doorLines,
                onCrossDoor = onCrossDoor
            ) { cont.resume(Unit) }
            cont.invokeOnCancellation { binding.mapNumberView.cancelAnimation() }
        }
    }

    private fun setupPauseUi() {
        updatePauseUi()
        binding.btnPause.setOnClickListener {
            if (isCountdownRunning || !hasStartedReveal) return@setOnClickListener
            isPaused = !isPaused
            if (isPaused) {
                binding.mapNumberView.pauseAnimation()
                RoomSoundPlayer.pause()
                DoorSoundPlayer.pause()
                HighQuickGamePlayer.pause()
                BgMusicPlayer.pause()
            } else {
                binding.mapNumberView.resumeAnimation()
                RoomSoundPlayer.resume()
                DoorSoundPlayer.resume()
                HighQuickGamePlayer.resume()
                BgMusicPlayer.resume()
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

    private suspend fun waitIfPaused() {
        while (isPaused) delay(100L)
    }

    private suspend fun pausedDelay(durationMs: Long) {
        var remaining = durationMs
        while (remaining > 0L) {
            waitIfPaused()
            val step = minOf(100L, remaining)
            delay(step)
            remaining -= step
        }
    }

    private fun computePathToPoint(from: PointF, to: PointF): List<PointF> =
        try {
            KillerPathfinder(mapData.collisionBitmap)
                .findPath(from.x, from.y, to.x, to.y)
                .ifEmpty { listOf(from, to) }
        } catch (_: Exception) {
            listOf(from, to)
        }

    private fun findNearestHider(from: PointF): PointF? =
        mapData.hiderSpawns.minByOrNull { p ->
            val dx = p.x - from.x
            val dy = p.y - from.y
            dx * dx + dy * dy
        }

    private fun java.util.Random.asKotlinRandom() = object : kotlin.random.Random() {
        override fun nextBits(bitCount: Int) = this@asKotlinRandom.nextInt().ushr(32 - bitCount)
    }

    private fun startCountdownAndReveal(playerNumber: Int) {
        if (isCountdownRunning || hasStartedReveal) return
        lifecycleScope.launch {
            isCountdownRunning = true
            updatePauseUi()
            binding.viewOverlayDim.apply {
                alpha = 0f
                visibility = View.VISIBLE
                animate().alpha(0.72f).setDuration(220L).start()
            }
            for (value in 3 downTo 1) {
                animateCountdownValue(value)
            }
            binding.tvOverLay.animate().cancel()
            binding.tvOverLay.animate()
                .alpha(0f)
                .setDuration(160L)
                .withEndAction {
                    binding.tvOverLay.visibility = View.GONE
                }
                .start()
            binding.viewOverlayDim.animate()
                .alpha(0f)
                .setDuration(220L)
                .withEndAction {
                    binding.viewOverlayDim.visibility = View.GONE
                }
                .start()
            isCountdownRunning = false
            updatePauseUi()
            startReveal(playerNumber)
        }
    }

    private suspend fun animateCountdownValue(value: Int) =
        suspendCancellableCoroutine<Unit> { cont ->
            binding.tvOverLay.animate().cancel()
            binding.tvOverLay.apply {
                text = value.toString()
                visibility = View.VISIBLE
                alpha = 0f
                scaleX = 1.45f
                scaleY = 1.45f
                translationY = height * 0.04f
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 92f)
            }
            binding.tvOverLay.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .translationY(0f)
                .setDuration(360L)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction {
                    binding.tvOverLay.animate()
                        .alpha(0f)
                        .scaleX(0.82f)
                        .scaleY(0.82f)
                        .setDuration(240L)
                        .setInterpolator(AccelerateInterpolator())
                        .withEndAction {
                            if (cont.isActive) cont.resume(Unit)
                        }
                        .start()
                }
                .start()
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
        if (caught) SfxPlayer.playScream(this)
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
                Intent(this, ChooseMapActivity::class.java).apply {
                    putExtra(
                        ChooseMapActivity.EXTRA_KILLER_ASSET_PATH,
                        intent.getStringExtra(ChooseNumberActivity.EXTRA_KILLER_ASSET_PATH)
                    )
                }
            )
            finish()
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
        HighQuickGamePlayer.release()
        RoomSoundPlayer.release()
        DoorSoundPlayer.release()
        BgMusicPlayer.release()
        SfxPlayer.release()
        super.onDestroy()
    }
}
