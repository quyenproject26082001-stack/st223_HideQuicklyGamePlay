package com.wanted.poster.hihi.activity_app.game

import android.content.Context
import android.view.Gravity
import androidx.recyclerview.widget.GridLayoutManager
import com.wanted.poster.hihi.R
import com.wanted.poster.hihi.activity_app.adapter.AvatarGridAdapter
import com.wanted.poster.hihi.core.base.BaseDialog
import com.wanted.poster.hihi.core.extensions.setOnSingleClick
import com.wanted.poster.hihi.databinding.DialogChooseAvatarBinding

class ChooseAvatarDialog(
    context: Context,
    private val onSave: (String) -> Unit
) : BaseDialog<DialogChooseAvatarBinding>(
    context,
    gravity = Gravity.CENTER,
    maxWidth = true,
    maxHeight = true
) {

    override val layoutId = R.layout.dialog_choose_avatar
    override val isCancelOnTouchOutside = false
    override val isCancelableByBack = true

    private var selectedAvatarPath: String? = null
    private var isAnyTab = true

    private val anyItems: List<AvatarGridAdapter.AvatarItem> by lazy {
        (context.assets.list("avatar") ?: emptyArray())
            .map { AvatarGridAdapter.AvatarItem.Asset("avatar/$it") }
    }

    private val flagItems: List<AvatarGridAdapter.AvatarItem> by lazy {
        listOf(
            AvatarGridAdapter.AvatarItem.Flag(R.drawable.ic_flag_english),
            AvatarGridAdapter.AvatarItem.Flag(R.drawable.ic_flag_french),
            AvatarGridAdapter.AvatarItem.Flag(R.drawable.ic_flag_germani),
            AvatarGridAdapter.AvatarItem.Flag(R.drawable.ic_flag_hindi),
            AvatarGridAdapter.AvatarItem.Flag(R.drawable.ic_flag_indo),
            AvatarGridAdapter.AvatarItem.Flag(R.drawable.ic_flag_portugeese),
            AvatarGridAdapter.AvatarItem.Flag(R.drawable.ic_flag_spanish)
        )
    }

    override fun initView() {
        showTab(isAny = true)
    }

    override fun initAction() {
        binding.tabAny.setOnSingleClick { showTab(isAny = true) }
        binding.tabCountry.setOnSingleClick { showTab(isAny = false) }
        binding.btnCancelAvatar.setOnSingleClick { dismiss() }
        binding.btnSaveAvatar.setOnSingleClick {
            selectedAvatarPath?.let {
                onSave(it)
                dismiss()
            }
        }
    }

    private fun showTab(isAny: Boolean) {
        isAnyTab = isAny
        if (isAny) {
            binding.tabAny.setBackgroundResource(R.drawable.bg_tab_selected)
            binding.tabAny.setTextColor(0xFF000000.toInt())
            binding.tabCountry.background = null
            binding.tabCountry.setTextColor(0xCCFFFFFF.toInt())
        } else {
            binding.tabCountry.setBackgroundResource(R.drawable.bg_tab_selected)
            binding.tabCountry.setTextColor(0xFF000000.toInt())
            binding.tabAny.background = null
            binding.tabAny.setTextColor(0xCCFFFFFF.toInt())
        }
        selectedAvatarPath = null
        val items = if (isAny) anyItems else flagItems
        val adapter = AvatarGridAdapter(items) { item, _ ->
            selectedAvatarPath = when (item) {
                is AvatarGridAdapter.AvatarItem.Asset -> item.path
                is AvatarGridAdapter.AvatarItem.Flag -> "flag:${item.drawableRes}"
            }
        }
        binding.rvAvatars.layoutManager = GridLayoutManager(context, 3)
        binding.rvAvatars.adapter = adapter
    }

    override fun onDismissListener() {}
}
