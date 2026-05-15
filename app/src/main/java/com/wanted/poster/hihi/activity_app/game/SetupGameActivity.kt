package com.wanted.poster.hihi.activity_app.game

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.wanted.poster.hihi.R
import com.wanted.poster.hihi.activity_app.adapter.PlayerSetupAdapter
import com.wanted.poster.hihi.core.extensions.handleBackLeftToRight
import com.wanted.poster.hihi.core.extensions.hideNavigation
import com.wanted.poster.hihi.core.extensions.select
import com.wanted.poster.hihi.core.extensions.setOnSingleClick
import com.wanted.poster.hihi.core.extensions.strings
import com.wanted.poster.hihi.core.extensions.visible
import com.wanted.poster.hihi.data.model.MapOption
import com.wanted.poster.hihi.databinding.ActivitySetupGameBinding

class SetupGameActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySetupGameBinding
    private val players = mutableListOf<PlayerSetupModel>()
    private var selectedMapOption: MapOption? = null
    private var selectedKillerPath: String? = null
    private var defaultAvatarPath: String? = null
    private var allAvatarPaths: List<String> = emptyList()
    private lateinit var playerAdapter: PlayerSetupAdapter

    companion object {
        private const val DEFAULT_PLAYER_COUNT = 4
        private const val MIN_PLAYERS = 2
        private const val MAX_PLAYERS = 10
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySetupGameBinding.inflate(layoutInflater)
        setContentView(binding.root)
        allAvatarPaths = resolveAllAvatarPaths()
        defaultAvatarPath = allAvatarPaths.firstOrNull()
        setupActionBar()
        initPlayers()
        setupRecyclerView()
        setupClicks()
        initDefaults()
    }

    private fun setupActionBar() {
        binding.actionBar.apply {
            btnActionBarLeft.setImageResource(R.drawable.ic_back)
            btnActionBarLeft.visible()
            tvCenter.text = strings(R.string.set_up_game)
            tvCenter.visible()
            tvCenterBlur.visible()
            tvCenter.select()
            btnActionBarLeft.setOnSingleClick { handleBackLeftToRight() }
        }
    }

    private fun initPlayers() {
        repeat(DEFAULT_PLAYER_COUNT) { i ->
            players.add(
                PlayerSetupModel(
                    id = i + 1,
                    name = "Player ${i + 1}",
                    avatarPath = avatarForIndex(i)
                )
            )
        }
        updatePlayerCount()
    }

    private fun setupRecyclerView() {
        playerAdapter = PlayerSetupAdapter(
            players = players,
            defaultAvatarPath = defaultAvatarPath,
            onAvatarClick = { index ->
                ChooseAvatarDialog(this) { avatarPath ->
                    players[index].avatarPath = avatarPath
                    playerAdapter.notifyItemChanged(index)
                }.show()
            },
            onEditClick = { index ->
                RenamePlayerDialog(this, players[index].name) { newName ->
                    players[index].name = newName
                    playerAdapter.notifyItemChanged(index)
                }.show()
            },
            onDeleteClick = { index ->
                if (players.size > MIN_PLAYERS) {
                    players.removeAt(index)
                    playerAdapter.notifyItemRemoved(index)
                    playerAdapter.notifyItemRangeChanged(index, players.size - index)
                    updatePlayerCount()
                }
            }
        )
        binding.rvPlayers.layoutManager = LinearLayoutManager(this)
        binding.rvPlayers.adapter = playerAdapter
    }

    private fun setupClicks() {
        binding.btnChooseMap.setOnSingleClick {
            ChooseMapSetupDialog(this) { mapOption ->
                selectedMapOption = mapOption
                updateMapHexagon(mapOption)
            }.show()
        }

        binding.btnChooseKiller.setOnSingleClick {
            ChooseKillerSetupDialog(this) { killerPath ->
                selectedKillerPath = killerPath
                updateKillerHexagon(killerPath)
            }.show()
        }

        binding.btnMinus.setOnSingleClick {
            if (players.size > MIN_PLAYERS) {
                players.removeLastOrNull()
                playerAdapter.notifyItemRemoved(players.size)
                updatePlayerCount()
            }
        }

        binding.btnPlus.setOnSingleClick {
            if (players.size < MAX_PLAYERS) {
                val newId = players.size + 1
                players.add(
                    PlayerSetupModel(
                        id = newId,
                        name = "Player $newId",
                        avatarPath = avatarForIndex(players.size)
                    )
                )
                playerAdapter.notifyItemInserted(players.size - 1)
                updatePlayerCount()
            }
        }

        binding.btnStartPlay.setOnSingleClick {
            val mapOption = selectedMapOption ?: run {
                Toast.makeText(this, "Please select a map first", Toast.LENGTH_SHORT).show()
                return@setOnSingleClick
            }
            val killerPath = selectedKillerPath ?: run {
                Toast.makeText(this, "Please select a killer first", Toast.LENGTH_SHORT).show()
                return@setOnSingleClick
            }
            ChooseModeSetupDialog(this) { isManual ->
                GameSession.setup(players.toList(), mapOption.mapIndex, killerPath)
                if (isManual) {
                    startActivity(Intent(this, ChooseNumberMultiActivity::class.java))
                } else {
                    startActivity(Intent(this, PlayingMultiActivity::class.java))
                }
            }.show()
        }
    }

    private fun initDefaults() {
        selectedMapOption = MapOption(mapIndex = 1, label = "Map 1")
        updateMapHexagon(selectedMapOption!!)

        val firstKiller = assets.list("killer_removebg")?.firstOrNull()
        if (firstKiller != null) {
            selectedKillerPath = "killer_removebg/$firstKiller"
            updateKillerHexagon(selectedKillerPath!!)
        }
    }

    private fun updatePlayerCount() {
        binding.tvPlayerCount.text = players.size.toString()
    }

    private fun resolveAllAvatarPaths(): List<String> =
        (assets.list("avatar") ?: emptyArray())
            .sortedBy { it.substringBeforeLast(".").toIntOrNull() ?: Int.MAX_VALUE }
            .map { "avatar/$it" }

    private fun avatarForIndex(index: Int): String? =
        if (allAvatarPaths.isEmpty()) null
        else allAvatarPaths[index % allAvatarPaths.size]

    private fun updateMapHexagon(mapOption: MapOption) {
        try {
            assets.open("Map/${mapOption.mapIndex}.jpg").use { stream ->
                binding.ivMapPreview.setImageBitmap(BitmapFactory.decodeStream(stream))
            }
        } catch (_: Exception) {}
    }

    private fun updateKillerHexagon(killerPath: String) {
        try {
            assets.open(killerPath).use { stream ->
                binding.ivKillerPreview.setImageBitmap(BitmapFactory.decodeStream(stream))
            }
        } catch (_: Exception) {}
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideNavigation()
    }
}
