    package com.example.ripplechat.app.ui.auth

    import androidx.lifecycle.ViewModel
    import androidx.lifecycle.viewModelScope
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
    class AuthViewModel@Inject constructor(private val authPrefs: AuthPreferences, private val auth: FirebaseAuth, private val db: FirebaseFirestore) : ViewModel() {

        private val _loginState = MutableStateFlow<AuthState>(AuthState.Idle)
        val loginState = _loginState.asStateFlow()

        private val _signupState = MutableStateFlow<AuthState>(AuthState.Idle)
        val signupState = _signupState.asStateFlow()

        fun login(username: String, password: String) {
            _loginState.value = AuthState.Loading
            viewModelScope.launch {
                try {
                    val snap = db.collection("users")
                        .whereEqualTo("name", username)
                        .get()
                        .await()

                    if (snap.isEmpty) {
                        _loginState.value = AuthState.Error("User not found")
                        return@launch
                    }

                    val email = snap.documents.first().getString("email") ?: ""
                    auth.signInWithEmailAndPassword(email, password)
                        .addOnSuccessListener {
                            _loginState.value = AuthState.Success
                        }
                        .addOnFailureListener {
                            _loginState.value = AuthState.Error(it.localizedMessage ?: "Login failed")
                        }
                } catch (e: Exception) {
                    _loginState.value = AuthState.Error(e.localizedMessage ?: "Login failed")
                }
            }
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
                        "usernameIndex" to name.lowercase(),
                        "profileImageUrl" to null
                    )
                    db.collection("users").document(uid).set(user)
                        .addOnSuccessListener { _signupState.value = AuthState.Success
                            viewModelScope.launch {
                                authPrefs.save(name, password) // ✅ Save username now
                            }
                        }
                        .addOnFailureListener { _signupState.value = AuthState.Error(it.localizedMessage ?: "Firestore save failed") }
                }
                .addOnFailureListener { _signupState.value = AuthState.Error(it.localizedMessage ?: "Signup failed") }
        }

        fun clearLoginState() { _loginState.value = AuthState.Idle }
        fun clearSignupState() { _signupState.value = AuthState.Idle }



        val savedCredentials = authPrefs.credentialsFlow
    }
