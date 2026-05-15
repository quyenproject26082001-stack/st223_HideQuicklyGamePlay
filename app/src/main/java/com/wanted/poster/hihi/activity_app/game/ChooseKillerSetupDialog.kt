package com.wanted.poster.hihi.activity_app.game

import android.content.Context
import android.graphics.BitmapFactory
import android.view.Gravity
import com.wanted.poster.hihi.R
import com.wanted.poster.hihi.activity_app.adapter.KillerAdapter
import com.wanted.poster.hihi.core.base.BaseDialog
import com.wanted.poster.hihi.core.extensions.setOnSingleClick
import com.wanted.poster.hihi.data.model.KillerModel
import com.wanted.poster.hihi.databinding.DialogChooseKillerSetupBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChooseKillerSetupDialog(
    context: Context,
    private val onSelect: (String) -> Unit
) : BaseDialog<DialogChooseKillerSetupBinding>(
    context,
    gravity = Gravity.CENTER,
    maxWidth = true,
    maxHeight = true
) {

    override val layoutId = R.layout.dialog_choose_killer_setup
    override val isCancelOnTouchOutside = false
    override val isCancelableByBack = true

    private var selectedKillerPath: String? = null
    private var killerList: List<KillerModel> = emptyList()

    override fun initView() {
        killerList = (context.assets.list("killer_removebg") ?: emptyArray())
            .map { KillerModel(name = it.substringBeforeLast("."), imageAssetPath = "killer_removebg/$it") }

        if (killerList.isNotEmpty()) {
            val first = killerList[0]
            selectedKillerPath = first.imageAssetPath
            binding.tvKillerName.text = first.name
            loadKillerPreview(first.imageAssetPath)
        }

        val adapter = KillerAdapter(killerList.toMutableList()) { item ->
            selectedKillerPath = item.imageAssetPath
            binding.tvKillerName.text = item.name
            loadKillerPreview(item.imageAssetPath)
        }
        binding.rvKiller.adapter = adapter
        binding.viewKillerScrollIndicator.attachTo(binding.rvKiller)
    }

    override fun initAction() {
        binding.btnCancelKiller.setOnSingleClick { dismiss() }
        binding.btnSelectKiller.setOnSingleClick {
            selectedKillerPath?.let {
                onSelect(it)
                dismiss()
            }
        }
    }

    private fun loadKillerPreview(assetPath: String) {
        CoroutineScope(Dispatchers.Main).launch {
            val bmp = withContext(Dispatchers.IO) {
                try { context.assets.open(assetPath).use { BitmapFactory.decodeStream(it) } }
                catch (_: Exception) { null }
            }
            binding.ivKillerShow.setImageBitmap(bmp)
        }
    }

    override fun onDismissListener() {}
}
