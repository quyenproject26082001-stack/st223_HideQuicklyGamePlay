package com.wanted.poster.maker.activity_app.game

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.wanted.poster.maker.databinding.ActivityGameBinding

class GameActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGameBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGameBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Fullscreen immersive
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )

        binding.btnDebug.setOnClickListener {
            binding.gameView.toggleDebug()
            binding.btnDebug.text = if (binding.gameView.showDebug) "Debug ON" else "Debug OFF"
        }

        binding.btnBack.setOnClickListener { finish() }

        binding.btnPlayGame.setOnClickListener {
            startActivity(Intent(this, ChooseMapActivity::class.java))
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
        }
    }
}
