package com.example.ripplechat.profile

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import com.cloudinary.android.callback.UploadCallback

data class ProfileUser(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val profileImageUrl: String? = null
)

sealed class ProfileState {
    object Idle : ProfileState()
    object Loading : ProfileState()
    object Success : ProfileState()
    data class Error(val message: String) : ProfileState()
}

@HiltViewModel
class ProfileViewModel @Inject constructor() : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance().reference

    private val _user = MutableStateFlow(ProfileUser())
    val user = _user.asStateFlow()

    var updateState: ProfileState = ProfileState.Idle
        private set

    private val client = OkHttpClient()

    init {
        val uid = auth.currentUser?.uid ?: ""
        db.collection("users").document(uid).addSnapshotListener { snap, _ ->
            snap?.let {
                _user.value = ProfileUser(
                    uid = uid,
                    name = it.getString("name") ?: "",
                    email = it.getString("email") ?: "",
                    profileImageUrl = it.getString("profileImageUrl")
                )
            }
        }
    }

    fun updateName(newName: String) {
        val uid = auth.currentUser?.uid ?: return
        updateState = ProfileState.Loading
        db.collection("users").document(uid)
            .update(mapOf("name" to newName, "nameIndex" to newName.lowercase()))
            .addOnSuccessListener { updateState = ProfileState.Success }
            .addOnFailureListener { updateState = ProfileState.Error(it.localizedMessage ?: "Failed") }
    }

    fun logout(onLoggedOut: () -> Unit) {
        auth.signOut()
        onLoggedOut()
    }

    /**
     * Process (apply brightness) and upload selected image.
     * If you want cropping too, integrate uCrop and pass the cropped Uri here.
     */
    fun uploadProcessedPicture(
        resolver: ContentResolver,
        sourceUri: Uri,
        brightness: Float // -1.0 .. +1.0 (0 = no change)
    ) {
        val uid = auth.currentUser?.uid ?: return
        updateState = ProfileState.Loading

        viewModelScope.launch {
            try {
                // 1. Get a signed upload signature from the backend
                val signatureResponse = withContext(Dispatchers.IO) {
                    try {
                        val request = Request.Builder()
                            .url(SIGNATURE_URL)
                            .get() // Use GET instead of POST
                            .build()
                        client.newCall(request).execute()
                    } catch (e: IOException) {
                        throw Exception("Network request for signature failed", e)
                    }
                }

                if (!signatureResponse.isSuccessful) {
                    throw Exception("Failed to get signature from server: ${signatureResponse.code}")
                }
                val signatureJson = signatureResponse.body?.string()
                val jsonObject = JSONObject(signatureJson)

                val signature = jsonObject.getString("signature")
                val timestamp = jsonObject.getLong("timestamp")
                val cloudName = jsonObject.getString("cloud_name") // Changed to match server key
                val apiKey = jsonObject.getString("api_key") // Changed to match server key
                val uploadPreset = jsonObject.getString("upload_preset")

                // Log the successful signature retrieval
                Log.d("ProfileViewModel", "Successfully retrieved signature and timestamp.")

                // 2. Load, process, and compress bitmap
                val bmp = withContext(Dispatchers.IO) {
                    loadBitmap(resolver, sourceUri)
                }

                val processed = withContext(Dispatchers.Default) {
                    applyBrightness(bmp, brightness)
                }

                val maxDim = 1024
                val scaled = withContext(Dispatchers.Default) {
                    if (processed.width > maxDim || processed.height > maxDim) {
                        val ratio = processed.width.toFloat() / processed.height.toFloat()
                        val (newW, newH) = if (ratio > 1) {
                            maxDim to (maxDim / ratio).toInt()
                        } else {
                            (maxDim * ratio).toInt() to maxDim
                        }
                        Bitmap.createScaledBitmap(processed, newW, newH, true)
                    } else {
                        processed
                    }
                }

                val uploadBytes = withContext(Dispatchers.Default) {
                    ByteArrayOutputStream().use { stream ->
                        scaled.compress(Bitmap.CompressFormat.JPEG, 85, stream)
                        stream.toByteArray()
                    }
                }
                Log.d("ProfileViewModel", "Image bytes size: ${uploadBytes.size}")

                // 3. Upload to Cloudinary using the signature
                withContext(Dispatchers.IO) {
                    MediaManager.get().upload(uploadBytes)
                        .option("resource_type", "image")
                        .option("public_id", "profile_$uid")
                        .option("signature", signature)
                        .option("timestamp", timestamp)
                        .option("cloud_name", cloudName)
                        .option("api_key", apiKey)
                        .option("upload_preset", uploadPreset) // <-- Re-added this option
                        .callback(object : UploadCallback {
                            override fun onStart(requestId: String?) {
                                Log.d("ProfileViewModel", "Starting Cloudinary upload...")
                            }

                            override fun onProgress(requestId: String?, bytes: Long, totalBytes: Long) {}

                            override fun onSuccess(
                                requestId: String?,
                                resultData: MutableMap<Any?, Any?>?
                            ) {
                                val url = resultData?.get("secure_url") as? String
                                if (url != null) {
                                    Log.d("ProfileViewModel", "Uploaded to Cloudinary: $url")
                                    viewModelScope.launch {
                                        try {
                                            db.collection("users").document(uid)
                                                .update("profileImageUrl", url)
                                                .await()

                                            _user.value = _user.value.copy(profileImageUrl = url)
                                            updateState = ProfileState.Success
                                        } catch (e: Exception) {
                                            Log.e("ProfileViewModel", "Firestore update failed: ${e.message}", e)
                                            updateState =
                                                ProfileState.Error("Firestore update failed: ${e.message}")
                                        }
                                    }
                                } else {
                                    Log.e("ProfileViewModel", "Upload succeeded but no URL was returned.")
                                    updateState = ProfileState.Error("Upload succeeded but no URL")
                                }
                            }

                            override fun onError(requestId: String?, error: ErrorInfo?) {
                                Log.e("ProfileViewModel", "Cloudinary upload failed for request ID $requestId. Error: ${error?.description}. Full Error Info: ${error?.toString()}")
                                updateState =
                                    ProfileState.Error(error?.description ?: "Cloudinary upload failed")
                            }

                            override fun onReschedule(requestId: String?, error: ErrorInfo?) {
                                Log.e("ProfileViewModel", "Cloudinary upload rescheduled for request ID $requestId. Error: ${error?.description}. Full Error Info: ${error?.toString()}")
                                updateState =
                                    ProfileState.Error("Rescheduled: ${error?.description}")
                            }
                        })

                        .dispatch()
                }
            } catch (t: Throwable) {
                Log.e("ProfileViewModel", "Processing failed.", t)
                updateState = ProfileState.Error(t.localizedMessage ?: "Processing failed")
            }
        }
    }



    private fun loadBitmap(resolver: ContentResolver, uri: Uri): Bitmap {
        resolver.openInputStream(uri).use { ins ->
            return BitmapFactory.decodeStream(ins!!)
        }
    }

    private fun applyBrightness(src: Bitmap, delta: Float): Bitmap {
        val scale = 1f
        val translate = 255f * delta
        val cm = ColorMatrix(
            floatArrayOf(
                scale, 0f, 0f, 0f, translate,
                0f, scale, 0f, 0f, translate,
                0f, 0f, scale, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )
        )
        val ret = Bitmap.createBitmap(src.width, src.height, src.config ?: Bitmap.Config.ARGB_8888)
        val canvas = Canvas(ret)
        val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(cm) }
        canvas.drawBitmap(src, 0f, 0f, paint)
        return ret
    }

    fun resizeBitmap(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        val ratio = minOf(maxWidth.toFloat() / bitmap.width, maxHeight.toFloat() / bitmap.height)
        if (ratio >= 1f) return bitmap // no resize needed
        val width = (bitmap.width * ratio).toInt()
        val height = (bitmap.height * ratio).toInt()
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    companion object {
        private const val SIGNATURE_URL = "https://auth-server-imagekit-for-ripplechat.onrender.com/cloudinary-auth"
    }
}
