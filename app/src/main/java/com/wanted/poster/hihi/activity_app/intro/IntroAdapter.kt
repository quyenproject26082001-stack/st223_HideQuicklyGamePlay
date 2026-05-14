package com.wanted.poster.hihi.activity_app.intro

import android.content.Context
import com.wanted.poster.hihi.core.base.BaseAdapter
import com.wanted.poster.hihi.core.extensions.loadImageGlide
import com.wanted.poster.hihi.core.extensions.select
import com.wanted.poster.hihi.core.extensions.setTextContent
import com.wanted.poster.hihi.core.extensions.strings
import com.wanted.poster.hihi.data.model.IntroModel
import com.wanted.poster.hihi.databinding.ItemIntroBinding

class IntroAdapter(val context: Context) : BaseAdapter<IntroModel, ItemIntroBinding>(
    ItemIntroBinding::inflate
) {
    override fun onBind(binding: ItemIntroBinding, item: IntroModel, position: Int) {
        binding.apply {
            loadImageGlide(root, item.image, imvImage, false)
            tvContent.text = context.strings(item.content)
            tvContent.select()
        }
    }
}