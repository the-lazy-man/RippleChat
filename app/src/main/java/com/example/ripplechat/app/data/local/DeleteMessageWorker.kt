package com.example.ripplechat.app.data.local

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.ripplechat.app.di.ChatRepositoryEntryPoint
import com.example.ripplechat.data.repository.ChatRepository
import dagger.hilt.android.EntryPointAccessors

/**
 * Worker that deletes ALL messages in a chat after a scheduled delay.
 * Handles both Firestore deletion and Cloudinary media cleanup for all messages.
 */
class DeleteChatMessagesWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val TAG = "DeleteChatWorker"
        const val KEY_CHAT_ID = "chat_id"
    }

    override suspend fun doWork(): Result {
        val chatId = inputData.getString(KEY_CHAT_ID)

        if (chatId.isNullOrBlank()) {
            Log.e(TAG, "Missing chatId. Cannot delete messages.")
            return Result.failure()
        }

        // Manual Hilt Retrieval (Service Locator Pattern)
        val chatRepository = try {
            EntryPointAccessors.fromApplication(
                applicationContext,
                ChatRepositoryEntryPoint::class.java
            ).chatRepository()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to retrieve ChatRepository via Hilt EntryPoint", e)
            return Result.failure()
        }

        return try {
            // Delete all messages in the chat with media cleanup
            chatRepository.deleteChatMessagesWithMedia(chatId)
            Log.d(TAG, "All messages deleted successfully in chat: $chatId")
            Result.success()
        } catch (e: Exception) {
            // If the operation fails (e.g., network error), retry later
            Log.e(TAG, "Failed to delete chat messages. Retrying.", e)
            Result.retry()
        }
    }
}
