package com.wanted.poster.hihi.activity_app.game

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
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
            val maxSpots = minOf(10, allSpawns.size)
            shownSpawnIndices = (0 until allSpawns.size).shuffled()
                .take(maxSpots)
                .toIntArray()
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
                    binding.btnConfirm.alpha = 1f
                    binding.btnConfirm.isEnabled = true
                }
            }

            showCurrentPlayer()
        }

        binding.btnConfirm.setOnSingleClick {
            if (pendingDisplayNum == -1) return@setOnSingleClick
            confirmSelection()
        }
    }

    private fun showCurrentPlayer() {
        val player = GameSession.players.getOrNull(currentPlayerIndex) ?: return
        binding.tvCurrentPlayerName.text = player.name
        binding.tvTurnInstruction.text = strings(R.string.choose_your_spot)

        pendingDisplayNum = -1
        binding.btnConfirm.alpha = 0.5f
        binding.btnConfirm.isEnabled = false

        // Reset selection on map view
        binding.mapNumberView.takenNumbers = GameSession.assignedDisplayNums()
        binding.mapNumberView.highlightNumber(-1)
        binding.mapNumberView.invalidate()

        loadCurrentPlayerAvatar(player)
    }

    private fun loadCurrentPlayerAvatar(player: PlayerSetupModel) {
        CoroutineScope(Dispatchers.Main).launch {
            val bmp = withContext(Dispatchers.IO) { loadAvatarBitmap(player) }
            binding.ivCurrentPlayerAvatar.setImageBitmap(bmp)
        }
    }

    private fun confirmSelection() {
        val player = GameSession.players.getOrNull(currentPlayerIndex) ?: return
        GameSession.playerAssignments[currentPlayerIndex] = pendingDisplayNum

        // Load avatar and set on map view
        CoroutineScope(Dispatchers.Main).launch {
            val bmp = withContext(Dispatchers.IO) { loadAvatarBitmap(player) }
            binding.mapNumberView.setSpawnAvatarBitmap(pendingDisplayNum, bmp)
        }

        currentPlayerIndex++
        if (currentPlayerIndex >= GameSession.players.size) {
            startActivity(Intent(this, PlayingMultiActivity::class.java))
            finish()
        } else {
            showCurrentPlayer()
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
