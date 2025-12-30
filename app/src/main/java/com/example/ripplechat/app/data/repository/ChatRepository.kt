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
import com.example.ripplechat.app.data.model.FcmRequest
import com.example.ripplechat.app.data.model.NotificationService
import android.util.Log
import com.google.firebase.firestore.FieldValue // Added
import com.google.firebase.firestore.FirebaseFirestore // Added (already there but ensuring clarity)
import kotlinx.coroutines.tasks.await // Added (already there but ensuring clarity)

class ChatRepository @Inject constructor(
    private val firebase: FirebaseSource,
    private val dao: MessageDao,
    private val notificationService: NotificationService
) {
    // --- 1. Message Mappings (UPDATED for Media and Edited fields) ---
    fun getLocalMessagesFlow(chatId: String): Flow<List<ChatMessage>> =
        dao.getMessagesFlow(chatId).map { list ->
            list.map {
                ChatMessage(
                    messageId = it.messageId,
                    chatId = it.chatId,
                    senderId = it.senderId,
                    text = it.text,
                    timestamp = it.timestamp,
                    edited = it.edited,       // <-- NEW
                    mediaUrl = it.mediaUrl,   // <-- NEW
                    isMedia = it.isMedia,     // <-- NEW
                    mediaType = it.mediaType  // <-- NEW
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
                timestamp = msg.timestamp,
                edited = msg.edited,       // <-- NEW
                mediaUrl = msg.mediaUrl,   // <-- NEW
                isMedia = msg.isMedia,     // <-- NEW
                mediaType = msg.mediaType  // <-- NEW
            )
        )
    }

    suspend fun getSenderName(uid: String): String = withContext(Dispatchers.IO) {
        val doc = FirebaseFirestore.getInstance().collection("users").document(uid).get().await()
        return@withContext doc.getString("name") ?: "RippleChat User"
    }

    suspend fun deleteLocal(messageId: String) = withContext(Dispatchers.IO) {
        dao.deleteMessage(messageId)
    }

    // --- 2. NEW: Self-Delete Chat Logic ---

    // NEW: Function to set auto-delete time on the chat document (Called by ViewModel)
    suspend fun setChatDeletionTime(chatId: String, durationMillis: Long?) {
        firebase.setChatDeletionTime(chatId, durationMillis)
    }

    // NEW: Full chat deletion logic (Called by Worker)
    suspend fun deleteChatMessagesWithMedia(chatId: String) {
        // This calls the full cleanup function in FirebaseSource (Firestore + Cloudinary)
        firebase.deleteChatMessagesWithMedia(chatId)
        // Clear Room DB (local messages)
        dao.clearChat(chatId)
    }

    // 3. UPDATED: Sends a text message.
    suspend fun sendMessage(chatId: String, messageId: String, text: String, senderId: String) {
        val payload = mapOf(
            "text" to text,
            "senderId" to senderId,
            "timestamp" to Timestamp.now()
        )
        // FirebaseSource updates the chat document's lastMessage/lastTimestamp
        firebase.sendMessage(chatId, messageId, payload)
    }

    // NEW: Sends a media message.
    suspend fun sendMediaMessage(
        chatId: String,
        messageId: String,
        mediaUrl: String,
        text: String,
        senderId: String,
        mediaType: String
    ) {
        // We create the payload with all required fields
        val payload = mapOf<String, Any>(
            "senderId" to senderId,
            "text" to text,
            "mediaUrl" to mediaUrl,
            "isMedia" to true,
            "mediaType" to mediaType,
            "timestamp" to FieldValue.serverTimestamp() // Use server timestamp for accuracy
        )
        // FirebaseSource handles message creation AND chat doc update
        firebase.sendMessage(chatId, messageId, payload)
    }


    suspend fun editMessage(chatId: String, messageId: String, newText: String) {
        firebase.editMessage(chatId, messageId, newText)
    }

    // FIX: Calls the corrected Firebase function AND deletes locally (since listener won't remove it)
    suspend fun deleteMessage(chatId: String, messageId: String) {
        firebase.deleteMessage(chatId, messageId)
        deleteLocal(messageId) // Add local delete here as this is an explicit user action
    }

    // Unnecessary `deleteChatForUser` kept minimal as per request
    suspend fun deleteChatForUser(chatId: String) {
        dao.clearChat(chatId)
        // ... (rest of the logic)
    }

    fun listenMessagesRealtime(
        chatId: String,
        onAdded: (ChatMessage) -> Unit,
        onModified: (ChatMessage) -> Unit,
        onRemoved: (String) -> Unit
    ) = firebase.listenMessages(chatId, onAdded, onModified, onRemoved)

    /* Triggers the external server to send an FCM notification to the peer. */
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