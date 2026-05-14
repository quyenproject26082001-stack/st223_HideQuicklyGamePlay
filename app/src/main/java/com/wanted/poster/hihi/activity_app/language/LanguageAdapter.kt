package com.wanted.poster.hihi.activity_app.language

import android.annotation.SuppressLint
import android.content.Context
import com.wanted.poster.hihi.R
import com.wanted.poster.hihi.core.base.BaseAdapter
import com.wanted.poster.hihi.core.extensions.gone
import com.wanted.poster.hihi.core.extensions.loadImageGlide
import com.wanted.poster.hihi.core.extensions.setOnSingleClick
import com.wanted.poster.hihi.core.extensions.visible
import com.wanted.poster.hihi.data.model.LanguageModel
import com.wanted.poster.hihi.databinding.ItemLanguageBinding

class LanguageAdapter(val context: Context) : BaseAdapter<LanguageModel, ItemLanguageBinding>(
    ItemLanguageBinding::inflate
) {
    var onItemClick: ((String) -> Unit) = {}
    override fun onBind(
        binding: ItemLanguageBinding, item: LanguageModel, position: Int
    ) {
        binding.apply {
            loadImageGlide(root, item.flag, imvFlag, false)
            tvLang.text = item.name

            if (item.activate) {
                //loadImageGlide(root, R.drawable.ic_tick_lang, btnRadio, false)
                imvFocus.setBackgroundResource(R.drawable.frame_language_slt)
            } else {
                //loadImageGlide(root, R.drawable.ic_not_tick_lang, btnRadio, false)
                imvFocus.setBackgroundResource(R.drawable.frame_lang_uslt)
            }

            root.setOnSingleClick {
                onItemClick.invoke(item.code)
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun submitItem(position: Int) {
        items.forEach { it.activate = false }
        items[position].activate = true
        notifyDataSetChanged()
    }
}