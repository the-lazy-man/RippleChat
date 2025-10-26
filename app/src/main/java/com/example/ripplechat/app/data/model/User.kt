package com.example.ripplechat.app.data.model

data class User(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val usernameIndex: String = "",
    val profileImageUrl: String? = null,
    val fcmToken: String? = null
)