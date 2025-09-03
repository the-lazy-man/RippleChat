package com.example.ripplechat.app.data.local


import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val chatId: String,
    val messageId: String, // Firestore doc id (or local uuid)
    val senderId: String,
    val text: String,
    val timestamp: Long // epoch millis
)
