package com.wanted.poster.maker.activity_app.game

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

    // 10 chỉ số spawn hiển thị cho người chơi (index vào mapData.hiderSpawns)
    private var shownIndices: List<Int> = emptyList()
    // Map ngược: spawnIndex → số hiện trên bản đồ (1-10), null nếu không hiện
    private var idxToDisplayNum: Map<Int, Int> = emptyMap()

    private val rng = java.util.Random()
    private val soundChance = 0.35f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        makeFullscreen()

        val mapIndex    = intent.getIntExtra(ChooseNumberActivity.EXTRA_MAP, 1)
        val killerIndex = intent.getIntExtra(ChooseNumberActivity.EXTRA_KILLER, 1)
        val playerNumber = intent.getIntExtra(ChooseNumberActivity.EXTRA_SELECTED_NUMBER, -1)

        mapData = MapLoader.load(this, mapIndex)

        // Chọn 10 spawns ngẫu nhiên để hiển thị cho người chơi
        val allSpawns = mapData.hiderSpawns
        shownIndices = (0 until allSpawns.size).shuffled().take(minOf(10, allSpawns.size))
        idxToDisplayNum = shownIndices.mapIndexed { i, idx -> idx to (i + 1) }.toMap()
        val shownSpawns = shownIndices.map { allSpawns[it] }

        binding.mapNumberView.loadAssets(mapIndex, killerIndex, shownSpawns)
        binding.mapNumberView.phase = MapNumberView.Phase.PLAYING
        binding.mapNumberView.setDebugCollision(mapData.collisionBitmap)
        if (playerNumber != -1) binding.mapNumberView.highlightNumber(playerNumber)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnReveal.setOnClickListener { startReveal(playerNumber) }

        binding.mapNumberView.setOnLongClickListener {
            binding.mapNumberView.showDebugCollision = !binding.mapNumberView.showDebugCollision
            binding.mapNumberView.invalidate()
            true
        }
    }

    private fun startReveal(playerNumber: Int) {
        binding.btnReveal.visibility = View.GONE
        binding.tvStatus.visibility  = View.VISIBLE
        binding.mapNumberView.clearGameState()
        binding.mapNumberView.highlightNumber(playerNumber)

        val allSpawns = mapData.hiderSpawns
        if (allSpawns.isEmpty()) { showResultDialog(false, playerNumber); return }

        // Spawn mà người chơi đang ẩn (index vào allSpawns)
        val playerSpawnIdx = shownIndices.getOrNull(playerNumber - 1)

        // Killer shuffle tất cả spawns (không biết 10 điểm nào đang hiện)
        val killerOrder = (0 until allSpawns.size).shuffled()
        val visitLimit  = minOf(11, allSpawns.size)

        val caughtAt = if (playerSpawnIdx != null) killerOrder.indexOf(playerSpawnIdx) else -1
        val caught   = caughtAt in 0 until visitLimit
        // Animate đến lúc bắt được hoặc hết visitLimit
        val toVisit  = killerOrder.take(if (caught) caughtAt + 1 else visitLimit)

        lifecycleScope.launch {
            var currentPos = mapData.killerSpawn
            val trail = mutableListOf<PointF>()

            for (spawnIdx in toVisit) {
                val spawnPos   = allSpawns[spawnIdx]
                val displayNum = idxToDisplayNum[spawnIdx]  // null = điểm ẩn, không có số

                // ── Kiểm tra tiếng động ──────────────────────────────────────
                val soundInfo = if (mapData.rooms.isNotEmpty() && rng.nextFloat() < soundChance)
                    mapData.rooms.random(rng.asKotlinRandom()) else null

                if (soundInfo != null) {
                    RoomSoundPlayer.play(this@PlayingActivity, soundInfo.type)
                    binding.tvStatus.text = "Có tiếng từ ${soundInfo.type.name.lowercase()}…"

                    val pathToSound = withContext(Dispatchers.IO) {
                        computePathToPoint(currentPos, soundInfo.position)
                    }
                    appendTrail(trail, pathToSound)
                    binding.mapNumberView.setKillerTrail(trail.toList())
                    awaitAnimation(pathToSound)
                    currentPos = soundInfo.position
                    delay(400L)

                    val nearestHider = findNearestHider(currentPos)
                    if (nearestHider != null) {
                        binding.tvStatus.text = "Killer tới phòng gần nhất…"
                        val pathToHider = withContext(Dispatchers.IO) {
                            computePathToPoint(currentPos, nearestHider)
                        }
                        appendTrail(trail, pathToHider)
                        binding.mapNumberView.setKillerTrail(trail.toList())
                        awaitAnimation(pathToHider)
                        currentPos = nearestHider
                    }
                    RoomSoundPlayer.release()
                    delay(500L)
                    continue
                }

                // ── Di chuyển bình thường ────────────────────────────────────
                binding.tvStatus.text = if (displayNum != null) "Killer vào phòng $displayNum…"
                                        else                    "Killer kiểm tra…"
                val path = withContext(Dispatchers.IO) { computePathToPoint(currentPos, spawnPos) }
                appendTrail(trail, path)
                binding.mapNumberView.setKillerTrail(trail.toList())
                awaitAnimation(path)
                if (displayNum != null) binding.mapNumberView.killNumber(displayNum)
                currentPos = spawnPos
                delay(500L)
            }

            RoomSoundPlayer.release()
            binding.tvStatus.visibility = View.GONE
            showResultDialog(caught, playerNumber)
        }
    }

    private fun appendTrail(trail: MutableList<PointF>, path: List<PointF>) {
        if (trail.isEmpty()) trail.addAll(path) else trail.addAll(path.drop(1))
    }

    private suspend fun awaitAnimation(path: List<PointF>) =
        suspendCancellableCoroutine<Unit> { cont ->
            binding.mapNumberView.animateKillerAlongPath(path) { cont.resume(Unit) }
            cont.invokeOnCancellation { binding.mapNumberView.cancelAnimation() }
        }

    private fun computePathToPoint(from: PointF, to: PointF): List<PointF> =
        try {
            KillerPathfinder(mapData.collisionBitmap)
                .findPath(from.x, from.y, to.x, to.y)
                .ifEmpty { listOf(from, to) }
        } catch (_: Exception) { listOf(from, to) }

    private fun findNearestHider(from: PointF): PointF? =
        mapData.hiderSpawns.minByOrNull { p ->
            val dx = p.x - from.x; val dy = p.y - from.y; dx * dx + dy * dy
        }

    private fun java.util.Random.asKotlinRandom() = object : kotlin.random.Random() {
        override fun nextBits(bitCount: Int) = this@asKotlinRandom.nextInt().ushr(32 - bitCount)
    }

    private fun showResultDialog(caught: Boolean, playerRoom: Int) {
        val title   = if (caught) "OOPS! Bị bắt rồi!" else "YOU SURVIVED!"
        val message = if (caught)
            "Killer tìm thấy bạn ở phòng $playerRoom!\nChúc may mắn lần sau!"
        else
            "Bạn đã sống sót!\nKiller không tìm thấy phòng $playerRoom."

        AlertDialog.Builder(this)
            .setTitle(title).setMessage(message).setCancelable(false)
            .setPositiveButton("Chơi lại") { _, _ -> finish() }
            .setNegativeButton("Thoát")    { _, _ -> finishAffinity() }
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
