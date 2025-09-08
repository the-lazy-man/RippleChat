package com.example.ripplechat.app.ui.auth

import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    object Success : AuthState()
    data class Error(val message: String) : AuthState()
}
@HiltViewModel
class AuthViewModel@Inject constructor(private val auth: FirebaseAuth, private val db: FirebaseFirestore) : ViewModel() {
//    private val auth = FirebaseAuth.getInstance()
//    private val db = FirebaseFirestore.getInstance()

    private val _loginState = MutableStateFlow<AuthState>(AuthState.Idle)
    val loginState = _loginState.asStateFlow()

    private val _signupState = MutableStateFlow<AuthState>(AuthState.Idle)
    val signupState = _signupState.asStateFlow()

    fun login(email: String, password: String) {
        _loginState.value = AuthState.Loading
        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener { _loginState.value = AuthState.Success }
            .addOnFailureListener { _loginState.value = AuthState.Error(it.localizedMessage ?: "Login failed") }
    }

    fun signup(name: String, email: String, password: String) {
        _signupState.value = AuthState.Loading
        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener {
                val uid = auth.currentUser?.uid ?: return@addOnSuccessListener
                val user = mapOf(
                    "uid" to uid,
                    "name" to name,
                    "email" to email,
                    "photoUrl" to null
                )
                db.collection("users").document(uid).set(user)
                    .addOnSuccessListener { _signupState.value = AuthState.Success }
                    .addOnFailureListener { _signupState.value = AuthState.Error(it.localizedMessage ?: "Firestore save failed") }
            }
            .addOnFailureListener { _signupState.value = AuthState.Error(it.localizedMessage ?: "Signup failed") }
    }

    fun clearLoginState() { _loginState.value = AuthState.Idle }
    fun clearSignupState() { _signupState.value = AuthState.Idle }
}
