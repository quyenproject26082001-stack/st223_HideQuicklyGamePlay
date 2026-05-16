package com.wanted.poster.hihi.activity_app.game

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.wanted.poster.hihi.R
import com.wanted.poster.hihi.core.extensions.handleBackLeftToRight
import com.wanted.poster.hihi.core.extensions.hideNavigation
import com.wanted.poster.hihi.core.extensions.select
import com.wanted.poster.hihi.core.extensions.setOnSingleClick
import com.wanted.poster.hihi.core.extensions.strings
import com.wanted.poster.hihi.core.extensions.visible
import com.wanted.poster.hihi.databinding.ActivityChooseNumberBinding

class ChooseNumberActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChooseNumberBinding
    private var shownSpawnIndices: IntArray = intArrayOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChooseNumberBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupActionBar()


        val mapIndex = intent.getIntExtra(EXTRA_MAP, 1)
        val killerAssetPath = intent.getStringExtra(EXTRA_KILLER_ASSET_PATH)

        val mapData = MapLoader.load(this, mapIndex)
        val allSpawns = mapData.hiderSpawns
        shownSpawnIndices = GameConfig.selectSpawnIndices(allSpawns, minOf(10, allSpawns.size)).toIntArray()
        val shownSpawns = shownSpawnIndices.map { allSpawns[it] }

        binding.mapNumberView.loadAssets(mapIndex, killerAssetPath, shownSpawns, mapData.killerSpawn)
        binding.mapNumberView.phase = MapNumberView.Phase.CHOOSE_NUMBER

        binding.mapNumberView.onNumberSelected = { number ->
            binding.btnStart.isEnabled = true
            binding.btnStart.alpha = 1f
        }


        binding.btnStart.setOnClickListener {
            val selected = binding.mapNumberView.selectedNumber
            if (selected == -1) return@setOnClickListener
            val intent = Intent(this, PlayingActivity::class.java).apply {
                putExtra(EXTRA_MAP, mapIndex)
                putExtra(EXTRA_KILLER_ASSET_PATH, killerAssetPath)
                putExtra(EXTRA_SELECTED_NUMBER, selected)
                putExtra(EXTRA_SHOWN_SPAWN_INDICES, shownSpawnIndices)
            }
            startActivity(intent)
        }
    }

    private fun makeFullscreen() {
        window.decorView.systemUiVisibility = (
            android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
            or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )
    }


    private fun setupActionBar() {
        binding.actionBar.apply {
            btnActionBarLeft.setImageResource(R.drawable.ic_back)
            btnActionBarLeft.visible()
            tvCenter.text = strings(R.string.choose_your_number)
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

    companion object {
        const val EXTRA_MAP = "extra_map"
        const val EXTRA_KILLER_ASSET_PATH = "extra_killer_asset_path"
        const val EXTRA_SELECTED_NUMBER = "extra_selected_number"
        const val EXTRA_SHOWN_SPAWN_INDICES = "extra_shown_spawn_indices"
    }
}
