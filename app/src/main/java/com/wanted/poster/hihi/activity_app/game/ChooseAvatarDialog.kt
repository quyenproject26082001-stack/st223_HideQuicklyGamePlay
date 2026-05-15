package com.wanted.poster.hihi.activity_app.game

import android.content.Context
import android.graphics.Color
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
            "us", "gb", "fr", "de", "it", "es", "pt", "vn", "cn", "jp",
            "kr", "in", "br", "ru", "au", "ca", "mx", "nl", "tr", "sa",
            "ar", "th", "id", "ph", "my", "sg", "eg", "ng", "za", "pk",
            "bd", "ir", "iq", "ae", "il", "pl", "se", "no", "dk", "fi",
            "ch", "at", "be", "cz", "hu", "ro", "gr", "ua", "nz", "cl",
            "co", "pe", "ve", "ec", "bo", "uy", "py", "kz", "uz", "mm",
            "kh", "la", "np", "lk", "tw", "hk"
        ).map { AvatarGridAdapter.AvatarItem.Asset("flags/$it.png") }
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
        val strokePx = 2f * context.resources.displayMetrics.density
        val shadowColor = 0x80000000.toInt()
        if (isAny) {
            binding.bgSelected.setBackgroundResource(R.drawable.bg_choose_avatar_selected_any)
            binding.tabAny.setTextColor(Color.BLACK)
            binding.tabAny.setDoubleStroke(Color.WHITE, strokePx, Color.TRANSPARENT, 0f)
            binding.tabAny.setShadowLayer(0f, 0f, 6f, shadowColor)
            binding.tabCountry.setTextColor(Color.WHITE)
            binding.tabCountry.setDoubleStroke(Color.BLACK, strokePx, Color.TRANSPARENT, 0f)
            binding.tabCountry.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
        } else {
            binding.bgSelected.setBackgroundResource(R.drawable.bg_choose_avatar_selected_country)
            binding.tabCountry.setTextColor(Color.BLACK)
            binding.tabCountry.setDoubleStroke(Color.WHITE, strokePx, Color.TRANSPARENT, 0f)
            binding.tabCountry.setShadowLayer(0f, 0f, 6f, shadowColor)
            binding.tabAny.setTextColor(Color.WHITE)
            binding.tabAny.setDoubleStroke(Color.BLACK, strokePx, Color.TRANSPARENT, 0f)
            binding.tabAny.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
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
