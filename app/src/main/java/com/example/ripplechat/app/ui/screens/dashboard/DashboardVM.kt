package com.example.ripplechat.app.data.model.ui.theme.screens.home


import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ripplechat.app.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

// ui/screens/UserListViewModel.kt
@HiltViewModel
class DashBoardVM @Inject constructor(
    private val userRepository: UserRepository
) : ViewModel() {

    val users = userRepository.getAllUsersFlow()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
}

