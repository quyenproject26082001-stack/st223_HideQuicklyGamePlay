package com.wanted.poster.maker.activity_app.game

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.wanted.poster.maker.databinding.ActivityChooseNumberBinding

class ChooseNumberActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChooseNumberBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChooseNumberBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        makeFullscreen()

        val mapIndex = intent.getIntExtra(EXTRA_MAP, 1)
        val killerIndex = intent.getIntExtra(EXTRA_KILLER, 1)
        val pathMode = intent.getStringExtra(ChooseMapActivity.EXTRA_PATH_MODE) ?: ChooseMapActivity.PATH_MODE_ASTAR

        binding.mapNumberView.loadAssets(mapIndex, killerIndex)
        binding.mapNumberView.phase = MapNumberView.Phase.CHOOSE_NUMBER

        binding.mapNumberView.onNumberSelected = { number ->
            binding.btnStart.isEnabled = true
            binding.btnStart.alpha = 1f
            binding.btnStart.text = "START  [$number]"
        }

        binding.btnBack.setOnClickListener { finish() }

        binding.btnStart.setOnClickListener {
            val selected = binding.mapNumberView.selectedNumber
            if (selected == -1) return@setOnClickListener
            val intent = Intent(this, PlayingActivity::class.java).apply {
                putExtra(EXTRA_MAP, mapIndex)
                putExtra(EXTRA_KILLER, killerIndex)
                putExtra(EXTRA_SELECTED_NUMBER, selected)
                putExtra(ChooseMapActivity.EXTRA_PATH_MODE, pathMode)
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

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) makeFullscreen()
    }

    companion object {
        const val EXTRA_MAP = "extra_map"
        const val EXTRA_KILLER = "extra_killer"
        const val EXTRA_SELECTED_NUMBER = "extra_selected_number"
    }
}
