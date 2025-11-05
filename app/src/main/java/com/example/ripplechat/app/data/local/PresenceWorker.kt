package com.example.ripplechat.app.data.local


import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.ripplechat.data.repository.ChatRepository
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class PresenceWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    @Inject
    lateinit var chatRepository: ChatRepository

    override suspend fun doWork(): Result {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        userId?.let { chatRepository.setPresence(it, false) }
        return Result.success()
    }
}
