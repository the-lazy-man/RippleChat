    package com.example.ripplechat.app.data.model.firebase

import android.util.Log
import com.example.ripplechat.app.data.model.ChatMessage
import com.example.ripplechat.app.data.model.Status
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await
import okhttp3.MediaType.Companion.toMediaType

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
    // 🔹 Removes contact + wipes dashboard entry. Does NOT delete messages/media (use clearChatOnly for that).
    suspend fun deleteContact(myUid: String, peerUid: String) {
        // Delete from my contacts
        firestore.collection("users").document(myUid)
            .collection("contacts").document(peerUid).delete().await()

        val chatId = if (myUid < peerUid) "$myUid-$peerUid" else "$peerUid-$myUid"

        // Delete from my sub-collection chats so it disappears from the Dashboard UI
        firestore.collection("users").document(myUid)
            .collection("chats").document(chatId).delete().await()
    }

    /**
     * Removes ONLY the dashboard chat entry (users/{uid}/chats/{chatId}).
     * Contact subcollection is untouched — user can still be found via search and re-added.
     */
    suspend fun removeChatFromDashboard(myUid: String, peerUid: String) {
        val chatId = if (myUid < peerUid) "$myUid-$peerUid" else "$peerUid-$myUid"
        firestore.collection("users").document(myUid)
            .collection("chats").document(chatId).delete().await()
    }

    /**
     * STEP 1: Scan all messages in a chat, collect every mediaUrl, and delete each from Cloudinary.
     * Cloudinary URL format: https://res.cloudinary.com/{cloud}/{type}/upload/v.../public_id.ext
     * We extract the public_id and resource_type from the URL automatically.
     */
    suspend fun wipeChatMedia(chatId: String) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val messages = firestore.collection("chats").document(chatId)
                .collection("messages").get().await()

            val client = okhttp3.OkHttpClient()
            val jsonMediaType = "application/json; charset=utf-8".toMediaType()

            for (doc in messages.documents) {
                val mediaUrl = doc.getString("mediaUrl") ?: continue
                val mediaType = doc.getString("mediaType") ?: "image"

                // Only process Cloudinary URLs
                if (!mediaUrl.contains("res.cloudinary.com")) continue

                // Extract public_id from URL
                // e.g. https://res.cloudinary.com/demo/image/upload/v1234/chat_uid_ts.jpg
                //  -> publicId = "chat_uid_ts", resourceType = "image"
                val urlPath = mediaUrl.substringAfter("/upload/")
                    .substringAfter("/") // skip version segment like "v1234567/"
                val publicId = urlPath.substringBeforeLast(".")

                val resourceType = when {
                    mediaType == "video" || mediaUrl.contains("/video/") -> "video"
                    mediaType == "audio" || mediaUrl.contains("/raw/") -> "raw"
                    else -> "image"
                }

                val json = org.json.JSONObject().apply {
                    put("public_id", publicId)
                    put("resource_type", resourceType)
                }.toString()

                try {
                    val body = okhttp3.RequestBody.create(jsonMediaType, json)
                    val request = okhttp3.Request.Builder()
                        .url("https://auth-server-imagekit-for-ripplechat.onrender.com/cloudinary-delete")
                        .post(body)
                        .build()
                    val response = client.newCall(request).execute()
                    if (response.isSuccessful) {
                        Log.d("FirebaseSource", "Cloudinary delete OK: $publicId ($resourceType)")
                    } else {
                        Log.e("FirebaseSource", "Cloudinary delete failed (${response.code}): ${response.body?.string()}")
                    }
                } catch (e: Exception) {
                    Log.e("FirebaseSource", "Error deleting Cloudinary asset $publicId: ${e.message}")
                    // Non-critical — continue deleting other assets
                }
            }
        } catch (e: Exception) {
            Log.e("FirebaseSource", "wipeChatMedia failed: ${e.message}")
        }
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
    /**
     * STEP 2: Delete all Firestore messages + the parent chat doc.
     * The parent chat doc (chats/{chatId}) stores: lastMessage, typing status.
     * Deleting it frees up that metadata too.
     */
    suspend fun deleteChatMessages(chatId: String) {
        try {
            val col = firestore.collection("chats").document(chatId).collection("messages").get().await()
            val documents = col.documents

            // Firestore batch limit = 500 ops — chunk to avoid crash
            val chunkedDocs = documents.chunked(500)
            for (chunk in chunkedDocs) {
                val batch = firestore.batch()
                for (doc in chunk) { batch.delete(doc.reference) }
                batch.commit().await()
            }

            // Delete the parent chat document (chats/{chatId} stores typing, lastMessage etc.)
            firestore.collection("chats").document(chatId).delete().await()
        } catch (e: Exception) {
            Log.e("FirebaseSource", "Error clearing chat messages: ${e.message}")
        }
    }

    /**
     * STEP 3 (Dashboard "Clear Chat" action):
     * Clears messages + media but keeps the contact and dashboard entry intact.
     * The chat remains visible in the dashboard but appears empty.
     */
    suspend fun clearChatOnly(myUid: String, peerUid: String) {
        val chatId = if (myUid < peerUid) "$myUid-$peerUid" else "$peerUid-$myUid"
        // 1. Delete Cloudinary media assets
        wipeChatMedia(chatId)
        // 2. Delete Firestore messages + parent chat doc
        deleteChatMessages(chatId)
        // 3. Reset the dashboard metadata so it shows "No messages yet" instead of old last message
        firestore.collection("users").document(myUid)
            .collection("chats").document(chatId)
            .update(mapOf(
                "lastMessage" to "",
                "unreadCount" to 0
            )).await()
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

    // Contacts - Updated to create chat metadata immediately
    suspend fun addContact(myUid: String, peerUid: String) {
        // 1. Add to contacts subcollection (for backward compatibility)
        val ref = firestore.collection("users").document(myUid)
            .collection("contacts").document(peerUid)
        ref.set(mapOf("addedAt" to com.google.firebase.Timestamp.now())).await()
        
        // 2. Get user info for both users
        val peerInfo = getUserInfo(peerUid)
        val myInfo = getUserInfo(myUid)
        
        // 3. Create chat metadata so contact appears in chat list immediately
        if (peerInfo != null && myInfo != null) {
            val chatId = if (myUid < peerUid) "$myUid-$peerUid" else "$peerUid-$myUid"
            
            // Create chat entry for current user
            firestore.collection("users").document(myUid)
                .collection("chats").document(chatId)
                .set(mapOf(
                    "peerUid" to peerUid,
                    "peerName" to (peerInfo["name"] as? String ?: "Unknown"),
                    "peerProfilePic" to peerInfo["profileImageUrl"],
                    "lastMessage" to "Say hello! 👋",
                    "lastTimestamp" to com.google.firebase.Timestamp.now(),
                    "unreadCount" to 0
                ), SetOptions.merge())
                .await()
            
            // Create chat entry for peer so they also see the contact
            firestore.collection("users").document(peerUid)
                .collection("chats").document(chatId)
                .set(mapOf(
                    "peerUid" to myUid,
                    "peerName" to (myInfo["name"] as? String ?: "Unknown"),
                    "peerProfilePic" to myInfo["profileImageUrl"],
                    "lastMessage" to "${myInfo["name"]} added you as a contact",
                    "lastTimestamp" to com.google.firebase.Timestamp.now(),
                    "unreadCount" to 0
                ), SetOptions.merge())
                .await()
        }
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
                        edited = (data["edited"] as? Boolean) ?: false,
                        mediaUrl = data["mediaUrl"] as? String,
                        isMedia = (data["isMedia"] as? Boolean) ?: false,
                        mediaType = data["mediaType"] as? String
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

    // ========== NEW: Sub-Collection Chat List Functions ==========

    /**
     * Listen to the user's chat list in real-time.
     * Returns chat metadata sorted by most recent activity.
     */
    fun listenUserChats(myUid: String, onChange: (List<Map<String, Any>>) -> Unit): ListenerRegistration {
        return firestore.collection("users").document(myUid)
            .collection("chats")
            .orderBy("lastTimestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null) {
                    Log.e("FirebaseSource", "Error listening to user chats: ${e?.message}")
                    onChange(emptyList())
                    return@addSnapshotListener
                }
                val chats = snapshot.documents.mapNotNull { doc ->
                    doc.data?.plus("chatId" to doc.id)
                }
                onChange(chats)
            }
    }

    /**
     * Update chat metadata for BOTH users when a message is sent.
     * Creates the chat document if it doesn't exist.
     */
    suspend fun updateChatMetadata(
        myUid: String,
        peerUid: String,
        chatId: String,
        lastMessage: String,
        timestamp: com.google.firebase.Timestamp,
        myName: String,
        peerName: String,
        myProfilePic: String? = null,
        peerProfilePic: String? = null
    ) {
        // Update sender's chat list (me)
        firestore.collection("users").document(myUid)
            .collection("chats").document(chatId)
            .set(mapOf(
                "peerUid" to peerUid,
                "peerName" to peerName,
                "peerProfilePic" to peerProfilePic,
                "lastMessage" to lastMessage,
                "lastTimestamp" to timestamp
                // Don't increment unreadCount for sender
            ), SetOptions.merge()).await()

        // Update receiver's chat list (peer)
        firestore.collection("users").document(peerUid)
            .collection("chats").document(chatId)
            .set(mapOf(
                "peerUid" to myUid,
                "peerName" to myName,
                "peerProfilePic" to myProfilePic,
                "lastMessage" to lastMessage,
                "lastTimestamp" to timestamp,
                "unreadCount" to FieldValue.increment(1) // Increment for receiver
            ), SetOptions.merge()).await()
    }

    /**
     * Mark a chat as read by resetting unread count to 0.
     */
    suspend fun markChatAsRead(myUid: String, chatId: String) {
        firestore.collection("users").document(myUid)
            .collection("chats").document(chatId)
            .update("unreadCount", 0)
            .await()
    }

    /**
     * Get user info for a specific peer (used when creating chat metadata).
     */
    suspend fun getUserInfo(uid: String): Map<String, Any>? {
        return try {
            val doc = firestore.collection("users").document(uid).get().await()
            doc.data
        } catch (e: Exception) {
            Log.e("FirebaseSource", "Error getting user info: ${e.message}")
            null
        }
    }
    // ========== NEW: Status Functions ==========

    /**
     * Upload a new status to the user's status sub-collection.
     */
    suspend fun addStatus(myUid: String, status: Status) {
        val ref = firestore.collection("users").document(myUid)
            .collection("status").document() // Auto-generate ID
        
        val statusWithId = status.copy(statusId = ref.id, userId = myUid)
        ref.set(statusWithId).await()
    }

    /**
     * Listen to statuses for a list of UIDs (contacts).
     * This returns a map of UserId -> List of their active Statuses.
     */
    fun listenStatuses(uids: List<String>, onUpdate: (Map<String, List<Status>>) -> Unit): List<ListenerRegistration> {
        val registrations = mutableListOf<ListenerRegistration>()
        val allStatuses = mutableMapOf<String, List<Status>>()
        val now = System.currentTimeMillis()

        uids.forEach { uid ->
            val reg = firestore.collection("users").document(uid)
                .collection("status")
                .whereGreaterThan("expiresAt", now)
                .addSnapshotListener { snapshot, e ->
                    if (e != null || snapshot == null) return@addSnapshotListener
                    
                    val statuses = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(Status::class.java)
                    }.sortedByDescending { it.timestamp }
                    
                    allStatuses[uid] = statuses
                    onUpdate(allStatuses.toMap())
                }
            registrations.add(reg)
        }
        return registrations
    }

    /**
     * Delete a status from Firestore.
     */
    suspend fun deleteStatus(userId: String, statusId: String) {
        firestore.collection("users")
            .document(userId)
            .collection("status")
            .document(statusId)
            .delete()
            .await()
    }

    /**
     * Delete status media from Cloudinary via backend /cloudinary-delete endpoint.
     * Extracts public_id from the CDN URL and sends it to the server.
     *
     * @param mediaUrl   The full Cloudinary URL (e.g. https://res.cloudinary.com/.../status_uid_123.jpg)
     * @param resourceType  "image" or "video" — passed to Cloudinary's destroy API
     */
    suspend fun deleteStatusMedia(mediaUrl: String, resourceType: String = "image") {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                // Extract public_id from Cloudinary URL
                // URL format: https://res.cloudinary.com/{cloud}/image/upload/v1234/status_uid_ts.jpg
                val publicId = mediaUrl
                    .substringAfterLast("/")    // "status_uid_ts.jpg"
                    .substringBeforeLast(".")   // "status_uid_ts"

                val json = org.json.JSONObject().apply {
                    put("public_id", publicId)
                    put("resource_type", resourceType)
                }.toString()

                val mediaType = "application/json; charset=utf-8"
                    .toMediaType()
                val body = okhttp3.RequestBody.create(mediaType, json)

                val request = okhttp3.Request.Builder()
                    .url("https://auth-server-imagekit-for-ripplechat.onrender.com/cloudinary-delete")
                    .post(body)
                    .build()

                val response = okhttp3.OkHttpClient().newCall(request).execute()
                if (response.isSuccessful) {
                    Log.d("FirebaseSource", "Cloudinary media deleted: $publicId")
                } else {
                    Log.e("FirebaseSource", "Cloudinary delete failed (${response.code}): ${response.body?.string()}")
                }
            } catch (e: Exception) {
                Log.e("FirebaseSource", "Error deleting Cloudinary media: ${e.message}", e)
                // Non-critical — status doc is already deleted from Firestore
            }
        }
    }

    /**
     * Update status fields (e.g., caption, backgroundColor for text statuses).
     */
    suspend fun updateStatus(userId: String, statusId: String, updates: Map<String, Any?>) {
        firestore.collection("users")
            .document(userId)
            .collection("status")
            .document(statusId)
            .update(updates)
            .await()
    }
}
