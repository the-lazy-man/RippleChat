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

        // Observe local Room messages
        viewModelScope.launch {
            repo.getLocalMessagesFlow(chatId).collect { list ->
                _messages.value = list
            }
        }

        // ✅ New repo API integration
        messagesListener = repo.listenMessagesRealtime(
            chatId,
            onAdded = { msg ->
                if(currentUserId != msg.senderId) {
                    viewModelScope.launch { repo.insertOrUpdate(msg) }
                }
            },
            onModified = { msg ->
                viewModelScope.launch { repo.insertOrUpdate(msg) }
            },
            onRemoved = { msgId ->
                viewModelScope.launch { repo.deleteLocal(msgId) }
            }
        )

        // Typing indicator
        chatDocListener = repo.listenChatDoc(chatId) { doc ->
            val otherKey = "typing_${peerUid}"
            _otherTyping.value = (doc?.get(otherKey) as? Boolean) ?: false
        }
    }

    fun sendMessage(text: String) {
        val chatId = currentChatId ?: return
        val sender = currentUserId ?: return

        // optimistic local insert
        val localMsg = ChatMessage(
            chatId = chatId,
            senderId = sender,
            text = text,
            timestamp = System.currentTimeMillis()
        )

        viewModelScope.launch {
            repo.insertOrUpdate(localMsg)
            try {
                // ✅ new repo call (with same id)
                repo.sendMessage(chatId,text, sender)
            } catch (t: Throwable) {
                Log.e("ChatViewModel", "sendMessage", t)
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
    }

    override fun onCleared() {
        super.onCleared()
        removeListeners()
    }
}

