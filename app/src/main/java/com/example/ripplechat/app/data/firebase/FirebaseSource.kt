package com.example.ripplechat.app.data.model.firebase

import com.example.ripplechat.app.data.model.ChatMessage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

class FirebaseSource(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) {
    fun currentUserUid(): String? = auth.currentUser?.uid

    suspend fun createOrUpdateUser(uid: String, name: String, email: String, photoUrl: String? = null) {
        val map = mapOf(
            "name" to name,
            "nameIndex" to name.lowercase(),
            "email" to email,
            "photoUrl" to photoUrl,
        )
        firestore.collection("users").document(uid).set(map, SetOptions.merge()).await()
    }

    // Search users by nameIndex (excludes me)
    suspend fun searchUsersByName(q: String, myUid: String): List<Pair<String, Map<String, Any>>> {
        val lower = q.trim().lowercase()
        if (lower.isBlank()) return emptyList()
        val snap = firestore.collection("users")
            .whereGreaterThanOrEqualTo("nameIndex", lower)
            .whereLessThanOrEqualTo("nameIndex", lower + '\uf8ff')
            .get()
            .await()
        return snap.documents
            .filter { it.id != myUid }
            .map { it.id to it.data.orEmpty() }
    }

    // Contacts
    suspend fun addContact(myUid: String, peerUid: String) {
        val ref = firestore.collection("users").document(myUid)
            .collection("contacts").document(peerUid)
        ref.set(mapOf("addedAt" to com.google.firebase.Timestamp.now())).await()
    }

    fun listenContacts(myUid: String, onChange: (Set<String>) -> Unit): ListenerRegistration {
        return firestore.collection("users").document(myUid)
            .collection("contacts")
            .addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null) { onChange(emptySet()); return@addSnapshotListener }
                onChange(snapshot.documents.map { it.id }.toSet())
            }
    }

    // Load user docs by ids
    suspend fun getUsersByIds(uids: Set<String>): List<Pair<String, Map<String, Any>>> {
        if (uids.isEmpty()) return emptyList()
        val refs = uids.map { firestore.collection("users").document(it) }
        val snaps = firestore.runTransaction { txn ->
            refs.mapNotNull { ref ->
                val doc = txn.get(ref)
                if (doc.exists()) ref.id to doc.data.orEmpty() else null
            }
        }.await()
        return snaps
    }

    // Messages
    suspend fun sendMessage(chatId: String, payload: Map<String, Any>) {
        val ref = firestore.collection("chats").document(chatId)
            .collection("messages").document()
        val messageWithId = payload + ("id" to ref.id)
        ref.set(messageWithId).await()
        firestore.collection("chats").document(chatId)
            .set(
                mapOf(
                    "lastMessage" to (payload["text"] ?: ""),
                    "lastTimestamp" to payload["timestamp"]
                ), SetOptions.merge()
            ).await()
    }

    fun listenMessages(
        chatId: String,
        onAdded: (ChatMessage) -> Unit,
        onModified: (ChatMessage) -> Unit,
        onRemoved: (String) -> Unit
    ): ListenerRegistration {
        return firestore.collection("chats").document(chatId)
            .collection("messages")
            .orderBy("timestamp")
            .addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null) return@addSnapshotListener
                for (change in snapshot.documentChanges) {
                    val d = change.document
                    val data = d.data ?: continue
                    val msg = ChatMessage(
                        messageId = d.id,
                        chatId = chatId,
                        senderId = data["senderId"] as? String ?: "",
                        text = data["text"] as? String ?: "",
                        timestamp = (data["timestamp"] as? com.google.firebase.Timestamp)?.toDate()?.time
                            ?: System.currentTimeMillis()
                    )
                    when (change.type) {
                        DocumentChange.Type.ADDED -> onAdded(msg)
                        DocumentChange.Type.MODIFIED -> onModified(msg)
                        DocumentChange.Type.REMOVED -> onRemoved(d.id)
                    }
                }
            }
    }

    fun setTyping(chatId: String, uid: String, isTyping: Boolean) {
        firestore.collection("chats").document(chatId)
            .set(mapOf("typing" to mapOf(uid to isTyping)), SetOptions.merge())
    }

    fun listenChatDoc(chatId: String, onDoc: (Map<String, Any>?) -> Unit): ListenerRegistration {
        return firestore.collection("chats").document(chatId)
            .addSnapshotListener { snapshot, e ->
                if (e != null) { onDoc(null); return@addSnapshotListener }
                onDoc(snapshot?.data)
            }
    }
}
