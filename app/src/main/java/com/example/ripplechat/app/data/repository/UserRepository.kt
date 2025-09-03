package com.example.ripplechat.app.data.repository

import com.example.ripplechat.app.data.model.User
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject

// data/repository/UserRepository.kt

class UserRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) {

    fun getAllUsersFlow(): Flow<List<User>> = callbackFlow {
        val subscription = firestore.collection("users")
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    close(e)
                    return@addSnapshotListener
                }
                val users = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(User::class.java)?.copy(uid = doc.id)
                }.orEmpty()
                trySend(users)
            }
        awaitClose { subscription.remove() }
    }
}
