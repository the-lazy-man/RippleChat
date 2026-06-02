package com.example.ripplechat.app.data.model

data class ChatListItem(
    val chatId: String = "",
    val peerUid: String = "",
    val peerName: String = "",
    val peerProfilePic: String? = null,
    val lastMessage: String = "",
    val lastTimestamp: Long = 0,
    val unreadCount: Int = 0,
    val isMuted: Boolean = false,
    // Group fields
    val isGroup: Boolean = false,
    val groupName: String? = null,
    val groupIcon: String? = null,
    val participants: List<String> = emptyList()
)
