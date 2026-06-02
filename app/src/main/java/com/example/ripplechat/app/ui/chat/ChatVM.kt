package com.example.ripplechat.app.ui.chat

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import com.example.ripplechat.app.data.model.ChatMessage
import com.example.ripplechat.data.repository.ChatRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import android.content.Context
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val repo: ChatRepository
) : ViewModel() {
    private var messagesListener: ListenerRegistration? = null
    private var chatDocListener: ListenerRegistration? = null

    val currentUserId: String? = FirebaseAuth.getInstance().currentUser?.uid

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages = _messages.asStateFlow()

    private val _otherTyping = MutableStateFlow(false)
    val otherTyping = _otherTyping.asStateFlow()

    private var stopTypingJob: Job? = null
    private var currentChatId: String? = null
    private var peerUid: String? = null

    private var presenceListener: ListenerRegistration? = null
    private val _peerOnline = MutableStateFlow(false)
    val peerOnline = _peerOnline.asStateFlow()
    private val _peerLastSeen = MutableStateFlow<Long?>(null)
    val peerLastSeen = _peerLastSeen.asStateFlow()

    // FIX: State for Delete Loader
    private val _isDeletingMessage = MutableStateFlow(false)
    val isDeletingMessage = _isDeletingMessage.asStateFlow()

    // Media upload state
    private val _isUploadingMedia = MutableStateFlow(false)
    val isUploadingMedia = _isUploadingMedia.asStateFlow()

    private val _uploadError = MutableStateFlow<String?>(null)
    val uploadError = _uploadError.asStateFlow()
    
    // Group / Chat Metadata
    private val _chatMetadata = MutableStateFlow<Map<String, Any>?>(null)
    val chatMetadata = _chatMetadata.asStateFlow()
    
    private val _participantNames = MutableStateFlow<Map<String, String>>(emptyMap())
    val participantNames = _participantNames.asStateFlow()

    // 💡 NEW: Variable to hold the current user's name fetched from the database
    private var currentUserName: String = "RippleChat User"

    private val client = OkHttpClient()
    private val SIGNATURE_URL = "https://auth-server-imagekit-for-ripplechat.onrender.com/cloudinary-auth"

    fun clearUploadError() { _uploadError.value = null }

    // --- Image Processing Helpers ---
    private fun loadBitmap(resolver: ContentResolver, uri: Uri): Bitmap {
        return with(resolver.openInputStream(uri)!!) {
            BitmapFactory.decodeStream(this).also { close() }
        }
    }

    fun resizeBitmap(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        val ratio = minOf(maxWidth.toFloat() / bitmap.width, maxHeight.toFloat() / bitmap.height)
        if (ratio >= 1f) return bitmap
        val width = (bitmap.width * ratio).toInt()
        val height = (bitmap.height * ratio).toInt()
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }
    // -----------------------------------------------------------------

    fun init(chatId: String, peerUid: String) {
        this.currentChatId = chatId
        this.peerUid = peerUid

        // Observe local Room messages
        viewModelScope.launch {
            repo.getLocalMessagesFlow(chatId).collect { list ->
                _messages.value = list
            }
        }

        currentUserId?.let { uid ->
            viewModelScope.launch {
                currentUserName = repo.getSenderName(uid)
            }
        }
        // Listen for chat metadata (useful for groups to get participants/name)
        val uid = currentUserId ?: ""
        if (uid.isNotBlank()) {
            chatDocListener = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("users").document(uid)
                .collection("chats").document(chatId)
                .addSnapshotListener { snapshot, e ->
                    if (e != null || snapshot == null) return@addSnapshotListener
                    val data = snapshot.data
                    _chatMetadata.value = data
                    
                    val participants = data?.get("participants") as? List<String>
                    if (participants != null && participants.isNotEmpty()) {
                        viewModelScope.launch {
                            val namesMap = mutableMapOf<String, String>()
                            participants.forEach { pUid ->
                                val name = if (pUid == currentUserId) "You" else repo.getSenderName(pUid)
                                namesMap[pUid] = name
                            }
                            _participantNames.value = namesMap
                        }
                    }
                }
        }
        
        // Listen to remote changes
        messagesListener = repo.listenMessagesRealtime(
            chatId,
            onAdded = { msg -> viewModelScope.launch { repo.insertOrUpdate(msg) } },
            onModified = { msg -> viewModelScope.launch { repo.insertOrUpdate(msg) } },
            onRemoved = { msgId -> viewModelScope.launch { repo.deleteLocal(msgId) } }
        )

        // Typing indicator and Presence
        chatDocListener = repo.listenChatDoc(chatId) { doc ->
            val typingMap = doc?.get("typing") as? Map<String, Any>
            _otherTyping.value = (typingMap?.get(peerUid) as? Boolean) ?: false
        }
       // ✅ PEER PRESENCE LISTENER IS KEPT HERE
        presenceListener = repo.listenPresence(peerUid) { online, lastSeen ->
            _peerOnline.value = online
            _peerLastSeen.value = lastSeen
        }
    }

    fun closeChat() {
        removeListeners()
    }

    // --- Update Group Icon ---
    fun updateGroupIcon(context: Context, uri: Uri, chatId: String, participants: List<String>) {
        viewModelScope.launch {
            _isUploadingMedia.value = true
            try {
                val bytes = com.example.ripplechat.app.util.CloudinaryUploadHelper.uriToImageBytes(uri = uri, contentResolver = context.contentResolver)
                if (bytes != null) {
                    val url = com.example.ripplechat.app.util.CloudinaryUploadHelper.uploadImage(bytes, "group_icons/${chatId}_${System.currentTimeMillis()}")
                    if (url != null) {
                        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                        participants.forEach { pUid ->
                            db.collection("users").document(pUid)
                                .collection("chats").document(chatId)
                                .update("groupIcon", url).await()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uploadError.value = "Failed to update group icon"
            } finally {
                _isUploadingMedia.value = false
            }
        }
    }

    // --- Remove Participant ---
    fun removeParticipant(chatId: String, participantUidToRemove: String, currentParticipants: List<String>) {
        viewModelScope.launch {
            try {
                val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                val updatedParticipants = currentParticipants.filter { it != participantUidToRemove }
                
                // Update for remaining participants
                updatedParticipants.forEach { pUid ->
                    db.collection("users").document(pUid)
                        .collection("chats").document(chatId)
                        .update("participants", updatedParticipants).await()
                }
                
                // Optionally remove the chat doc for the removed participant
                db.collection("users").document(participantUidToRemove)
                    .collection("chats").document(chatId).delete().await()
                
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Media upload functionality - Cloudinary integration
    fun uploadMediaFile(resolver: ContentResolver, sourceUri: Uri, caption: String = "") {
        val uid = currentUserId ?: return
        val chatId = currentChatId ?: return
        val receiver = peerUid ?: return

        _isUploadingMedia.value = true
        val fileId = repo.generateMessageId()
        val publicId = "chat_media_${chatId}_$fileId"
        val extension = android.webkit.MimeTypeMap.getFileExtensionFromUrl(sourceUri.toString()) ?: ""
        val mimeType = resolver.getType(sourceUri) ?: android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: ""
        val mediaTypeString = when {
            mimeType.startsWith("video/") -> "video"
            mimeType.startsWith("image/") -> "image"
            mimeType.startsWith("audio/") || sourceUri.toString().endsWith(".m4a") -> "audio"
            else -> "raw"
        }

        val tempLocalMsg = ChatMessage(
            messageId = fileId,
            chatId = chatId,
            senderId = uid,
            text = if (caption.isNotBlank()) caption else "Uploading File...",
            mediaUrl = sourceUri.toString(),
            isMedia = true,
            mediaType = mediaTypeString,
            timestamp = System.currentTimeMillis()
        )

        viewModelScope.launch {
            try {
                // 1. Get Signature from server
                val signatureResponse = withContext(Dispatchers.IO) {
                    try {
                        val jsonBody = JSONObject().apply { put("public_id", publicId) }.toString()
                        val mediaType = "application/json; charset=utf-8".toMediaType()
                        val requestBody = okhttp3.RequestBody.create(mediaType, jsonBody)
                        val request = Request.Builder().url(SIGNATURE_URL).post(requestBody).build()
                        client.newCall(request).execute()
                    } catch (e: IOException) {
                        throw Exception("Network request for signature failed", e)
                    }
                }
                if (!signatureResponse.isSuccessful) throw Exception("Failed to get signature from server: ${signatureResponse.code}")

                repo.insertOrUpdate(tempLocalMsg)

                val signatureJson = signatureResponse.body?.string()
                val jsonObject = JSONObject(signatureJson)
                val signature = jsonObject.getString("signature")
                val timestamp = jsonObject.getLong("timestamp")
                val uploadPreset = jsonObject.getString("upload_preset")

                // 2. Read raw bytes directly to support GIFs, Videos, and Docs without compression corruption
                val uploadBytes = withContext(Dispatchers.IO) {
                    resolver.openInputStream(sourceUri)?.readBytes() ?: ByteArray(0)
                }

                // 3. Upload to Cloudinary
                withContext(Dispatchers.IO) {
                    val resourceTypeOption = when (mediaTypeString) {
                        "raw" -> "raw"
                        "audio" -> "video" // Cloudinary treats audio as video
                        else -> mediaTypeString
                    }
                    MediaManager.get().upload(uploadBytes)
                        .option("resource_type", resourceTypeOption)
                        .option("public_id", publicId)
                        .option("signature", signature)
                        .option("timestamp", timestamp)
                        .option("upload_preset", uploadPreset)
                        .callback(object : UploadCallback {
                            override fun onStart(requestId: String?) {}
                            override fun onProgress(requestId: String?, bytes: Long, totalBytes: Long) {}

                            override fun onSuccess(requestId: String?, resultData: MutableMap<Any?, Any?>?) {
                                val url = resultData?.get("secure_url") as? String
                                if (url != null) {
                                    sendMediaMessage(fileId, url, caption, mediaTypeString, receiver)
                                } else {
                                    Log.e("ChatViewModel", "Upload succeeded but no URL returned.")
                                    viewModelScope.launch {
                                        repo.deleteLocal(fileId)
                                        _isUploadingMedia.value = false
                                        _uploadError.value = "Upload succeeded but no media URL was returned."
                                    }
                                }
                            }

                            override fun onError(requestId: String?, error: ErrorInfo?) {
                                Log.e("ChatViewModel", "Cloudinary upload failed: ${error?.description}")
                                viewModelScope.launch {
                                    repo.deleteLocal(fileId)
                                    _isUploadingMedia.value = false
                                    _uploadError.value = error?.description ?: "Media upload failed due to network or server error."
                                }
                            }
                            override fun onReschedule(requestId: String?, error: ErrorInfo?) {
                                viewModelScope.launch {
                                    _isUploadingMedia.value = false
                                    _uploadError.value = "Upload was rescheduled. Check connection."
                                }
                            }
                        })
                        .dispatch()
                }
            } catch (t: Throwable) {
                repo.deleteLocal(fileId)
                Log.e("ChatViewModel", "Media file upload/processing failed.", t)
                viewModelScope.launch {
                    _isUploadingMedia.value = false
                    _uploadError.value = t.localizedMessage ?: "File processing failed."
                }
            }
        }
    }

    private fun sendMediaMessage(messageId: String, url: String, text: String, mediaType: String, receiver: String) {
        val chatId = currentChatId ?: return
        val sender = currentUserId ?: return

        val finalMsg = ChatMessage(
            messageId = messageId,
            chatId = chatId,
            senderId = sender,
            text = text,
            mediaUrl = url,
            isMedia = true,
            mediaType = mediaType,
            timestamp = System.currentTimeMillis()
        )

        viewModelScope.launch {
            repo.insertOrUpdate(finalMsg)
            try {
                repo.sendMediaMessage(chatId, messageId, url, text, sender, receiver, mediaType)

                // FCM notification if peer is offline
                val isPeerOnline = peerOnline.value
                if (!isPeerOnline) {
                    repo.triggerFcmNotification(
                        recipientId = receiver,
                        senderId = sender,
                        messageText = if (text.isNotBlank()) text else "\ud83d\udcf7 Photo",
                        senderName = currentUserName,
                        chatId = chatId
                    )
                }

            } catch (t: Throwable) {
                Log.e("ChatViewModel", "sendMediaMessage failed", t)
            } finally {
                _isUploadingMedia.value = false
            }
        }
    }

    fun editExistingMessage(messageId: String, newText: String) {
        val chatId = currentChatId ?: return
        viewModelScope.launch {
            try { repo.editMessage(chatId, messageId, newText) } catch (t: Throwable) {
                Log.e("ChatViewModel", "Error editing message: $messageId", t)
            }
        }
    }

    // FIX: Implemented Delete Loader
    fun deleteExistingMessage(messageId: String) {
        val chatId = currentChatId ?: return
        _isDeletingMessage.value = true
        viewModelScope.launch {
            try {
                repo.deleteMessage(chatId, messageId)
                delay(500) // Small delay for UX feedback
            } catch (t: Throwable) {
                Log.e("ChatViewModel", "Error deleting message: $messageId", t)
            } finally {
                _isDeletingMessage.value = false
            }
        }
    }

    fun sendLocationMessage(lat: Double, lng: Double) {
        val receiver = peerUid ?: return
        val messageId = repo.generateMessageId()
        sendMediaMessage(
            messageId = messageId,
            url = "$lat,$lng",
            text = "Shared Location",
            mediaType = "location",
            receiver = receiver
        )
    }

    fun sendMessage(text: String) {
        val chatId = currentChatId ?: return
        val sender = currentUserId ?: return
        val receiver = peerUid ?: return

        val messageId = repo.generateMessageId()

        val localMsg = ChatMessage(
            messageId = messageId,
            chatId = chatId,
            senderId = sender,
            text = text,
            timestamp = System.currentTimeMillis()
        )

        viewModelScope.launch {
            repo.insertOrUpdate(localMsg)
            try {
                repo.sendMessage(chatId, messageId, text, sender, receiver)

                // Conditional FCM Notification
          val isPeerOnline = peerOnline.value
                if (!isPeerOnline) {
                    repo.triggerFcmNotification(
                        recipientId = receiver,
                        senderId = sender,
                        messageText = text,
                        senderName = currentUserName,
                        chatId = chatId
                    )
                }
                Log.d("CurrentUser","$currentUserName")

            } catch (t: Throwable) {
                Log.e("ChatViewModel", "sendMessage failed", t)
            }
        }
    }
    
    fun updateTyping(isTyping: Boolean) {
        val chatId = currentChatId ?: return
        val uid = currentUserId ?: return
        repo.setTyping(chatId, uid, isTyping)
    }

    fun scheduleStopTyping(delayMs: Long = 1200L) {
        stopTypingJob?.cancel()
        stopTypingJob = viewModelScope.launch {
            delay(delayMs)
            updateTyping(false)
        }
    }

    fun removeListeners() {
        messagesListener?.remove()
        chatDocListener?.remove()
        presenceListener?.remove()
    }

    override fun onCleared() {
        super.onCleared()
        removeListeners()
    }
}