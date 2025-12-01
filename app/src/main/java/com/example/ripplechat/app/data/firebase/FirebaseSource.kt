package com.example.ripplechat.app.data.model.firebase

import android.util.Log
import com.example.ripplechat.app.data.model.ChatMessage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await

class FirebaseSource(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) {
    fun currentUserUid(): String? = auth.currentUser?.uid

    fun saveUserToken(uid: String) {
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .update("fcmToken", token)
                .addOnSuccessListener {
                    Log.d("FirebaseSource", "Token saved: $token")
                }
                .addOnFailureListener {
                    Log.e("FirebaseSource", "Failed to save token: ${it.message}")
                }
        }
    }


    suspend fun createOrUpdateUser(uid: String, name: String, email: String, photoUrl: String? = null) {
        val map = mapOf(
            "name" to name,
            "nameIndex" to name.lowercase(),
            "email" to email,
            "profileImageUrl" to photoUrl,
        )
        firestore.collection("users").document(uid).set(map, SetOptions.merge()).await()
    }
    fun setPresence(uid: String, online: Boolean) {
        val ref = firestore.collection("presence").document(uid)
        ref.set(mapOf("online" to online, "lastSeen" to com.google.firebase.Timestamp.now()), SetOptions.merge())
    }

    fun listenPresence(peerUid: String, onChange: (Boolean, Long?) -> Unit): ListenerRegistration {
        return firestore.collection("presence").document(peerUid)
            .addSnapshotListener { snap, e ->
                if (e != null || snap == null) { onChange(false, null); return@addSnapshotListener }
                val online = (snap.get("online") as? Boolean) ?: false
                val ts = (snap.get("lastSeen") as? com.google.firebase.Timestamp)?.toDate()?.time
                onChange(online, ts)
            }
    }

    suspend fun deleteMessage(chatId: String, messageId: String) {
        firestore.collection("chats").document(chatId)
            .collection("messages")
            .document(messageId)
            .delete()
            .await()
    }
    // 🔹 FirebaseSource.kt
    suspend fun deleteContact(myUid: String, peerUid: String) {
        // Delete from my contacts
        firestore.collection("users").document(myUid)
            .collection("contacts").document(peerUid).delete().await()

        // Delete chat messages for my side
        val chatId = if (myUid < peerUid) "$myUid-$peerUid" else "$peerUid-$myUid"
        deleteChatMessages(chatId)
    }
    // FIX: Add missing function to FirebaseSource
    suspend fun getAllUsers(): List<Pair<String, Map<String, Any>>> {
        val snap = firestore.collection("users").get().await()
        val myUid = currentUserUid()
        return snap.documents
            .filter { it.id != myUid }
            .map { it.id to it.data.orEmpty() }
    }

    suspend fun editMessage(chatId: String, messageId: String, newText: String) {
        firestore.collection("chats").document(chatId)
            .collection("messages")
            .document(messageId)
            .update(mapOf("text" to newText, "edited" to true))
            .await()
    }
    suspend fun deleteChatMessages(chatId: String) {
        // WARNING: this can be expensive; we batch delete messages for this chat for current user.
        val col = firestore.collection("chats").document(chatId).collection("messages").get().await()
        val batch = firestore.batch()
        for (doc in col.documents) {
            batch.delete(doc.reference)
        }
        batch.commit().await()
        // optionally delete chat doc
        firestore.collection("chats").document(chatId).delete().await()
    }

    // Search users by nameIndex (excludes me)
    suspend fun searchUsersByName(q: String, myUid: String): List<Pair<String, Map<String, Any>>> {
        val lower = q.trim().lowercase()
        if (lower.isBlank()) {
            val snap = firestore.collection("users").get().await()
            return snap.documents
                .filter { it.id != myUid }
                .map { it.id to it.data.orEmpty() }
        }

        val snap = firestore.collection("users")
            .whereGreaterThanOrEqualTo("nameIndex", lower)
            .whereLessThanOrEqualTo("nameIndex", lower + '\uf8ff')
            .get()
            .await()
        val result = snap.documents
            .filter { it.id != myUid }
            .map { it.id to it.data.orEmpty() }

        if (result.isEmpty()) {
            // 🔹 fallback: try filtering locally from ALL users
            val all = firestore.collection("users").get().await()
            return all.documents
                .filter { it.id != myUid }
                .filter { (it.getString("name") ?: "").lowercase().contains(lower) }
                .map { it.id to it.data.orEmpty() }
        }
        return result
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

    fun generateMessageId(): String {
        return firestore.collection("chats").document().collection("messages").document().id
    }

    // FIX: Add a unique messageId to the function signature
    suspend fun sendMessage(chatId: String, messageId: String, payload: Map<String, Any>) {
        val ref = firestore.collection("chats").document(chatId)
            .collection("messages").document(messageId) // Use the unique ID here

        val messageWithId = payload + ("id" to messageId) // Add the ID to the payload
        ref.set(messageWithId).await() // Now, this correctly sets the message with a unique ID

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
                            ?: System.currentTimeMillis(),
                        edited = (data["edited"] as? Boolean) ?: false, // <-- NEW
                        mediaUrl = data["mediaUrl"] as? String,         // <-- NEW
                        isMedia = (data["isMedia"] as? Boolean) ?: false, // <-- NEW
                        mediaType = data["mediaType"] as? String          // <-- NEW
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
