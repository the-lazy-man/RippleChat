package com.example.ripplechat.data.repository

import com.example.ripplechat.app.data.local.MessageDao
import com.example.ripplechat.app.data.local.MessageEntity
import com.example.ripplechat.app.data.model.ChatMessage
import com.example.ripplechat.app.data.model.firebase.FirebaseSource
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

class ChatRepository @Inject constructor(
    private val firebase: FirebaseSource,
    private val dao: MessageDao
) {
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun getLocalMessagesFlow(chatId: String) = dao.getMessagesFlow(chatId) // returns Flow<List<MessageEntity>>

    suspend fun insertLocalSingle(msg: ChatMessage) {
        dao.insertMessage(
            MessageEntity(
                messageId = msg.messageId,
                chatId = msg.chatId,
                senderId = msg.senderId,
                text = msg.text,
                timestamp = msg.timestamp
            )
        )
    }

    suspend fun deleteLocal(messageId: String) {
        dao.deleteMessage(messageId)
    }

    // generate id for optimistic inserts
    fun generateMessageId(chatId: String): String = firebase.generateMessageId(chatId)

    // send (suspending) with the same id
    suspend fun sendMessageWithId(chatId: String, messageId: String, text: String, senderId: String) {
        val payload = mapOf(
            "text" to text,
            "senderId" to senderId,
            "timestamp" to com.google.firebase.Timestamp.now()
        )
        firebase.sendMessageWithId(chatId, messageId, payload)
    }

    // Listen to firebase and persist changes to Room (incremental)
    fun listenMessagesRealtime(chatId: String): ListenerRegistration {
        return firebase.listenMessages(chatId) { change, msg ->
            when (change.type) {
                DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> {
                    ioScope.launch {
                        dao.insertMessage(
                            MessageEntity(
                                messageId = msg.messageId,
                                chatId = msg.chatId,
                                senderId = msg.senderId,
                                text = msg.text,
                                timestamp = msg.timestamp
                            )
                        )
                    }
                }
                DocumentChange.Type.REMOVED -> {
                    ioScope.launch { dao.deleteMessage(msg.messageId) }
                }
            }
        }
    }

    fun setTyping(chatId: String, uid: String, isTyping: Boolean) = firebase.setTyping(chatId, uid, isTyping)
    fun listenChatDoc(chatId: String, onDoc: (Map<String, Any>?) -> Unit) = firebase.listenChatDoc(chatId, onDoc)
}
