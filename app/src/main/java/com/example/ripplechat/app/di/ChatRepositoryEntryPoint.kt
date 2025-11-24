package com.example.ripplechat.app.di


import com.example.ripplechat.data.repository.ChatRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

// This interface allows non-injected classes (like the standard Worker)
// to manually retrieve dependencies from the Hilt SingletonComponent.
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ChatRepositoryEntryPoint {
    fun chatRepository(): ChatRepository
}
