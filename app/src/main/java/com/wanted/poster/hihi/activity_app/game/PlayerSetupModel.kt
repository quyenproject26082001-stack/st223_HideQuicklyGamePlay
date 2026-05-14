package com.wanted.poster.hihi.activity_app.game

data class PlayerSetupModel(
    val id: Int,
    var name: String,
    var avatarPath: String? = null  // null = no avatar; "avatar/1.png" = asset; "flag:2131230720" = drawable resId
)
