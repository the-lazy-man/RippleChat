package com.example.ripplechat.app.util

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object CloudinaryUploadHelper {
    
    private const val SIGNATURE_URL = "https://auth-server-imagekit-for-ripplechat.onrender.com/cloudinary-auth"
    private val client = OkHttpClient()

    /**
     * Upload image bytes to Cloudinary.
     * Used for: Status images, profile pictures
     */
    suspend fun uploadImage(
        bytes: ByteArray,
        publicId: String
    ): String = withContext(Dispatchers.IO) {
        val (signature, timestamp, uploadPreset) = getCloudinarySignature(publicId, "image")
        
        uploadToCloudinary(
            bytes = bytes,
            publicId = publicId,
            resourceType = "image",
            signature = signature,
            timestamp = timestamp,
            uploadPreset = uploadPreset
        )
    }

    /**
     * Upload video from URI to Cloudinary.
     * Cloudinary handles large files efficiently.
     */
    suspend fun uploadVideo(
        uri: Uri,
        publicId: String,
        contentResolver: ContentResolver
    ): String = withContext(Dispatchers.IO) {
        val (signature, timestamp, uploadPreset) = getCloudinarySignature(publicId, "video")
        
        // For videos, we upload directly from URI
        val inputStream = contentResolver.openInputStream(uri) 
            ?: throw Exception("Cannot open video file")
        
        val bytes = inputStream.readBytes()
        inputStream.close()
        
        uploadToCloudinary(
            bytes = bytes,
            publicId = publicId,
            resourceType = "video",
            signature = signature,
            timestamp = timestamp,
            uploadPreset = uploadPreset
        )
    }

    /**
     * Convert image URI to compressed JPEG bytes.
     */
    suspend fun uriToImageBytes(
        uri: Uri,
        contentResolver: ContentResolver,
        maxDimension: Int = 1024,
        quality: Int = 85
    ): ByteArray = withContext(Dispatchers.Default) {
        val bitmap = withContext(Dispatchers.IO) {
            contentResolver.openInputStream(uri).use { inputStream ->
                BitmapFactory.decodeStream(inputStream)
            }
        }
        
        // Scale down if needed
        val scaled = if (bitmap.width > maxDimension || bitmap.height > maxDimension) {
            val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()
            val (newW, newH) = if (ratio > 1) {
                maxDimension to (maxDimension / ratio).toInt()
            } else {
                (maxDimension * ratio).toInt() to maxDimension
            }
            Bitmap.createScaledBitmap(bitmap, newW, newH, true)
        } else {
            bitmap
        }
        
        // Compress to JPEG
        ByteArrayOutputStream().use { stream ->
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, stream)
            stream.toByteArray()
        }
    }

    /**
     * Get Cloudinary signature from server.
     */
    private suspend fun getCloudinarySignature(
        publicId: String,
        resourceType: String
    ): Triple<String, Long, String> = withContext(Dispatchers.IO) {
        val jsonBody = JSONObject().apply {
            put("public_id", publicId)
            put("resource_type", resourceType)
        }.toString()

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = okhttp3.RequestBody.create(mediaType, jsonBody)

        val request = Request.Builder()
            .url(SIGNATURE_URL)
            .post(requestBody)
            .build()

        val response = client.newCall(request).execute()
        
        if (!response.isSuccessful) {
            throw Exception("Failed to get signature: ${response.code}")
        }

        val responseJson = JSONObject(response.body?.string() ?: "")
        
        Triple(
            responseJson.getString("signature"),
            responseJson.getLong("timestamp"),
            responseJson.getString("upload_preset")
        )
    }

    /**
     * Upload bytes to Cloudinary using MediaManager.
     */
    private suspend fun uploadToCloudinary(
        bytes: ByteArray,
        publicId: String,
        resourceType: String,
        signature: String,
        timestamp: Long,
        uploadPreset: String
    ): String = suspendCancellableCoroutine { continuation ->
        
        MediaManager.get().upload(bytes)
            .option("resource_type", resourceType)
            .option("public_id", publicId)
            .option("signature", signature)
            .option("timestamp", timestamp)
            .option("upload_preset", uploadPreset)
            .callback(object : UploadCallback {
                override fun onStart(requestId: String?) {
                    Log.d("CloudinaryUpload", "Upload started: $requestId")
                }

                override fun onProgress(requestId: String?, bytes: Long, totalBytes: Long) {
                    val percent = (bytes * 100 / totalBytes)
                    Log.d("CloudinaryUpload", "Progress: $percent%")
                }

                override fun onSuccess(requestId: String?, resultData: MutableMap<Any?, Any?>?) {
                    val url = resultData?.get("secure_url") as? String
                    if (url != null) {
                        Log.d("CloudinaryUpload", "Upload successful: $url")
                        continuation.resume(url)
                    } else {
                        continuation.resumeWithException(Exception("No URL returned from Cloudinary"))
                    }
                }

                override fun onError(requestId: String?, error: ErrorInfo?) {
                    Log.e("CloudinaryUpload", "Upload failed: ${error?.description}")
                    continuation.resumeWithException(
                        Exception(error?.description ?: "Cloudinary upload failed")
                    )
                }

                override fun onReschedule(requestId: String?, error: ErrorInfo?) {
                    Log.w("CloudinaryUpload", "Upload rescheduled: ${error?.description}")
                }
            })
            .dispatch()
    }
}
