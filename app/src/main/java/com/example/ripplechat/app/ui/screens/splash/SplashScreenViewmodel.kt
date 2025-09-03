package com.example.ripplechat.app.data.model.ui.theme.screens.splash


import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth

class SplashViewModel : ViewModel() {
    fun isUserLoggedIn(): Boolean = FirebaseAuth.getInstance().currentUser != null
}
