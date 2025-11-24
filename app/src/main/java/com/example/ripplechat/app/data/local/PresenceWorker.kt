package com.example.ripplechat.app.data.local

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.ripplechat.app.di.ChatRepositoryEntryPoint
import dagger.hilt.android.EntryPointAccessors
import com.example.ripplechat.data.repository.ChatRepository
import com.google.firebase.auth.FirebaseAuth

// This is now a standard CoroutineWorker (no @HiltWorker)
// It uses the standard Worker constructor.
class SetPresenceOffWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val TAG = "PresenceOff"
    }

    override suspend fun doWork(): Result {
        val userId =  FirebaseAuth.getInstance().currentUser?.uid

        // Manual Hilt Retrieval (Service Locator Pattern)
        // We retrieve the ChatRepository using the EntryPoint defined in the companion Canvas.
        val chatRepository = try {
            EntryPointAccessors.fromApplication(
                applicationContext,
                ChatRepositoryEntryPoint::class.java
            ).chatRepository()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to retrieve ChatRepository via Hilt EntryPoint. Check if Hilt is fully initialized and dependencies are in SingletonComponent.", e)
            return Result.failure()
        }

        return if (userId.isNullOrBlank()) {
            Log.e(TAG, "User ID is missing. Cannot set presence off.")
            Result.failure()
        } else {
            try {
                // Call the repository function to set presence to false
                chatRepository.setPresence(userId, false)
                Log.d(TAG, "Presence set to false for user: $userId")
                Result.success()
            } catch (e: Exception) {
                // If the operation fails (e.g., network error), retry later
                Log.e(TAG, "Failed to set presence off. Retrying.", e)
                Result.retry()
            }
        }
    }
}
