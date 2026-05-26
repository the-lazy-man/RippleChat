package com.example.ripplechat.app.data.model

enum class MediaType {
    TEXT, IMAGE, VIDEO
}

data class Status(
    val statusId: String = "",
    val userId: String = "",
    val mediaType: MediaType = MediaType.TEXT,
    val mediaUrl: String? = null,
    val caption: String? = null,
    val backgroundColor: String? = null, // For text-only statuses
    val timestamp: Long = 0,
    val expiresAt: Long = 0,
    val duration: Int = 5000 // Viewing duration in ms (5s for images, actual length for videos)
)
