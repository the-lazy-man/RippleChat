package com.example.ripplechat.data.repository

import com.example.ripplechat.app.data.local.MessageDao
import com.example.ripplechat.app.data.local.MessageEntity
import com.example.ripplechat.app.data.model.ChatMessage
import com.example.ripplechat.app.data.model.firebase.FirebaseSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

class ChatRepository @Inject constructor(
    private val firebase: FirebaseSource,
    private val dao: MessageDao
) {
    fun getLocalMessagesFlow(chatId: String): Flow<List<ChatMessage>> =
        dao.getMessagesFlow(chatId).map { list ->
            list.map {
                ChatMessage(
                    messageId = it.messageId,
                    chatId = it.chatId,
                    senderId = it.senderId,
                    text = it.text,
                    timestamp = it.timestamp
                )
            }
        }
    fun generateMessageId(): String {
        return firebase.generateMessageId()
    }

    suspend fun insertOrUpdate(msg: ChatMessage) = withContext(Dispatchers.IO) {
        dao.insertMessage(
            MessageEntity(
                chatId = msg.chatId,
                messageId = msg.messageId,
                senderId = msg.senderId,
                text = msg.text,
                timestamp = msg.timestamp
            )
        )
    }

    suspend fun deleteLocal(messageId: String) = withContext(Dispatchers.IO) {
        dao.deleteMessage(messageId)
    }

    suspend fun sendMessage(chatId: String, messageId: String, text: String, senderId: String) {
        val payload = mapOf(
            "text" to text,
            "senderId" to senderId,
            "timestamp" to com.google.firebase.Timestamp.now()
        )
        // FIX: The firebase sendMessage function needs to be corrected as well.
        firebase.sendMessage(chatId, messageId, payload)
    }


    fun listenMessagesRealtime(
        chatId: String,
        onAdded: (ChatMessage) -> Unit,
        onModified: (ChatMessage) -> Unit,
        onRemoved: (String) -> Unit
    ) = firebase.listenMessages(chatId, onAdded, onModified, onRemoved)

    fun setTyping(chatId: String, uid: String, isTyping: Boolean) = firebase.setTyping(chatId, uid, isTyping)

    fun listenChatDoc(chatId: String, onDoc: (Map<String, Any>?) -> Unit) = firebase.listenChatDoc(chatId, onDoc)
}
