package com.wanted.poster.hihi.activity_app.game

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
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


        val files = assets.list("killer_removebg") ?: emptyArray()

        val killerList = files.map { fileName ->

            KillerModel(
                name = fileName.substringBeforeLast("."),
                imageAssetPath = "killer_removebg/$fileName"
            )
        }

        if (killerList.isNotEmpty()) {
            val firstItem = killerList[0]
            selectedKillerAssetPath = firstItem.imageAssetPath
            binding.tvNameChooseKiller.text = firstItem.name
            assets.open(firstItem.imageAssetPath).use { inputStream ->
                val bitmap = BitmapFactory.decodeStream(inputStream)
                binding.ivKillerShow.setImageBitmap(bitmap)
            }
        }

        val adapter = KillerAdapter(killerList.toMutableList()) { killerItem ->
            selectedKillerAssetPath = killerItem.imageAssetPath
            binding.tvNameChooseKiller.text = killerItem.name
            assets.open(killerItem.imageAssetPath).use { inputStream ->
                val bitmap = BitmapFactory.decodeStream(inputStream)
                binding.ivKillerShow.setImageBitmap(bitmap)
            }
        }
        binding.rvKiller.adapter = adapter
        binding.viewKillerScrollIndicator.attachTo(binding.rvKiller)


        binding.btnSelect.setOnClickListener {
            val intent = Intent(this, ChooseMapActivity::class.java).apply {
                putExtra(ChooseMapActivity.EXTRA_KILLER_ASSET_PATH, selectedKillerAssetPath)
            }
            startActivity(intent)
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
