package com.example.ripplechat.app.ui.profile

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.net.Uri
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
import javax.inject.Inject

data class ProfileUser(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val photoUrl: String? = null
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
                    photoUrl = it.getString("photoUrl")
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
                val bmp = withContext(Dispatchers.IO) { loadBitmap(resolver, sourceUri) }
                val processed = withContext(Dispatchers.Default) { applyBrightness(bmp, brightness) }
                val path = "profilePics/$uid.jpg"
                val ref = storage.child(path)

                val uploadBytes = withContext(Dispatchers.IO) {
                    val stream = java.io.ByteArrayOutputStream()
                    processed.compress(Bitmap.CompressFormat.JPEG, 90, stream)
                    stream.toByteArray()
                }

                ref.putBytes(uploadBytes).await()
                val url = ref.downloadUrl.await().toString()
                db.collection("users").document(uid)
                    .update("photoUrl", url)
                    .await()
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
}
