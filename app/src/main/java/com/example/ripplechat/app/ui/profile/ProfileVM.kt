package com.example.ripplechat.app.ui.profile

import android.net.Uri
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class ProfileUser(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val photoUrl: String? = null
)

sealed class ProfileState {
    object Idle : ProfileState()
    object Loading : ProfileState()
    object Success : ProfileState()
    data class Error(val message: String) : ProfileState()
}
@HiltViewModel
class ProfileViewModel @Inject constructor() : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance().reference

    private val _user = MutableStateFlow(ProfileUser())
    val user = _user.asStateFlow()

    var updateState: ProfileState = ProfileState.Idle
        private set

    init {
        val uid = auth.currentUser?.uid
        if (uid != null) {
            db.collection("users").document(uid).addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null) return@addSnapshotListener
                _user.value = ProfileUser(
                    uid = uid,
                    name = snapshot.getString("name") ?: "",
                    email = snapshot.getString("email") ?: "",
                    photoUrl = snapshot.getString("photoUrl")
                )
            }
        }
    }

    fun updateName(newName: String) {
        val uid = auth.currentUser?.uid ?: return
        updateState = ProfileState.Loading
        db.collection("users").document(uid).update("name", newName)
            .addOnSuccessListener { updateState = ProfileState.Success }
            .addOnFailureListener { updateState = ProfileState.Error(it.localizedMessage ?: "Failed") }
    }

    fun uploadPicture(uri: Uri) {
        val uid = auth.currentUser?.uid ?: return
        updateState = ProfileState.Loading
        val ref = storage.child("profilePics/$uid.jpg")
        ref.putFile(uri).addOnSuccessListener {
            ref.downloadUrl.addOnSuccessListener { url ->
                db.collection("users").document(uid).update("photoUrl", url.toString())
                    .addOnSuccessListener { updateState = ProfileState.Success }
                    .addOnFailureListener { updateState = ProfileState.Error(it.localizedMessage ?: "Firestore update failed") }
            }
        }.addOnFailureListener {
            updateState = ProfileState.Error(it.localizedMessage ?: "Upload failed")
        }
    }
    fun logout(onLoggedOut: () -> Unit) {
        auth.signOut()  // Firebase sign out
        onLoggedOut()   // Callback to navigate back to login
    }
}
