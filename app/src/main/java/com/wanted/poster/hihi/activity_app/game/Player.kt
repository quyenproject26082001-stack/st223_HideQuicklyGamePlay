package com.wanted.poster.hihi.activity_app.game

data class Player(
    var x: Float,       // normalized 0..1
    var y: Float,       // normalized 0..1
    val isKiller: Boolean = false,
    val name: String = ""
)
