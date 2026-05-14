package com.wanted.poster.hihi.core.utils

import androidx.lifecycle.MutableLiveData
import com.wanted.poster.hihi.R
import com.wanted.poster.hihi.core.custom.layout.LayoutPresets
import com.wanted.poster.hihi.data.model.IntroModel
import com.wanted.poster.hihi.data.model.LanguageModel
import com.wanted.poster.hihi.data.model.custom.CustomizeModel
import com.facebook.shimmer.Shimmer
import com.wanted.poster.hihi.core.utils.key.NavigationLayerKey
import com.wanted.poster.hihi.data.model.custom.NavigationModel

object DataLocal {
    val shimmer =
        Shimmer.AlphaHighlightBuilder().setDuration(1800).setBaseAlpha(0.7f).setHighlightAlpha(0.6f)
            .setDirection(Shimmer.Direction.LEFT_TO_RIGHT).setAutoStart(true).build()

    var lastClickTime = 0L
    var currentDate = ""
    var isConnectInternet = MutableLiveData<Boolean>()
    var isFailBaseURL = false
    var isCallDataAlready = false

    fun getLanguageList(): ArrayList<LanguageModel> {
        return arrayListOf(
            LanguageModel("hi", "\u00a0Hindi", R.drawable.ic_flag_hindi),
            LanguageModel("es", "\u00a0Spanish", R.drawable.ic_flag_spanish),
            LanguageModel("fr", "\u00a0French", R.drawable.ic_flag_french),
            LanguageModel("en", "\u00a0English", R.drawable.ic_flag_english),
            LanguageModel("pt", "\u00a0Portuguese", R.drawable.ic_flag_portugeese),
            LanguageModel("in", "\u00a0Indonesian", R.drawable.ic_flag_indo),
            LanguageModel("de", "\u00a0German", R.drawable.ic_flag_germani),
        )
    }

    val itemIntroList = listOf(
        IntroModel(R.drawable.img_intro_1, R.string.title_1),
        IntroModel(R.drawable.img_intro_2, R.string.title_2),
        IntroModel(R.drawable.img_intro_3, R.string.title_3)
    )
}