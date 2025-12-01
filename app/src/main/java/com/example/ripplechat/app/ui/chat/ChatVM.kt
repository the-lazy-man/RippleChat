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
import kotlinx.coroutines.withContext
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

    private val _isDeletingMessage = MutableStateFlow(false)
    val isDeletingMessage = _isDeletingMessage.asStateFlow()

    // Media Upload State
    private val _isUploadingMedia = MutableStateFlow(false)
    val isUploadingMedia = _isUploadingMedia.asStateFlow()

    private val _uploadError = MutableStateFlow<String?>(null)
    val uploadError = _uploadError.asStateFlow()

    private var currentUserName: String = "RippleChat User"

    private val client = OkHttpClient()
    private val SIGNATURE_URL = "https://auth-server-imagekit-for-ripplechat.onrender.com/cloudinary-auth"

    fun clearUploadError() {
        _uploadError.value = null
    }

    // --- Image Processing Helpers ---
    private fun loadBitmap(resolver: ContentResolver, uri: Uri): Bitmap {
        resolver.openInputStream(uri).use { ins ->
            return BitmapFactory.decodeStream(ins!!)
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

        messagesListener = repo.listenMessagesRealtime(
            chatId,
            onAdded = { msg -> viewModelScope.launch { repo.insertOrUpdate(msg) } },
            onModified = { msg -> viewModelScope.launch { repo.insertOrUpdate(msg) } },
            onRemoved = { msgId -> viewModelScope.launch { repo.deleteLocal(msgId) } }
        )

        chatDocListener = repo.listenChatDoc(chatId) { doc ->
            val typingMap = doc?.get("typing") as? Map<String, Any>
            _otherTyping.value = (typingMap?.get(peerUid) as? Boolean) ?: false
        }

        presenceListener = repo.listenPresence(peerUid) { online, lastSeen ->
            _peerOnline.value = online
            _peerLastSeen.value = lastSeen
        }

    }

    fun closeChat() {
        removeListeners()
    }

    // --- CORE IMAGE UPLOAD LOGIC (UPDATED SIGNATURE) ---
    fun uploadMediaFile(
        resolver: ContentResolver,
        sourceUri: Uri,
        caption: String = "" // <--- NEW ARGUMENT ADDED
    ) {
        val uid = currentUserId ?: return
        val chatId = currentChatId ?: return

        _isUploadingMedia.value = true
        val fileId = repo.generateMessageId()
        val publicId = "chat_media_${chatId}_$fileId"
        val mediaTypeString = "image"

        // Use the caption in the temporary message
        val tempLocalMsg = ChatMessage(
            messageId = fileId,
            chatId = chatId,
            senderId = uid,
            text = if (caption.isNotBlank()) caption else "Uploading Image...", // Use caption or default
            mediaUrl = sourceUri.toString(),
            isMedia = true,
            mediaType = mediaTypeString,
            timestamp = System.currentTimeMillis()
        )

        viewModelScope.launch {
            try {
                // 1. Get Signature
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

                // FIX: ONLY INSERT MESSAGE TO ROOM AFTER SUCCESSFUL SIGNATURE
                repo.insertOrUpdate(tempLocalMsg)

                val signatureJson = signatureResponse.body?.string()
                val jsonObject = JSONObject(signatureJson)
                val signature = jsonObject.getString("signature")
                val timestamp = jsonObject.getLong("timestamp")
                val uploadPreset = jsonObject.getString("upload_preset")

                // 2. Load, resize, and compress bitmap
                val bmp = withContext(Dispatchers.IO) { loadBitmap(resolver, sourceUri) }
                val maxDim = 1024
                val scaled = withContext(Dispatchers.Default) { resizeBitmap(bmp, maxDim, maxDim) }
                val uploadBytes = withContext(Dispatchers.Default) {
                    ByteArrayOutputStream().use { stream ->
                        scaled.compress(Bitmap.CompressFormat.JPEG, 85, stream)
                        stream.toByteArray()
                    }
                }

                // 3. Upload to Cloudinary
                withContext(Dispatchers.IO) {
                    MediaManager.get().upload(uploadBytes)
                        .option("resource_type", mediaTypeString)
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
                                    // Pass the caption from the signature call
                                    sendMediaMessage(fileId, url, caption, mediaTypeString)
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
                // Catches signature/network/processing failure. The message will not be in Room/Firestore.
                repo.deleteLocal(fileId)
                Log.e("ChatViewModel", "Media file upload/processing failed.", t)
                viewModelScope.launch {
                    _isUploadingMedia.value = false
                    _uploadError.value = t.localizedMessage ?: "File processing failed."
                }
            }
        }
    }

    private fun sendMediaMessage(messageId: String, url: String, text: String, mediaType: String) {
        val chatId = currentChatId ?: return
        val sender = currentUserId ?: return
        val receiver = peerUid ?: return

        val finalMsg = ChatMessage(
            messageId = messageId,
            chatId = chatId,
            senderId = sender,
            text = text, // Use the provided text (caption)
            mediaUrl = url,
            isMedia = true,
            mediaType = mediaType,
            timestamp = System.currentTimeMillis()
        )

        viewModelScope.launch {
            repo.insertOrUpdate(finalMsg)
            try {
                repo.sendMediaMessage(chatId, messageId, url, text, sender, mediaType)

                if (!peerOnline.value) {
                    repo.triggerFcmNotification(
                        recipientId = receiver,
                        senderId = sender,
                        messageText = if (text.isNotBlank()) "Image: $text" else "Image sent", // Better FCM text
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

    fun deleteExistingMessage(messageId: String) {
        val chatId = currentChatId ?: return
        _isDeletingMessage.value = true
        viewModelScope.launch {
            try {
                repo.deleteMessage(chatId, messageId)
                delay(500)
            } catch (t: Throwable) {
                Log.e("ChatViewModel", "Error deleting message: $messageId", t)
            } finally {
                _isDeletingMessage.value = false
            }
        }
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
                repo.sendMessage(chatId, messageId, text, sender)

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