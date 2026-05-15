package com.wanted.poster.hihi.activity_app.game

import android.app.Dialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PointF
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.wanted.poster.hihi.R
import com.wanted.poster.hihi.activity_app.main.MainActivity
import com.wanted.poster.hihi.core.extensions.hideNavigation
import com.wanted.poster.hihi.databinding.ActivityPlayingMultiBinding
import com.wanted.poster.hihi.databinding.DialogEndGameMultiBinding
import com.wanted.poster.hihi.databinding.ItemResultPlayerBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

class PlayingMultiActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlayingMultiBinding
    private lateinit var mapData: MapData

    private var shownIndices: List<Int> = emptyList()
    private var idxToDisplayNum: Map<Int, Int> = emptyMap()

    private val rng = java.util.Random()
    private val soundChance = 0.35f

    private var isPaused = false
    private var isCountdownRunning = false
    private var hasStartedReveal = false

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

            DoorSoundPlayer.preload(this@PlayingMultiActivity)
            HighQuickGamePlayer.start(this@PlayingMultiActivity)
            BgMusicPlayer.start(this@PlayingMultiActivity)

            val allSpawns = mapData.hiderSpawns

            // Assign spawns for random mode (if not already assigned by ChooseNumberMulti)
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

            // Load player avatars and set on spawn positions
            loadAndSetAvatars()

            setupPauseUi()
            binding.btnBack.setOnClickListener { finish() }

            binding.root.post { startCountdownAndReveal() }
        }
    }

    private suspend fun loadAndSetAvatars() {
        GameSession.playerAssignments.forEachIndexed { playerIdx, displayNum ->
            if (displayNum == -1) return@forEachIndexed
            val player = GameSession.players.getOrNull(playerIdx) ?: return@forEachIndexed
            val bmp = withContext(Dispatchers.IO) { loadAvatarBitmap(player) }
            binding.mapNumberView.setSpawnAvatarBitmap(displayNum, bmp)
        }
    }

    private fun loadAvatarBitmap(player: PlayerSetupModel): Bitmap? {
        val path = player.avatarPath ?: return null
        return when {
            path.startsWith("flag:") -> null
            else -> try {
                assets.open(path).use { BitmapFactory.decodeStream(it) }
            } catch (_: Exception) { null }
        }
    }

    private fun startReveal() {
        if (hasStartedReveal) return
        hasStartedReveal = true
        binding.mapNumberView.clearGameState()

        // Re-set avatars after clearGameState
        lifecycleScope.launch {
            loadAndSetAvatars()
        }

        val allSpawns = mapData.hiderSpawns
        if (allSpawns.isEmpty()) {
            showResultDialog(emptySet())
            return
        }

        val killerOrder = (0 until allSpawns.size).shuffled()
        val visitLimit = minOf(11, allSpawns.size)
        val toVisit = killerOrder.take(visitLimit)

        lifecycleScope.launch {
            var currentPos = mapData.killerSpawn
            val trail = mutableListOf<PointF>()
            val killedSpawnIndices = mutableSetOf<Int>()
            val caughtDisplayNums = mutableSetOf<Int>()

            fun markKilled(spawnIdx: Int, displayNum: Int?) {
                if (displayNum != null) {
                    binding.mapNumberView.killNumber(displayNum)
                    caughtDisplayNums.add(displayNum)
                }
                SfxPlayer.playKill(this@PlayingMultiActivity)
                killedSpawnIndices.add(spawnIdx)
            }

            for (spawnIdx in toVisit) {
                waitIfPaused()
                val spawnPos = allSpawns[spawnIdx]
                val displayNum = idxToDisplayNum[spawnIdx]
                val soundInfo = if (mapData.rooms.isNotEmpty() && rng.nextFloat() < soundChance) {
                    mapData.rooms.random(rng.asKotlinRandom())
                } else null

                if (soundInfo != null) {
                    waitIfPaused()
                    RoomSoundPlayer.play(this@PlayingMultiActivity, soundInfo.type)
                    binding.mapNumberView.showSoundIndicator(soundInfo.position)
                    val pathToSound = withContext(Dispatchers.IO) { computePath(currentPos, soundInfo.position) }
                    appendTrail(trail, pathToSound)
                    binding.mapNumberView.setKillerTrail(trail.toList())
                    awaitAnimation(pathToSound)
                    currentPos = soundInfo.position
                    binding.mapNumberView.clearSoundIndicator()
                    RoomSoundPlayer.release()
                    pausedDelay(400L)

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
                        val path = withContext(Dispatchers.IO) { computePath(currentPos, nearestPos) }
                        appendTrail(trail, path)
                        binding.mapNumberView.setKillerTrail(trail.toList())
                        awaitAnimation(path)
                        awaitKillShake()
                        markKilled(nearestIdx, nearestNum)
                        currentPos = nearestPos
                    }
                    pausedDelay(500L)
                    continue
                }

                val path = withContext(Dispatchers.IO) { computePath(currentPos, spawnPos) }
                appendTrail(trail, path)
                binding.mapNumberView.setKillerTrail(trail.toList())
                awaitAnimation(path, onCrossDoor = { DoorSoundPlayer.playRandom(this@PlayingMultiActivity) })
                currentPos = spawnPos

                if (displayNum != null) {
                    awaitKillShake()
                    markKilled(spawnIdx, displayNum)
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
                        val sweepPath = withContext(Dispatchers.IO) { computePath(currentPos, nearestPos) }
                        appendTrail(trail, sweepPath)
                        binding.mapNumberView.setKillerTrail(trail.toList())
                        awaitAnimation(sweepPath)
                        if (nearestNum != null) {
                            awaitKillShake()
                            markKilled(nearestIdx, nearestNum)
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
            showResultDialog(caughtDisplayNums)
        }
    }

    private fun appendTrail(trail: MutableList<PointF>, path: List<PointF>) {
        if (trail.isEmpty()) trail.addAll(path)
        else trail.addAll(path.drop(1))
    }

    private suspend fun awaitKillShake() = suspendCancellableCoroutine<Unit> { cont ->
        binding.mapNumberView.animateKillShake { if (cont.isActive) cont.resume(Unit) }
    }

    private suspend fun awaitAnimation(path: List<PointF>, onCrossDoor: (() -> Unit)? = null) {
        waitIfPaused()
        suspendCancellableCoroutine<Unit> { cont ->
            binding.mapNumberView.animateKillerAlongPath(
                path,
                durationMs = GameConfig.killerAnimationDurationMs(path),
                doorLines = mapData.doorLines,
                onCrossDoor = onCrossDoor
            ) { if (cont.isActive) cont.resume(Unit) }
            cont.invokeOnCancellation { binding.mapNumberView.cancelAnimation() }
        }
    }

    private fun computePath(from: PointF, to: PointF): List<PointF> =
        try {
            KillerPathfinder(mapData.collisionBitmap).findPath(from.x, from.y, to.x, to.y)
                .ifEmpty { listOf(from, to) }
        } catch (_: Exception) { listOf(from, to) }

    private fun setupPauseUi() {
        updatePauseUi()
        binding.btnPause.setOnClickListener {
            if (isCountdownRunning || !hasStartedReveal) return@setOnClickListener
            isPaused = !isPaused
            if (isPaused) {
                binding.mapNumberView.pauseAnimation()
                RoomSoundPlayer.pause(); DoorSoundPlayer.pause()
                HighQuickGamePlayer.pause(); BgMusicPlayer.pause()
            } else {
                binding.mapNumberView.resumeAnimation()
                RoomSoundPlayer.resume(); DoorSoundPlayer.resume()
                HighQuickGamePlayer.resume(); BgMusicPlayer.resume()
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

    private suspend fun waitIfPaused() { while (isPaused) delay(100L) }

    private suspend fun pausedDelay(ms: Long) {
        var remaining = ms
        while (remaining > 0L) {
            waitIfPaused()
            val step = minOf(100L, remaining)
            delay(step)
            remaining -= step
        }
    }

    private fun startCountdownAndReveal() {
        if (isCountdownRunning || hasStartedReveal) return
        lifecycleScope.launch {
            isCountdownRunning = true
            updatePauseUi()
            binding.viewOverlayDim.apply { alpha = 0f; visibility = View.VISIBLE }
            binding.viewOverlayDim.animate().alpha(0.72f).setDuration(220L).start()

            for (value in 3 downTo 1) {
                animateCountdownValue(value)
            }
            binding.tvOverLay.animate().cancel()
            binding.tvOverLay.animate().alpha(0f).setDuration(160L)
                .withEndAction { binding.tvOverLay.visibility = View.GONE }.start()
            binding.viewOverlayDim.animate().alpha(0f).setDuration(220L)
                .withEndAction { binding.viewOverlayDim.visibility = View.GONE }.start()

            isCountdownRunning = false
            updatePauseUi()
            startReveal()
        }
    }

    private suspend fun animateCountdownValue(value: Int) = suspendCancellableCoroutine<Unit> { cont ->
        binding.tvOverLay.animate().cancel()
        binding.tvOverLay.apply {
            text = value.toString()
            visibility = View.VISIBLE
            alpha = 0f; scaleX = 1.45f; scaleY = 1.45f
            translationY = height * 0.04f
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 92f)
        }
        binding.tvOverLay.animate()
            .alpha(1f).scaleX(1f).scaleY(1f).translationY(0f)
            .setDuration(360L).setInterpolator(DecelerateInterpolator())
            .withEndAction {
                binding.tvOverLay.animate()
                    .alpha(0f).scaleX(0.82f).scaleY(0.82f)
                    .setDuration(240L).setInterpolator(AccelerateInterpolator())
                    .withEndAction { if (cont.isActive) cont.resume(Unit) }.start()
            }.start()
    }

    private fun showResultDialog(caughtDisplayNums: Set<Int>) {
        val dialogBinding = DialogEndGameMultiBinding.inflate(layoutInflater)

        // Build results: playerIndex → caught
        val results = GameSession.players.mapIndexed { idx, player ->
            val displayNum = GameSession.playerAssignments.getOrElse(idx) { -1 }
            val caught = displayNum != -1 && caughtDisplayNums.contains(displayNum)
            Triple(player, displayNum, caught)
        }

        val dialog = Dialog(this).apply {
            requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
            setContentView(dialogBinding.root)
            setCancelable(false)
            window?.apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            }
        }

        dialogBinding.rvResults.layoutManager = LinearLayoutManager(this)
        dialogBinding.rvResults.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun getItemCount() = results.size
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val b = ItemResultPlayerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                return object : RecyclerView.ViewHolder(b.root) {}
            }
            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val (player, _, caught) = results[position]
                val b = ItemResultPlayerBinding.bind(holder.itemView)
                b.tvResultName.text = player.name
                b.tvResultStatus.text = if (caught) getString(R.string.result_caught) else getString(R.string.result_survived)
                b.tvResultStatus.setTextColor(if (caught) Color.parseColor("#FF5555") else Color.parseColor("#55FF55"))
                // Load avatar
                CoroutineScope(Dispatchers.Main).launch {
                    val bmp = withContext(Dispatchers.IO) { loadAvatarBitmap(player) }
                    if (bmp != null) b.ivResultAvatar.setImageBitmap(bmp)
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
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            finish()
        }

        dialog.show()
    }

    private fun java.util.Random.asKotlinRandom() = object : kotlin.random.Random() {
        override fun nextBits(bitCount: Int) = this@asKotlinRandom.nextInt().ushr(32 - bitCount)
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
