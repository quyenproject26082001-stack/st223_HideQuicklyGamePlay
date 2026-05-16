package com.wanted.poster.hihi.activity_app.game

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.wanted.poster.hihi.activity_app.adapter.KillerAdapter
import com.wanted.poster.hihi.core.extensions.handleBackLeftToRight
import com.wanted.poster.hihi.core.extensions.hideNavigation
import com.wanted.poster.hihi.core.extensions.select
import com.wanted.poster.hihi.core.extensions.setOnSingleClick
import com.wanted.poster.hihi.core.extensions.strings
import com.wanted.poster.hihi.core.extensions.visible
import com.wanted.poster.hihi.data.model.KillerModel
import com.wanted.poster.hihi.databinding.ActivityChooseKillerBinding
import com.wanted.poster.hihi.R

class ChooseKillerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChooseKillerBinding
    private var selectedKillerAssetPath: String? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityChooseKillerBinding.inflate(layoutInflater)

        setContentView(binding.root)
        setupActionBar()
        selectedKillerAssetPath = intent.getStringExtra(ChooseNumberActivity.EXTRA_KILLER_ASSET_PATH)


        val files = assets.list("killer_removebg") ?: emptyArray()

        val killerList = files.map { fileName ->

            KillerModel(
                name = fileName.substringBeforeLast("."),
                imageAssetPath = "killer_removebg/$fileName"
            )
        }

        var initialSelectedPosition = 0
        if (killerList.isNotEmpty()) {
            initialSelectedPosition = killerList.indexOfFirst { it.imageAssetPath == selectedKillerAssetPath }
                .takeIf { it >= 0 } ?: 0
            val initialItem = killerList[initialSelectedPosition]
            selectedKillerAssetPath = initialItem.imageAssetPath
            binding.tvNameChooseKiller.text = initialItem.name
            loadKillerPreview(initialItem.imageAssetPath)
        }

        val adapter = KillerAdapter(
            list = killerList.toMutableList(),
            initialSelectedPosition = initialSelectedPosition
        ) { killerItem ->
            selectedKillerAssetPath = killerItem.imageAssetPath
            binding.tvNameChooseKiller.text = killerItem.name
            loadKillerPreview(killerItem.imageAssetPath)
        }
        binding.rvKiller.adapter = adapter
        binding.rvKiller.scrollToPosition(initialSelectedPosition)
        binding.viewKillerScrollIndicator.attachTo(binding.rvKiller)


        binding.btnSelect.setOnClickListener {
            val intent = Intent(this, ChooseMapActivity::class.java).apply {
                putExtra(ChooseMapActivity.EXTRA_KILLER_ASSET_PATH, selectedKillerAssetPath)
            }
            startActivity(intent)
        }
    }

    private fun loadKillerPreview(assetPath: String) {
        CoroutineScope(Dispatchers.Main).launch {
            val bmp = withContext(Dispatchers.IO) {
                try { assets.open(assetPath).use { BitmapFactory.decodeStream(it) } }
                catch (_: Exception) { null }
            }
            binding.ivKillerShow.setImageBitmap(bmp)
        }
    }

    private fun setupActionBar() {
        binding.actionBar.apply {
            btnActionBarLeft.setImageResource(R.drawable.ic_back)
            btnActionBarLeft.visible()
            tvCenter.text = strings(R.string.select_killer)
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
