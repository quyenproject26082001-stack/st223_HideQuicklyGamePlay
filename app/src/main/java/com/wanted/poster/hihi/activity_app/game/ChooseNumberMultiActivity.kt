package com.wanted.poster.hihi.activity_app.game

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.wanted.poster.hihi.R
import com.wanted.poster.hihi.core.extensions.handleBackLeftToRight
import com.wanted.poster.hihi.core.extensions.hideNavigation
import com.wanted.poster.hihi.core.extensions.select
import com.wanted.poster.hihi.core.extensions.setOnSingleClick
import com.wanted.poster.hihi.core.extensions.strings
import com.wanted.poster.hihi.core.extensions.visible
import com.wanted.poster.hihi.databinding.ActivityChooseNumberMultiBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChooseNumberMultiActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChooseNumberMultiBinding
    private lateinit var mapData: MapData
    private var shownSpawnIndices: IntArray = intArrayOf()

    private var currentPlayerIndex = 0
    private var pendingDisplayNum = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChooseNumberMultiBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupActionBar()

        CoroutineScope(Dispatchers.Main).launch {
            mapData = withContext(Dispatchers.IO) {
                MapLoader.load(this@ChooseNumberMultiActivity, GameSession.mapIndex)
            }

            val allSpawns = mapData.hiderSpawns
            val maxSpots = minOf(GameSession.players.size, allSpawns.size)
            shownSpawnIndices = GameConfig.selectSpawnIndices(allSpawns, maxSpots).toIntArray()
            GameSession.shownSpawnIndices = shownSpawnIndices

            val shownSpawns = shownSpawnIndices.map { allSpawns[it] }
            binding.mapNumberView.loadAssets(
                GameSession.mapIndex,
                GameSession.killerAssetPath,
                shownSpawns,
                mapData.killerSpawn
            )
            binding.mapNumberView.phase = MapNumberView.Phase.CHOOSE_NUMBER

            binding.mapNumberView.onNumberSelected = { displayNum ->
                if (!GameSession.assignedDisplayNums().contains(displayNum)) {
                    pendingDisplayNum = displayNum
                    binding.btnConfirm.visibility = android.view.View.VISIBLE
                }
            }

            binding.btnConfirm.setOnSingleClick {
                val num = pendingDisplayNum
                if (num != -1 && !GameSession.assignedDisplayNums().contains(num)) {
                    binding.btnConfirm.visibility = android.view.View.GONE
                    confirmSelection(num)
                }
            }

            showCurrentPlayer()
        }
    }

    private fun showCurrentPlayer() {
        val player = GameSession.players.getOrNull(currentPlayerIndex) ?: return
        binding.tvCurrentPlayerName.text = "Player ${currentPlayerIndex + 1}'s turn"
        binding.tvTurnInstruction.text = strings(R.string.choose_your_spot)

        pendingDisplayNum = -1
        binding.btnConfirm.visibility = android.view.View.GONE
        binding.mapNumberView.takenNumbers = GameSession.assignedDisplayNums()
        binding.mapNumberView.highlightNumber(-1)
        binding.mapNumberView.invalidate()

        loadCurrentPlayerAvatar(player)
    }

    private fun loadCurrentPlayerAvatar(player: PlayerSetupModel) {
        CoroutineScope(Dispatchers.Main).launch {
            val bmp = withContext(Dispatchers.IO) { AvatarLoader.load(this@ChooseNumberMultiActivity, player) }
            binding.ivCurrentPlayerAvatar.setImageBitmap(bmp)
        }
    }

    private fun confirmSelection(displayNum: Int) {
        val player = GameSession.players.getOrNull(currentPlayerIndex) ?: return
        GameSession.playerAssignments[currentPlayerIndex] = displayNum

        val isLast = (currentPlayerIndex + 1) >= GameSession.players.size
        currentPlayerIndex++

        CoroutineScope(Dispatchers.Main).launch {
            val bmp = withContext(Dispatchers.IO) { AvatarLoader.load(this@ChooseNumberMultiActivity, player) }
            binding.mapNumberView.setSpawnAvatarBitmap(displayNum, bmp)

            if (isLast) {
                binding.tvAllReady.visibility = android.view.View.VISIBLE
                kotlinx.coroutines.delay(2000)
                startActivity(Intent(this@ChooseNumberMultiActivity, PlayingMultiActivity::class.java))
                finish()
            }
        }

        if (!isLast) showCurrentPlayer()
    }

    private fun setupActionBar() {
        binding.actionBar.apply {
            btnActionBarLeft.setImageResource(R.drawable.ic_back)
            btnActionBarLeft.visible()
            tvCenter.text = strings(R.string.choose_your_spot)
            tvCenter.visible()
            tvCenterBlur.visible()
            tvCenter.select()
            btnActionBarLeft.setOnSingleClick { handleBackLeftToRight() }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideNavigation()
    }
}
