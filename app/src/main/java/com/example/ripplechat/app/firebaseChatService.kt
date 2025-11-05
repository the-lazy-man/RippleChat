package com.example.ripplechat.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.ripplechat.MainActivity
import com.example.ripplechat.R
import com.example.ripplechat.app.data.local.NotificationPreferences
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ChatFirebaseService(): FirebaseMessagingService() {

    private val notificationPreferences: NotificationPreferences by lazy {
        EntryPointAccessors.fromApplication(
            applicationContext,
            NotificationPrefsEntryPoint::class.java
        ).notificationPreferences()
    }

    companion object {
        private const val CHANNEL_ID = "chat_channel"
        private const val CHANNEL_NAME = "Chat messages"
    }

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
        // read sender and message from data payload (backend sends data.*)
        val senderId = remoteMessage.data["senderId"] ?: remoteMessage.data["fromUser"] ?: ""
        val messageText = remoteMessage.data["body"] ?: remoteMessage.data["messageText"] ?: "New message"
        val chatIdFromPayload = remoteMessage.data["chatId"]
        val senderName = remoteMessage.data["title"] ?: remoteMessage.data["peerName"] ?: senderId

        // Now it's safe to use the non-nullable lateinit property
        val preferences = notificationPreferences
        // ✅ Launch background coroutine
        CoroutineScope(Dispatchers.IO).launch {
            val mutedUsers = preferences.mutedUsersFlow.first()

            if (mutedUsers.contains(senderId)) {
                Log.d("ChatFirebaseService", "Muted user → No notification: $senderId")
                return@launch
            }

            showNotification(
                senderId = senderId,
                senderName = senderName,
                messageText = messageText,
                chatId = chatIdFromPayload
            )
        }
    }

    private fun showNotification(senderId: String?, senderName: String?, messageText: String?, chatId: String?) {
        createNotificationChannelIfNeeded()

        // Resolve or build chatId
        val currentUid = FirebaseAuth.getInstance().uid
        val finalChatId = if (!chatId.isNullOrBlank()) chatId else createChatId(currentUid, senderId)

        // Build intent that opens MainActivity with navigation extras
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to_chat", true)
            putExtra("chatId", finalChatId)
            putExtra("peerUid", senderId)
            putExtra("peerName", senderName)
        }

        // Use a unique request code per chat so PendingIntent carries correct extras
        val requestCode = finalChatId.hashCode()

        val pendingIntent = PendingIntent.getActivity(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            // Use your app icon; do NOT use Compose icons here
            .setSmallIcon(R.mipmap.ic_bell)
            .setContentTitle(senderName ?: "New message")
            .setContentText(messageText)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(finalChatId.hashCode(), notificationBuilder.build())
    }

    private fun createNotificationChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val existing = manager.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                )
                channel.description = "Notifications for chat messages"
                manager.createNotificationChannel(channel)
            }
        }
    }

    private fun createChatId(currentUid: String?, senderUid: String?): String {
        if (currentUid == null || senderUid == null) return ""
        return if (currentUid < senderUid) "$currentUid-$senderUid" else "$senderUid-$currentUid"
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface NotificationPrefsEntryPoint {
        fun notificationPreferences(): NotificationPreferences
    }

}
