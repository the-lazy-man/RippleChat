package com.example.ripplechat.app.data.model



data class ChatMessage(
    val messageId: String = "",
    val chatId: String = "",
    val senderId: String = "",
    val text: String = "",
    val timestamp: Long = 0,
    val edited: Boolean = false,
    val mediaUrl: String? = null,
    val isMedia: Boolean = false,  // Flag to indicate this is a media message
    val mediaType: String? = null
)