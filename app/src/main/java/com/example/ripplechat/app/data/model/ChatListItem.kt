package com.example.ripplechat.app.data.model

data class ChatListItem(
    val chatId: String = "",
    val peerUid: String = "",
    val peerName: String = "",
    val peerProfilePic: String? = null,
    val lastMessage: String = "",
    val lastTimestamp: Long = 0,
    val unreadCount: Int = 0,
    val isMuted: Boolean = false
)
