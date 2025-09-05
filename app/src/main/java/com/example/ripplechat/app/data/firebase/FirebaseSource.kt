package com.example.ripplechat.app.data.model.firebase

import android.util.Log
import com.example.ripplechat.app.data.model.ChatMessage
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
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

    // non-suspending: generate a new doc id for messages (doesn't write)
    fun generateMessageId(chatId: String): String =
        firestore.collection("chats")
            .document(chatId)
            .collection("messages")
            .document()
            .id

    // suspend: write a message with a specific id
    suspend fun sendMessageWithId(chatId: String, messageId: String, payload: Map<String, Any>) {
        val ref = firestore.collection("chats")
            .document(chatId)
            .collection("messages")
            .document(messageId)

        // ensure id in payload for convenience
        val withId = payload + ("id" to messageId)
        ref.set(withId).await()

        // update chat metadata for quick preview in dashboard
        firestore.collection("chats")
            .document(chatId)
            .set(
                mapOf(
                    "lastMessage" to payload["text"],
                    "lastTimestamp" to payload["timestamp"]
                ),
                com.google.firebase.firestore.SetOptions.merge()
            )
            .await()
    }

    // listenMessages: notify caller for EACH DocumentChange
    fun listenMessages(chatId: String, onChange: (DocumentChange, ChatMessage) -> Unit): ListenerRegistration {
        Log.d("FirebaseSource", "Setting up message listener for chat ID: $chatId") // Add this

        return firestore.collection("chats")
            .document(chatId)
            .collection("messages")
            .orderBy("timestamp")
            .addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null) {
                    Log.e("FirebaseSource", "Message listener error for $chatId: ${e?.message}", e) // Add this

                    return@addSnapshotListener
                }
                if (snapshot == null) {
                    Log.d("FirebaseSource", "Message listener snapshot is null for $chatId")
                    return@addSnapshotListener
                }

                Log.d("FirebaseSource", "Message listener triggered for $chatId. Changes: ${snapshot.documentChanges.size}") // Add this
                for (change in snapshot.documentChanges) {
                    val d = change.document
                    val data = d.data ?: continue

                    val ts = (data["timestamp"] as? com.google.firebase.Timestamp)?.toDate()?.time
                        ?: System.currentTimeMillis()

                    val msg = ChatMessage(
                        messageId = d.id,
                        chatId = chatId,
                        senderId = data["senderId"] as? String ?: "",
                        text = data["text"] as? String ?: "",
                        timestamp = ts
                    )
                    onChange(change, msg)
                    Log.d("FirebaseSource", "Processed message change: ${change.type} - ${msg.text}") // Add this

                }
            }
    }

    // typing status
    fun setTyping(chatId: String, uid: String, isTyping: Boolean) {
        firestore.collection("chats").document(chatId)
            .update("typing_$uid", isTyping)
    }

    fun listenChatDoc(chatId: String, onDoc: (Map<String, Any>?) -> Unit): ListenerRegistration {
        Log.d("FirebaseSource", "Setting up chat doc listener for chat ID: $chatId") // Add this
        return firestore.collection("chats")
            .document(chatId)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e("FirebaseSource", "Chat doc listener error for $chatId: ${e.message}", e) // Add this
                    onDoc(null); return@addSnapshotListener
                }
                if (snapshot == null) {
                    Log.d("FirebaseSource", "Chat doc snapshot is null for $chatId")
                } else {
                    Log.d("FirebaseSource", "Chat doc listener triggered for $chatId. Data: ${snapshot.data}") // Add this
                }
                onDoc(snapshot?.data)
            }
    }
}
