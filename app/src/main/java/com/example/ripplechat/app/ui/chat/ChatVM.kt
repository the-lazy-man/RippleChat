package com.example.ripplechat.app.ui.chat


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
        // 1) Observe local Room messages first
        viewModelScope.launch {
            repo.getLocalMessagesFlow(chatId).collect { local ->
                _messages.value = local
            }
        }

        // 2) Listen remote and update local
        messagesListener = repo.listenMessagesRealtime(chatId) { remoteMessages ->
            viewModelScope.launch {
                // Insert remote messages into local Room
                repo.insertLocal(remoteMessages)
            }
        }

        // 3) Listen for typing flag
        chatDocListener = repo.listenChatDoc(chatId) { doc ->
            val key = "typing_${currentUserId}"
            val otherKey = "typing_${peerUid}"
            val isOtherTyping = (doc?.get(otherKey) as? Boolean) ?: false
            _otherTyping.value = isOtherTyping
        }
    }

    fun sendMessage(text: String) {
        val chatId = currentChatId ?: return
        val sender = currentUserId ?: return
        viewModelScope.launch {
            repo.sendMessage(chatId, text, sender)
            // local insert will be handled when Firestore listener fires; but we can also optimistically insert:
            val localMsg = ChatMessage(
                messageId = java.util.UUID.randomUUID().toString(),
                chatId = chatId,
                senderId = sender,
                text = text,
                timestamp = System.currentTimeMillis()
            )
            repo.insertLocalSingle(localMsg)
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
