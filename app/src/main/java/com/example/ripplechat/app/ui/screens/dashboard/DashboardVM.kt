package com.example.ripplechat.app.data.model.ui.theme.screens.home


import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ripplechat.app.data.model.User
import com.example.ripplechat.app.data.model.firebase.FirebaseSource
import com.example.ripplechat.app.data.repository.UserRepository
import com.google.firebase.firestore.ListenerRegistration
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashBoardVM @Inject constructor(
    private val firebase: FirebaseSource
) : ViewModel() {

    private val _contacts = MutableStateFlow<Set<String>>(emptySet())
    val contacts = _contacts.asStateFlow()

    private val _users = MutableStateFlow<List<User>>(emptyList())
    val users = _users.asStateFlow()

    private val _searchResults = MutableStateFlow<List<User>>(emptyList())
    val searchResults = _searchResults.asStateFlow()

    private var contactsReg: ListenerRegistration? = null

    init {
        val uid = firebase.currentUserUid() ?: ""
        contactsReg = firebase.listenContacts(uid) { ids ->
            _contacts.value = ids
            viewModelScope.launch {
                val pairs = firebase.getUsersByIds(ids)
                _users.value = pairs.map { (id, data) ->
                    User(
                        uid = id,
                        name = data["name"] as? String ?: "",
                        email = data["email"] as? String ?: "",
                        profileImageUrl = data["photoUrl"] as? String
                    )
                }.sortedBy { it.name.lowercase() }
            }
        }
    }

    fun search(query: String) {
        val uid = firebase.currentUserUid() ?: return
        viewModelScope.launch {
            val pairs = firebase.searchUsersByName(query, uid)
            _searchResults.value = pairs.map { (id, data) ->
                User(
                    uid = id,
                    name = data["name"] as? String ?: "",
                    email = data["email"] as? String ?: "",
                    profileImageUrl = data["photoUrl"] as? String
                )
            }
        }
    }

    fun clearSearch() {
        _searchResults.value = emptyList()
    }

    fun addContact(peerUid: String) {
        val uid = firebase.currentUserUid() ?: return
        viewModelScope.launch { firebase.addContact(uid, peerUid) }
    }

    override fun onCleared() {
        super.onCleared()
        contactsReg?.remove()
    }
}


