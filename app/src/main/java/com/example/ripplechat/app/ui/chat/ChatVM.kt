package com.example.ripplechat.app.ui.chat

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ripplechat.app.data.model.ChatMessage
import com.example.ripplechat.data.repository.ChatRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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

    // 💡 NEW: Variable to hold the current user's name fetched from the database
    private var currentUserName: String = "RippleChat User"




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
                // NOTE: This assumes repo.getSenderName(uid) is added to the repository
                currentUserName = repo.getSenderName(uid)
            }
        }
        // Realtime Firestore Listener
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
        // ❌ REMOVED: Set presence false (this is now handled by MainActivity's onStop)
//        currentUserId?.let { repo.setPresence(it, false) }
        removeListeners()
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
                // This line now calls the corrected repository function
                repo.deleteMessage(chatId, messageId)

                // OPTIONAL: Small delay for UX feedback
                delay(500)
            } catch (t: Throwable) {
                Log.e("ChatViewModel", "Error deleting message: $messageId", t)
            } finally {
                _isDeletingMessage.value = false
            }
        }
    }
    // In ChatViewModel.kt
// Replace your existing sendMessage function with this:
    fun sendMessage(text: String) {
        val chatId = currentChatId ?: return
        val sender = currentUserId ?: return
        val receiver = peerUid ?: return // Get the recipient's UID

        // FIX: Ensure unique ID is used for both local and remote
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
                // FIX: Send the message using the generated ID
                repo.sendMessage(chatId, messageId, text, sender)

                // ⚡ NEW: Conditional FCM Notification Call
                val isPeerOnline = peerOnline.value
                if (!isPeerOnline) {
                    repo.triggerFcmNotification(
                        recipientId = receiver,
                        senderId = sender,
                        messageText = text,
                        senderName = currentUserName,
                        chatId = chatId // This explicitly assigns the value to the chatId parameter
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