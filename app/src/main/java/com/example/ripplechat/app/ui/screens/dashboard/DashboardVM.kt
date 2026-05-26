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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashBoardVM @Inject constructor(
    private val firebase: FirebaseSource,
    private val chatRepository: ChatRepository,
    private val notificationPreferences: NotificationPreferences

) : ViewModel() {
    val mutedUsers: Flow<Set<String>> = notificationPreferences.mutedUsersFlow

    fun muteUser (userId: String) {
        viewModelScope.launch {
            notificationPreferences.addMutedUser(userId)
        }
    }
    fun unmuteUser (userId: String) {  // Renamed from removeMutedUser  for clarity
        viewModelScope.launch {
            notificationPreferences.removeMutedUser(userId)
        }
    }

    // NEW: Chat list with metadata instead of contacts
    private val _chatList = MutableStateFlow<List<ChatListItem>>(emptyList())
    val chatList = _chatList.asStateFlow()

    private val _searchResults = MutableStateFlow<List<User>>(emptyList())
    val searchResults = _searchResults.asStateFlow()

    var showToast  = MutableStateFlow<Boolean>(false)
    private var chatsReg: ListenerRegistration? = null

    // State for all users fetched when search is activated
    private val _allUsersForSearch = MutableStateFlow<List<User>>(emptyList())
    val allUsersForSearch = _allUsersForSearch.asStateFlow()

    /**
     * Removes a peer from the current user's contacts list.
     */
    fun removeContact(peerUid: String) {
        val uid = firebase.currentUserUid() ?: return
        viewModelScope.launch {
            try {
                // The FirebaseSource logic handles removing contacts and deleting the chat
                firebase.deleteContact(uid, peerUid)
            } catch (e: Exception) {
                Log.e("DashBoardVM", "Error removing contact", e)
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
            }catch (e : Exception){
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
                    lastTimestamp = (data["lastTimestamp"] as? com.google.firebase.Timestamp)?.toDate()?.time ?: 0,
                    unreadCount = (data["unreadCount"] as? Long)?.toInt() ?: 0,
                    isMuted = false // TODO: Add muted tracking later
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