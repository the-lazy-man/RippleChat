package com.example.ripplechat.data.repository

import com.example.ripplechat.app.data.local.MessageDao
import com.example.ripplechat.app.data.local.MessageEntity
import com.example.ripplechat.app.data.model.ChatMessage
import com.example.ripplechat.app.data.model.firebase.FirebaseSource
import com.google.firebase.Timestamp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject


class ChatRepository @Inject constructor(
    private val firebase: FirebaseSource,
    private val dao: MessageDao
) {
    fun getLocalMessagesFlow(chatId: String): Flow<List<ChatMessage>> {
        return dao.getMessagesFlow(chatId).map { list ->
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
    }

    suspend fun insertLocal(messages: List<ChatMessage>) {
        withContext(Dispatchers.IO) {
            dao.insertAll(messages.map {
                MessageEntity(
                    chatId = it.chatId,
                    messageId = it.messageId,
                    senderId = it.senderId,
                    text = it.text,
                    timestamp = it.timestamp
                )
            })
        }
    }

    suspend fun insertLocalSingle(msg: ChatMessage) {
        withContext(Dispatchers.IO) {
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
    }

    suspend fun sendMessage(chatId: String, text: String, senderId: String) {
        val ts = Timestamp.now().toDate().time
        val payload = mapOf(
            "text" to text,
            "senderId" to senderId,
            "timestamp" to com.google.firebase.Timestamp.now()
        )
        firebase.sendMessage(chatId, payload)
        // Firestore will push back via snapshot listener; local insert will also happen when listener fires.
    }

    fun listenMessagesRealtime(chatId: String, onEvent: (List<ChatMessage>) -> Unit) =
        firebase.listenMessages(chatId) { docs ->
            val msgs = docs.map { map ->
                val ts = (map["timestamp"] as? com.google.firebase.Timestamp)?.toDate()?.time ?: System.currentTimeMillis()
                ChatMessage(
                    messageId = (map["id"] as? String) ?: UUID.randomUUID().toString(),
                    chatId = chatId,
                    senderId = map["senderId"] as? String ?: "",
                    text = map["text"] as? String ?: "",
                    timestamp = ts
                )
            }
            onEvent(msgs)
        }

    fun setTyping(chatId: String, uid: String, isTyping: Boolean) = firebase.setTyping(chatId, uid, isTyping)

    fun listenChatDoc(chatId: String, onDoc: (Map<String, Any>?) -> Unit) = firebase.listenChatDoc(chatId, onDoc)
}
