package com.example.ripplechat.app.data.local


import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class MessageEntity(
    val chatId: String,
    @PrimaryKey val messageId: String, // Firestore doc id (or local uuid)
    val senderId: String,
    val text: String,
    val timestamp: Long // epoch millis
)
