package com.example.ripplechat.app.data.model.ui.theme.screens.home


import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ripplechat.app.data.local.NotificationPreferences
import com.example.ripplechat.app.data.model.ChatListItem
import com.example.ripplechat.app.data.model.User
import com.example.ripplechat.app.data.model.firebase.FirebaseSource
import com.example.ripplechat.data.repository.ChatRepository
import com.google.firebase.firestore.ListenerRegistration
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashBoardVM @Inject constructor(
    private val firebase: FirebaseSource,
    private val chatRepository: ChatRepository,
    private val notificationPreferences: NotificationPreferences

) : ViewModel() {
    val mutedUsers: StateFlow<Set<String>> = notificationPreferences.mutedUsersFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptySet()
    )

    fun muteUser(userId: String) {
        viewModelScope.launch {
            notificationPreferences.addMutedUser(userId)
        }
    }

    fun unmuteUser(userId: String) {  // Renamed from removeMutedUser  for clarity
        viewModelScope.launch {
            notificationPreferences.removeMutedUser(userId)
        }
    }

    // NEW: Chat list with metadata instead of contacts
    private val _chatList = MutableStateFlow<List<ChatListItem>>(emptyList())
    val chatList = _chatList.asStateFlow()

    private val _searchResults = MutableStateFlow<List<User>>(emptyList())
    val searchResults = _searchResults.asStateFlow()

    var showToast = MutableStateFlow<Boolean>(false)
    private var chatsReg: ListenerRegistration? = null

    // State for all users fetched when search is activated
    private val _allUsersForSearch = MutableStateFlow<List<User>>(emptyList())
    val allUsersForSearch = _allUsersForSearch.asStateFlow()

    /**
     * Deletes chat from dashboard + wipes all messages/media.
     * Does NOT remove the user from contacts — they can still be found via search.
     */
    fun removeContact(peerUid: String) {
        val uid = firebase.currentUserUid() ?: return
        viewModelScope.launch {
            try {
                // 1. Wipe Cloudinary media + Firestore messages + Room cache
                chatRepository.clearChat(uid, peerUid)
                // 2. Remove dashboard entry so chat disappears from list
                firebase.removeChatFromDashboard(uid, peerUid)
            } catch (e: Exception) {
                Log.e("DashBoardVM", "Error clearing chat", e)
            }
        }
    }

    /**
     * Fetches the entire list of users (excluding the current user) for the search/selection view.
     * This replaces the two conflicting fetchAllUsers implementations.
     */
    fun fetchAllUsers() {
        viewModelScope.launch {
            try {
                val pairs = firebase.getAllUsers()
                _allUsersForSearch.value = pairs.map { (id, data) ->
                    User(
                        uid = id,
                        name = data["name"] as? String ?: "",
                        email = data["email"] as? String ?: "",
                        profileImageUrl = data["profileImageUrl"] as? String
                    )
                }.filter { it.uid != firebase.currentUserUid() } // Exclude current user
            } catch (e: Exception) {
                Log.e("DashBoardVM", "Error fetching all users", e)
            }
        }
    }

    /**
     * Clears the list of all users used for the search/selection view.
     */
    fun clearAllUsersForSearch() {
        _allUsersForSearch.value = emptyList()
    }

    /**
     * Searches users by name.
     */
    fun search(query: String) {
        val uid = firebase.currentUserUid() ?: return
        viewModelScope.launch {
            val pairs = firebase.searchUsersByName(query, uid)
            try {
                _searchResults.value = pairs.map { (id, data) ->
                    User(
                        uid = id,
                        name = data["name"] as? String ?: "",
                        email = data["email"] as? String ?: "",
                        profileImageUrl = data["profileImageUrl"] as? String
                    )
                }
            } catch (e: Exception) {
                _searchResults.value = emptyList()
                showToast.value = true
                Log.e("DashBoardVM", "Error searching users: ${e.message}")
            }
        }
    }

    /**
     * Initializes the ViewModel by setting up the real-time listener for chat list.
     */
    init {
        val uid = firebase.currentUserUid() ?: ""
        chatsReg = chatRepository.listenUserChats(uid) { chatDocs ->
            _chatList.value = chatDocs.map { data ->
                ChatListItem(
                    chatId = data["chatId"] as? String ?: "",
                    peerUid = data["peerUid"] as? String ?: "",
                    peerName = data["peerName"] as? String ?: "Unknown",
                    peerProfilePic = data["peerProfilePic"] as? String,
                    lastMessage = data["lastMessage"] as? String ?: "",
                    lastTimestamp = (data["lastTimestamp"] as? com.google.firebase.Timestamp)?.toDate()?.time
                        ?: 0,
                    unreadCount = (data["unreadCount"] as? Long)?.toInt() ?: 0,
                    isMuted = false, // TODO: Add muted tracking later
                    isGroup = data["isGroup"] as? Boolean ?: false,
                    groupName = data["groupName"] as? String,
                    groupIcon = data["groupIcon"] as? String,
                    participants = (data["participants"] as? List<String>) ?: emptyList()
                )
            }
        }
    }

    /**
     * Clears the search results state.
     */
    fun clearSearch() {
        _searchResults.value = emptyList()
    }

    /**
     * Adds a peer to the current user's contacts list.
     */
    fun addContact(peerUid: String) {
        val uid = firebase.currentUserUid() ?: return
        viewModelScope.launch { firebase.addContact(uid, peerUid) }
    }

    /**
     * Mark a chat as read when user opens it.
     */
    fun markChatAsRead(chatId: String) {
        val uid = firebase.currentUserUid() ?: return
        viewModelScope.launch {
            chatRepository.markChatAsRead(uid, chatId)
        }
    }

    override fun onCleared() {
        super.onCleared()
        chatsReg?.remove()
    }
}