package com.wanted.poster.hihi.data.model.custom

import com.wanted.poster.hihi.data.model.custom.ColorModel

data class LayerModel(
    val image: String,
    val isMoreColors: Boolean = false,
    var listColor: ArrayList<ColorModel> = arrayListOf()
)