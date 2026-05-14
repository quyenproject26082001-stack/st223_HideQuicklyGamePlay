package com.wanted.poster.hihi.activity_app.game

import android.content.Context
import android.view.Gravity
import com.wanted.poster.hihi.R
import com.wanted.poster.hihi.core.base.BaseDialog
import com.wanted.poster.hihi.core.extensions.setOnSingleClick
import com.wanted.poster.hihi.databinding.DialogRenamePlayerBinding

class RenamePlayerDialog(
    context: Context,
    private val currentName: String,
    private val onSave: (String) -> Unit
) : BaseDialog<DialogRenamePlayerBinding>(context, gravity = Gravity.CENTER, maxWidth = true) {

    override val layoutId = R.layout.dialog_rename_player
    override val isCancelOnTouchOutside = true
    override val isCancelableByBack = true

    override fun initView() {
        binding.etPlayerName.setText(currentName)
        binding.etPlayerName.setSelection(currentName.length)
    }

    override fun initAction() {
        binding.btnCancelRename.setOnSingleClick { dismiss() }
        binding.btnSaveRename.setOnSingleClick {
            val name = binding.etPlayerName.text.toString().trim()
            if (name.isNotEmpty()) {
                onSave(name)
                dismiss()
            }
        }
    }

    override fun onDismissListener() {}
}
