package com.example.ripplechat.app.data.model.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

class FirebaseSource(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) {
    fun currentUserUid(): String? = auth.currentUser?.uid

    suspend fun createUserInFirestore(uid: String, name: String, email: String) {
        val map = mapOf("name" to name, "email" to email, "photoUrl" to null)
        firestore.collection("users").document(uid).set(map).await()
    }

    // ✅ Get users list (one time fetch)
    suspend fun getOtherUsers(): List<Pair<String, Map<String, Any>>> {
        val uid = currentUserUid()
        val snap = firestore.collection("users").get().await()
        return snap.documents
            .filter { it.id != uid }
            .map { it.id to it.data.orEmpty() }
    }

    // ✅ Send a message
    suspend fun sendMessage(chatId: String, payload: Map<String, Any>) {
        val ref = firestore.collection("chats")
            .document(chatId)
            .collection("messages")
            .document() // generate new doc

        val messageWithId = payload + ("id" to ref.id)

        ref.set(messageWithId).await()

        // update chat metadata
        firestore.collection("chats").document(chatId)
            .set(
                mapOf(
                    "lastMessage" to payload["text"],
                    "lastTimestamp" to payload["timestamp"]
                ),
                SetOptions.merge()
            )
            .await()
    }

    // ✅ Listen to new messages in a chat (realtime snapshot listener)
    fun listenMessages(chatId: String, onEvent: (List<Map<String, Any>>) -> Unit): ListenerRegistration {
        return firestore.collection("chats")
            .document(chatId)
            .collection("messages")
            .orderBy("timestamp")
            .addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null) {
                    onEvent(emptyList())
                    return@addSnapshotListener
                }
                val docs = snapshot.documents.mapNotNull { it.data }
                onEvent(docs)
            }
    }

    // ✅ Update typing status in chat doc
    fun setTyping(chatId: String, uid: String, isTyping: Boolean) {
        val ref = firestore.collection("chats").document(chatId)
        ref.update("typing.$uid", isTyping)
    }

    // ✅ Listen to chat doc for metadata (typing, last seen, etc.)
    fun listenChatDoc(chatId: String, onDoc: (Map<String, Any>?) -> Unit): ListenerRegistration {
        return firestore.collection("chats")
            .document(chatId)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    onDoc(null)
                    return@addSnapshotListener
                }
                onDoc(snapshot?.data)
            }
    }
}
