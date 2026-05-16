package com.wanted.poster.hihi.activity_app.game

import android.content.Context
import android.view.Gravity
import androidx.recyclerview.widget.GridLayoutManager
import com.wanted.poster.hihi.R
import com.wanted.poster.hihi.activity_app.adapter.MapOptionAdapter
import com.wanted.poster.hihi.core.base.BaseDialog
import com.wanted.poster.hihi.core.extensions.setOnSingleClick
import com.wanted.poster.hihi.data.model.MapOption
import com.wanted.poster.hihi.databinding.DialogChooseMapSetupBinding

class ChooseMapSetupDialog(
    context: Context,
    private val currentSelection: MapOption?,
    private val onSelect: (MapOption) -> Unit
) : BaseDialog<DialogChooseMapSetupBinding>(
    context,
    gravity = Gravity.CENTER,
    maxWidth = true,
    maxHeight = true
) {

    override val layoutId = R.layout.dialog_choose_map_setup
    override val isCancelOnTouchOutside = false
    override val isCancelableByBack = true

    private var selectedOption: MapOption? = null

    private val mapOptions: List<MapOption> by lazy {
        (1..47).map { i -> MapOption(mapIndex = i, label = "Map $i") }
    }

    override fun initView() {
        val initialSelectedPosition = mapOptions.indexOfFirst { option ->
            option.mapIndex == currentSelection?.mapIndex
        }.takeIf { it >= 0 } ?: 0

        selectedOption = mapOptions.getOrNull(initialSelectedPosition)

        val adapter = MapOptionAdapter(
            ctx = context,
            items = mapOptions,
            onSelected = { option ->
            selectedOption = option
            },
            initialSelectedPosition = initialSelectedPosition
        )
        binding.rvMaps.layoutManager = GridLayoutManager(context, 2)
        binding.rvMaps.adapter = adapter
        binding.rvMaps.scrollToPosition(initialSelectedPosition)
    }

    override fun initAction() {
        binding.btnCancelMap.setOnSingleClick { dismiss() }
        binding.btnSelectMap.setOnSingleClick {
            selectedOption?.let {
                onSelect(it)
                dismiss()
            }
        }
    }

    override fun onDismissListener() {}
}
