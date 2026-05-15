package com.wanted.poster.hihi.activity_app.game

import android.content.Context
import android.view.Gravity
import android.view.View
import com.wanted.poster.hihi.R
import com.wanted.poster.hihi.core.base.BaseDialog
import com.wanted.poster.hihi.core.extensions.setOnSingleClick
import com.wanted.poster.hihi.databinding.DialogChooseModeSetupBinding

class ChooseModeSetupDialog(
    context: Context,
    private val onModeSelected: (isManual: Boolean) -> Unit
) : BaseDialog<DialogChooseModeSetupBinding>(
    context,
    gravity = Gravity.CENTER,
    maxWidth = true
) {

    override val layoutId = R.layout.dialog_choose_mode_setup
    override val isCancelOnTouchOutside = true
    override val isCancelableByBack = true

    private var isManualSelected = true

    override fun initView() {
        binding.itemModeManual.ivModeIcon.setImageResource(R.drawable.ic_mode_manual)
        binding.itemModeManual.tvModeLabel.text = context.getString(R.string.mode_manual)
        binding.itemModeRandom.ivModeIcon.setImageResource(R.drawable.ic_mode_random)
        binding.itemModeRandom.tvModeLabel.text = context.getString(R.string.mode_random)
        updateSelection()
    }

    override fun initAction() {
        binding.containerManual.setOnSingleClick { isManualSelected = true; updateSelection() }
        binding.containerRandom.setOnSingleClick { isManualSelected = false; updateSelection() }
        binding.btnCancelMode.setOnSingleClick { dismiss() }
        binding.btnStartMode.setOnSingleClick {
            onModeSelected(isManualSelected)
            dismiss()
        }
    }

    private fun updateSelection() {
        binding.itemModeManual.viewModeSelectedOverlay.visibility =
            if (isManualSelected) View.VISIBLE else View.GONE
        binding.itemModeManual.imgModeSelected.visibility =
            if (isManualSelected) View.VISIBLE else View.GONE
        binding.itemModeRandom.viewModeSelectedOverlay.visibility =
            if (!isManualSelected) View.VISIBLE else View.GONE
        binding.itemModeRandom.imgModeSelected.visibility =
            if (!isManualSelected) View.VISIBLE else View.GONE
    }

    override fun onDismissListener() {}
}
