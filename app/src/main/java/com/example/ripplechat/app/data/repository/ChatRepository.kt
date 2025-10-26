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
import com.google.firebase.Timestamp
// Add these imports to ChatRepository.kt
import com.example.ripplechat.app.data.model.FcmRequest // Assuming FcmRequest is in this path based on Network.kt
import com.example.ripplechat.app.data.model.NotificationService // Assuming NotificationService is in this path based on Network.kt
import kotlinx.coroutines.withContext
import android.util.Log // For logging network response
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class ChatRepository @Inject constructor(
    private val firebase: FirebaseSource,
    private val dao: MessageDao,
    private val notificationService: NotificationService
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

    suspend fun getSenderName(uid: String): String = withContext(Dispatchers.IO) {
        // Assuming 'firebase' in the repository has access to Firestore
        val doc = FirebaseFirestore.getInstance().collection("users").document(uid).get().await()
        return@withContext doc.getString("name") ?: "RippleChat User"
    }
    suspend fun deleteLocal(messageId: String) = withContext(Dispatchers.IO) {
        dao.deleteMessage(messageId)
    }

    // FIX: Correctly formats payload for the fixed FirebaseSource.sendMessage
    suspend fun sendMessage(chatId: String, messageId: String, text: String, senderId: String) {
        val payload = mapOf(
            "text" to text,
            "senderId" to senderId,
            "timestamp" to Timestamp.now()
        )
        firebase.sendMessage(chatId, messageId, payload)
    }

    suspend fun editMessage(chatId: String, messageId: String, newText: String) {
        firebase.editMessage(chatId, messageId, newText)
    }

    // FIX: Calls the corrected Firebase function
    suspend fun deleteMessage(chatId: String, messageId: String) {
        firebase.deleteMessage(chatId, messageId)
    }

    // Unnecessary `deleteChatForUser` kept minimal as per request
    suspend fun deleteChatForUser(chatId: String) {
        dao.clearChat(chatId)
        // Optionally delete remote messages if desired, but typically only deletes for the viewing user.
        // firebase.deleteChatMessages(chatId)
    }

    fun listenMessagesRealtime(
        chatId: String,
        onAdded: (ChatMessage) -> Unit,
        onModified: (ChatMessage) -> Unit,
        onRemoved: (String) -> Unit
    ) = firebase.listenMessages(chatId, onAdded, onModified, onRemoved)

    /* Triggers the external server to send an FCM notification to the peer.
    * This is only called when the peer is determined to be offline.
    */
    suspend fun triggerFcmNotification(
        recipientId: String,
        senderId: String,
        messageText: String,
        chatId: String,
        senderName : String
    ) = withContext(Dispatchers.IO) {
        try {
            val request = FcmRequest(recipientId, senderId,senderName, messageText, chatId)
            val response = notificationService.triggerNotification(request)

            if (response.isSuccessful) {
                Log.d("ChatRepo", "FCM trigger successful for recipient: $recipientId")
            } else {
                Log.e("ChatRepo", "FCM trigger failed. Code: ${response.code()}, Body: ${response.errorBody()?.string()}")
            }
        } catch (e: Exception) {
            Log.e("ChatRepo", "FCM network exception: ${e.message}", e)
        }
    }

    fun setTyping(chatId: String, uid: String, isTyping: Boolean) = firebase.setTyping(chatId, uid, isTyping)

    fun listenChatDoc(chatId: String, onDoc: (Map<String, Any>?) -> Unit) = firebase.listenChatDoc(chatId, onDoc)
    fun setPresence(uid: String, online: Boolean) = firebase.setPresence(uid, online)
    fun listenPresence(peerUid: String, onChange: (Boolean, Long?) -> Unit) = firebase.listenPresence(peerUid, onChange)
}