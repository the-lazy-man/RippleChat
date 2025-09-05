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

    fun init(chatId: String, peerUid: String) {
        this.currentChatId = chatId
        this.peerUid = peerUid
        Log.d("ChatViewModel", "Initializing chat with ID: $chatId, Peer UID: $peerUid") // Add this

        // 1) Observe local Room messages first
        // Observe local Room messages first (map MessageEntity -> ChatMessage)
        viewModelScope.launch {
            repo.getLocalMessagesFlow(chatId).collect { entities ->
                _messages.value = entities.map {
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

        // start remote listener (repo will persist incremental changes to Room)
        messagesListener = repo.listenMessagesRealtime(chatId)

        // listen typing/metadata
        chatDocListener = repo.listenChatDoc(chatId) { doc ->
            val otherKey = "typing_${peerUid}"
            _otherTyping.value = (doc?.get(otherKey) as? Boolean) ?: false
        }
    }

    fun sendMessage(text: String) {
        val chatId = currentChatId ?: return
        val sender = currentUserId ?: return


        // 1) generate id
        val msgId = repo.generateMessageId(chatId)

        // 2) optimistic local insert (same id)
        val localMsg = ChatMessage(
            messageId = msgId,
            chatId = chatId,
            senderId = sender,
            text = text,
            timestamp = System.currentTimeMillis()
        )

        viewModelScope.launch {
            // insert immediately so UI shows message
            repo.insertLocalSingle(localMsg)

            // 3) actually send to Firebase with same id (suspend)
            try {
                repo.sendMessageWithId(chatId, msgId, text, sender)
            } catch (t: Throwable) {
                // handle send error (e.g., mark as failed, show snackbar)
            }
            // When Firestore emits ADDED for the message, repo will insert again (REPLACE) — no duplicate.
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
    }

    override fun onCleared() {
        super.onCleared()
        removeListeners()
    }
}
