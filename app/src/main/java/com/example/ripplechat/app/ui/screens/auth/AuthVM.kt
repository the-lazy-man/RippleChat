package com.example.ripplechat.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ripplechat.app.data.model.firebase.FirebaseSource
import com.example.ripplechat.app.local.AuthPreferences
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    object Success : AuthState()
    data class Error(val message: String) : AuthState()
}
@HiltViewModel
class AuthViewModel@Inject constructor(
    private val authPrefs: AuthPreferences,
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
    private val firebase: FirebaseSource
) : ViewModel() {

    private val _loginState = MutableStateFlow<AuthState>(AuthState.Idle)
    val loginState = _loginState.asStateFlow()

    private val _signupState = MutableStateFlow<AuthState>(AuthState.Idle)
    val signupState = _signupState.asStateFlow()

    fun login(username: String, password: String) {
        if (username.isBlank()) {
            _loginState.value = AuthState.Error("Username cannot be empty")
            return
        }
        if (password.isBlank()) {
            _loginState.value = AuthState.Error("Password cannot be empty")
            return
        }

        _loginState.value = AuthState.Loading

        viewModelScope.launch {
            try {
                // Search by name
                val byName = db.collection("users")
                    .whereEqualTo("name", username)
                    .get()
                    .await()

                // Search by email
                val byEmail = db.collection("users")
                    .whereEqualTo("email", username)
                    .get()
                    .await()

                val snap = when {
                    !byName.isEmpty -> byName
                    !byEmail.isEmpty -> byEmail
                    else -> {
                        _loginState.value = AuthState.Error("User not found")
                        return@launch
                    }
                }

                val email = snap.documents.first().getString("email")!!

                // Login with email
                try {
                    auth.signInWithEmailAndPassword(email, password).await()

                    _loginState.value = AuthState.Success
                    firebase.saveUserToken(firebase.currentUserUid()!!)

                } catch (authError: Exception) {
                    _loginState.value =
                        AuthState.Error(authError.localizedMessage ?: "Login failed")
                }

            } catch (e: Exception) {
                _loginState.value = AuthState.Error(e.localizedMessage ?: "Login failed")
            }
        }
    }


    fun signup(name: String, email: String, password: String) {
        // Input validation
        val validationError = validateSignupInput(name, email, password)
        if (validationError != null) {
            _signupState.value = AuthState.Error(validationError)
            return
        }

        _signupState.value = AuthState.Loading

        viewModelScope.launch {
            try {
                // Check if username already exists
                val existingUser = db.collection("users")
                    .whereEqualTo("name", name)
                    .get()
                    .await()

                if (!existingUser.isEmpty) {
                    _signupState.value = AuthState.Error("Username already exists")
                    return@launch
                }

                // Create user with Firebase Auth
                auth.createUserWithEmailAndPassword(email, password)
                    .addOnSuccessListener {
                        val uid = auth.currentUser?.uid ?: return@addOnSuccessListener
                        val user = mapOf(
                            "uid" to uid,
                            "name" to name,
                            "email" to email,
                            "usernameIndex" to name.lowercase(),
                            "profileImageUrl" to null
                        )
                        db.collection("users").document(uid).set(user)
                            .addOnSuccessListener {
                                _signupState.value = AuthState.Success
                                viewModelScope.launch {
                                    authPrefs.save(name, password) // ✅ Save username now
                                }
                                firebase.saveUserToken(firebase.currentUserUid()!!)
                            }
                            .addOnFailureListener {
                                _signupState.value = AuthState.Error(it.localizedMessage ?: "Failed to save user data")
                            }
                    }
                    .addOnFailureListener {
                        _signupState.value = AuthState.Error(it.localizedMessage ?: "Signup failed")
                    }
            } catch (e: Exception) {
                _signupState.value = AuthState.Error(e.localizedMessage ?: "Signup failed")
            }
        }
    }

    private fun validateSignupInput(name: String, email: String, password: String): String? {
        return when {
            name.isBlank() -> "Name cannot be empty"
            name.length < 2 -> "Name must be at least 2 characters"
            name.length > 30 -> "Name must be less than 30 characters"

            email.isBlank() -> "Email cannot be empty"
            !isValidEmail(email) -> "Please enter a valid email address"

            password.isBlank() -> "Password cannot be empty"
            password.length < 6 -> "Password must be at least 6 characters"
            password.length > 128 -> "Password is too long"

            else -> null // All validations passed
        }
    }

    private fun isValidEmail(email: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

    fun clearLoginState() { _loginState.value = AuthState.Idle }
    fun clearSignupState() { _signupState.value = AuthState.Idle }

    val savedCredentials = authPrefs.credentialsFlow
}