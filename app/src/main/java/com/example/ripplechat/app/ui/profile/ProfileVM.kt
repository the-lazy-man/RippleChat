package com.example.ripplechat.app.ui.profile

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

data class ProfileUser(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val profileImageUrl: String? = null
)
sealed class ProfileState { object Idle: ProfileState(); object Loading: ProfileState(); object Success: ProfileState(); data class Error(val message: String): ProfileState() }

@HiltViewModel
class ProfileViewModel @Inject constructor(): ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance().reference

    private val _user = MutableStateFlow(ProfileUser())
    val user = _user.asStateFlow()

    var updateState: ProfileState = ProfileState.Idle
        private set

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
                val bmp = withContext(Dispatchers.IO) {
                    loadBitmap(resolver, sourceUri)
                }

                // 2. Apply brightness (CPU bound → use Default dispatcher)
                val processed = withContext(Dispatchers.Default) {
                    applyBrightness(bmp, brightness)
                }

                // 3. Downscale before compression to avoid OOM / debugger detach
                val maxDim = 1024 // pixels (tune this depending on your needs)
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

                // 4. Compress in background (CPU bound → Default dispatcher)
                val uploadBytes = withContext(Dispatchers.Default) {
                    val stream = ByteArrayOutputStream()
                    try {
                        scaled.compress(Bitmap.CompressFormat.JPEG, 85, stream) // 85 keeps good quality
                        stream.toByteArray()
                    } finally {
                        stream.close()
                    }
                }

                // 5. Upload to Firebase Storage (I/O bound)
                val path = "profilePics/$uid.jpg"
                val ref = storage.child(path)
                ref.putBytes(uploadBytes).await()

                // 6. Get download URL
                val url = ref.downloadUrl.await().toString()
                Log.d("ProfileViewModel", "Uploaded profile picture: $url")

                // 7. Update Firestore
                db.collection("users").document(uid)
                    .update("profileImageUrl", url)
                    .await()

                // 8. Update local state
                _user.value = _user.value.copy(profileImageUrl = url)
                updateState = ProfileState.Success
            } catch (t: Throwable) {
                updateState = ProfileState.Error(t.localizedMessage ?: "Upload failed")
            }
        }
    }

    private fun loadBitmap(resolver: ContentResolver, uri: Uri): Bitmap {
        resolver.openInputStream(uri).use { ins ->
            return BitmapFactory.decodeStream(ins!!)
        }
    }

    private fun applyBrightness(src: Bitmap, delta: Float): Bitmap {
        // delta -1..+1 -> convert to CM add
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

}
