package com.example.ripplechat.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.core.app.NotificationCompat
import com.example.ripplechat.MainActivity
import com.example.ripplechat.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class ChatFirebaseService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("ChatFirebaseService", "New token: $token")
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseFirestore.getInstance()
            .collection("users")
            .document(uid)
            .update("fcmToken", token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        // Only show notification if user has not muted the sender
        val fromUser = remoteMessage.data["fromUser"] ?: ""
        val body = remoteMessage.data["body"] ?: "New message"

        // TODO: load mute prefs from DataStore (we’ll do this in Dashboard)
        val mutedUsers = emptyList<String>() // placeholder
        if (!mutedUsers.contains(fromUser)) {
            showNotification("RippleChat", body)
        }
    }

    private fun showNotification(senderId: String?, messageText: String?) {
        val chatId = createChatId(FirebaseAuth.getInstance().uid, senderId)
        val senderName = senderId // or fetch senderName if available in payload

        // Intent to open MainActivity with deep-link-like extras
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to_chat", true)
            putExtra("chatId", chatId)
            putExtra("senderId", senderId)
            putExtra("senderName", senderName)
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationBuilder = NotificationCompat.Builder(this, "chat_channel")
            .setSmallIcon(Icons.Default.Notifications)
            .setContentTitle("New message from $senderName")
            .setContentText(messageText)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(chatId.hashCode(), notificationBuilder.build())
    }

    private fun createChatId(currentUid: String?, senderUid: String?): String {
        if (currentUid == null || senderUid == null) return ""
        return if (currentUid < senderUid) "$currentUid-$senderUid" else "$senderUid-$currentUid"
    }

}
