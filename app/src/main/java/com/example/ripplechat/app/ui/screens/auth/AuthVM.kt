package com.example.ripplechat.app.data.model.ui.theme.screens.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    object Success : AuthState()
    data class Error(val message: String) : AuthState()
}

class AuthViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState = _authState.asStateFlow()

    private val _signupState = MutableStateFlow<AuthState>(AuthState.Idle)
    val signupState = _signupState.asStateFlow()

    fun login(email: String, password: String) {
        _authState.value = AuthState.Loading
        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener { _authState.value = AuthState.Success }
            .addOnFailureListener { _authState.value = AuthState.Error(it.localizedMessage ?: "Login failed") }
    }

    fun signup(name: String, email: String, password: String) {
        _signupState.value = AuthState.Loading
        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener {
                val uid = auth.currentUser?.uid ?: return@addOnSuccessListener
                val user = mapOf("name" to name, "email" to email, "photoUrl" to null)
                db.collection("users").document(uid).set(user)
                    .addOnSuccessListener { _signupState.value = AuthState.Success }
                    .addOnFailureListener { _signupState.value = AuthState.Error(it.localizedMessage ?: "Firestore save failed") }
            }
            .addOnFailureListener { _signupState.value = AuthState.Error(it.localizedMessage ?: "Signup failed") }
    }

    fun clearLoginState() { _authState.value = AuthState.Idle }
    fun clearSignupState() { _signupState.value = AuthState.Idle }
}

