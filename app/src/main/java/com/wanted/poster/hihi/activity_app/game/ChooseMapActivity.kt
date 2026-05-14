package com.wanted.poster.hihi.activity_app.game

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.wanted.poster.hihi.R
import com.wanted.poster.hihi.activity_app.adapter.MapOptionAdapter
import com.wanted.poster.hihi.core.extensions.handleBackLeftToRight
import com.wanted.poster.hihi.core.extensions.hideNavigation
import com.wanted.poster.hihi.core.extensions.select
import com.wanted.poster.hihi.core.extensions.setOnSingleClick
import com.wanted.poster.hihi.core.extensions.strings
import com.wanted.poster.hihi.core.extensions.visible
import com.wanted.poster.hihi.data.model.MapOption
import com.wanted.poster.hihi.databinding.ActivityChooseMapBinding

class ChooseMapActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChooseMapBinding


    private val mapOptions: List<MapOption> by lazy {
        (1..47).map { i -> MapOption(mapIndex = i, label = "Map $i") }
    }

    private var selectedOption: MapOption? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChooseMapBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupActionBar()
        val killerAssetPath = intent.getStringExtra(EXTRA_KILLER_ASSET_PATH)

        val adapter = MapOptionAdapter(this, mapOptions) { option ->
            selectedOption = option
            binding.btnSelect.isEnabled = true
            binding.btnSelect.alpha = 1f
        }

        binding.rvMaps.layoutManager = GridLayoutManager(this, 2)
        binding.rvMaps.adapter = adapter


        binding.btnSelect.setOnClickListener {
            val opt = selectedOption ?: return@setOnClickListener
            startActivity(Intent(this, ChooseNumberActivity::class.java).apply {
                putExtra(ChooseNumberActivity.EXTRA_MAP, opt.mapIndex)
                putExtra(ChooseNumberActivity.EXTRA_KILLER_ASSET_PATH, killerAssetPath)
            })
        }
    }

    private fun setupActionBar() {
        binding.actionBar.apply {
            btnActionBarLeft.setImageResource(R.drawable.ic_back)
            btnActionBarLeft.visible()
            tvCenter.text = strings(R.string.select_map)
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
        const val EXTRA_KILLER_ASSET_PATH = "extra_killer_asset_path"
    }
}
