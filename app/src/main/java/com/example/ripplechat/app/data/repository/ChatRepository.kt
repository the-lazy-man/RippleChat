package com.example.ripplechat.data.repository

import com.example.ripplechat.app.data.local.MessageDao
import com.example.ripplechat.app.data.local.MessageEntity
import com.example.ripplechat.app.data.model.ChatMessage
import com.example.ripplechat.app.data.model.Status
import com.example.ripplechat.app.data.model.firebase.FirebaseSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
// Add these imports to ChatRepository.kt
import com.example.ripplechat.app.data.model.FcmRequest // Assuming FcmRequest is in this path based on Network.kt
import com.example.ripplechat.app.data.model.NotificationService // Assuming NotificationService is in this path based on Network.kt
import android.util.Log // For logging network response
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.Timestamp
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
                    timestamp = it.timestamp,
                    edited = it.edited,
                    mediaUrl = it.mediaUrl,
                    isMedia = it.isMedia,
                    mediaType = it.mediaType
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
                edited = msg.edited,
                mediaUrl = msg.mediaUrl,
                isMedia = msg.isMedia,
                mediaType = msg.mediaType
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
    suspend fun sendMessage(chatId: String, messageId: String, text: String, senderId: String, receiverId: String) {
        val payload = mapOf(
            "text" to text,
            "senderId" to senderId,
            "timestamp" to Timestamp.now()
        )
        firebase.sendMessage(chatId, messageId, payload)
        
        // NEW: Update chat metadata for both users
        val senderInfo = firebase.getUserInfo(senderId)
        val receiverInfo = firebase.getUserInfo(receiverId)
        
        if (senderInfo != null && receiverInfo != null) {
            firebase.updateChatMetadata(
                myUid = senderId,
                peerUid = receiverId,
                chatId = chatId,
                lastMessage = text,
                timestamp = Timestamp.now(),
                myName = senderInfo.get("name") as? String ?: "",
                peerName = receiverInfo.get("name") as? String ?: "",
                myProfilePic = senderInfo.get("profileImageUrl") as? String,
                peerProfilePic = receiverInfo.get("profileImageUrl") as? String
            )
        }
    }

    // NEW: Sends a media message (used by ChatViewModel for image/video uploads)
    suspend fun sendMediaMessage(
        chatId: String,
        messageId: String,
        mediaUrl: String,
        text: String,
        senderId: String,
        receiverId: String,
        mediaType: String
    ) {
        val payload = mapOf<String, Any>(
            "senderId" to senderId,
            "text" to text,
            "mediaUrl" to mediaUrl,
            "isMedia" to true,
            "mediaType" to mediaType,
            "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )
        firebase.sendMessage(chatId, messageId, payload)
        
        // Update chat metadata
        val senderInfo = firebase.getUserInfo(senderId)
        val receiverInfo = firebase.getUserInfo(receiverId)
        
        if (senderInfo != null && receiverInfo != null) {
            firebase.updateChatMetadata(
                myUid = senderId,
                peerUid = receiverId,
                chatId = chatId,
                lastMessage = if (text.isNotBlank()) text else "📷 Photo",
                timestamp = com.google.firebase.Timestamp.now(),
                myName = senderInfo.get("name") as? String ?: "",
                peerName = receiverInfo.get("name") as? String ?: "",
                myProfilePic = senderInfo.get("profileImageUrl") as? String,
                peerProfilePic = receiverInfo.get("profileImageUrl") as? String
            )
        }
    }

    suspend fun editMessage(chatId: String, messageId: String, newText: String) {
        firebase.editMessage(chatId, messageId, newText)
    }

    // FIX: Calls the corrected Firebase function
    suspend fun deleteMessage(chatId: String, messageId: String) {
        firebase.deleteMessage(chatId, messageId)
    }

    /**
     * Full chat wipe (called from Dashboard delete action):
     *  - Deletes all Cloudinary media assets
     *  - Deletes all Firestore messages
     *  - Resets dashboard entry (chat stays visible but shows empty)
     *  - Wipes Room (local) cache
     */
    suspend fun clearChat(myUid: String, peerUid: String) {
        firebase.clearChatOnly(myUid, peerUid) // Cloudinary + Firestore wipe
        val chatId = if (myUid < peerUid) "$myUid-$peerUid" else "$peerUid-$myUid"
        dao.clearChat(chatId)                   // Room local cache wipe
    }

    /** Called by DeleteChatMessagesWorker — wipes Firestore messages by chatId only. */
    suspend fun deleteChatMessages(chatId: String) {
        firebase.deleteChatMessages(chatId)
        dao.clearChat(chatId)
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
    
    // NEW: Chat list functions
    fun listenUserChats(myUid: String, onChange: (List<Map<String, Any>>) -> Unit) = firebase.listenUserChats(myUid, onChange)
    
    suspend fun markChatAsRead(myUid: String, chatId: String) {
        firebase.markChatAsRead(myUid, chatId)
    }

    // ========== NEW: Status repository methods ==========

    suspend fun addStatus(myUid: String, status: Status) = withContext(Dispatchers.IO) {
        firebase.addStatus(myUid, status)
    }

    fun listenStatuses(uids: List<String>, onUpdate: (Map<String, List<Status>>) -> Unit) =
        firebase.listenStatuses(uids, onUpdate)
}