package com.example.ripplechat.app.data.model

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST


/**
 * Data class representing the JSON payload sent to the Render server
 * to trigger an FCM notification.
 */
data class FcmRequest(
    @SerializedName("recipientId")
    val recipientId: String,
    @SerializedName("senderId")
    val senderId: String,
    @SerializedName("title")
    val senderName : String,
    @SerializedName("messageText")
    val messageText: String,
    @SerializedName("chatId")
    val chatId: String
)


interface NotificationService {
    @POST("send-fcm-notification")
    suspend fun triggerNotification(@Body request: FcmRequest): Response<Unit>
}