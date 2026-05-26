package com.example.ripplechat.app.ui.status

import android.content.ContentResolver
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ripplechat.app.data.model.MediaType
import com.example.ripplechat.app.data.model.Status
import com.example.ripplechat.app.data.model.firebase.FirebaseSource
import com.example.ripplechat.app.util.CloudinaryUploadHelper
import com.example.ripplechat.data.repository.ChatRepository
import com.google.firebase.firestore.ListenerRegistration
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StatusVM @Inject constructor(
    private val firebase: FirebaseSource,
    private val chatRepository: ChatRepository
) : ViewModel() {

    private val _contactStatuses = MutableStateFlow<Map<String, List<Status>>>(emptyMap())
    val contactStatuses = _contactStatuses.asStateFlow()

    private val _myStatuses = MutableStateFlow<List<Status>>(emptyList())
    val myStatuses = _myStatuses.asStateFlow()

    // Cache for user names: userId -> userName
    private val _userNames = MutableStateFlow<Map<String, String>>(emptyMap())
    val userNames = _userNames.asStateFlow()

    private var statusListeners = mutableListOf<ListenerRegistration>()
    private var myStatusListener: ListenerRegistration? = null

    init {
        fetchMyStatuses()
    }

    /**
     * Listen to statuses of contacts and fetch their names.
     */
    fun startListeningContactStatuses(contactIds: List<String>) {
        statusListeners.forEach { it.remove() }
        statusListeners.clear()
        
        statusListeners.addAll(chatRepository.listenStatuses(contactIds) { updatedMap ->
            _contactStatuses.value = updatedMap
            
            // Fetch names for users who have statuses
            val userIdsWithStatuses = updatedMap.keys
            fetchUserNames(userIdsWithStatuses.toList())
        })
    }

    /**
     * Fetch and cache user names for the given UIDs.
     */
    private fun fetchUserNames(uids: List<String>) {
        viewModelScope.launch {
            try {
                val currentNames = _userNames.value.toMutableMap()
                
                uids.forEach { uid ->
                    if (!currentNames.containsKey(uid)) {
                        val userInfo = firebase.getUserInfo(uid)
                        if (userInfo != null) {
                            currentNames[uid] = userInfo["name"] as? String ?: "Unknown"
                        }
                    }
                }
                
                _userNames.value = currentNames
            } catch (e: Exception) {
                Log.e("StatusVM", "Error fetching user names: ${e.message}")
            }
        }
    }

    /**
     * Listen to my own statuses.
     */
    private fun fetchMyStatuses() {
        val uid = firebase.currentUserUid() ?: return
        myStatusListener?.remove()
        myStatusListener = chatRepository.listenStatuses(listOf(uid)) { updatedMap ->
            _myStatuses.value = updatedMap[uid] ?: emptyList()
        }.firstOrNull()
    }

    /**
     * Add a new status update (internal helper).
     */
    private fun addStatus(
        mediaType: MediaType = MediaType.TEXT,
        mediaUrl: String? = null,
        caption: String? = null,
        backgroundColor: String? = null
    ) {
        val uid = firebase.currentUserUid() ?: return
        val now = System.currentTimeMillis()
        val status = Status(
            userId = uid,
            mediaType = mediaType,
            mediaUrl = mediaUrl,
            caption = caption,
            backgroundColor = backgroundColor,
            timestamp = now,
            expiresAt = now + (24 * 60 * 60 * 1000), // 24 hours later
            duration = when (mediaType) {
                MediaType.TEXT -> 5000
                MediaType.IMAGE -> 5000
                MediaType.VIDEO -> 30000 // Default max, will be updated with actual video duration
            }
        )
        
        viewModelScope.launch {
            try {
                chatRepository.addStatus(uid, status)
            } catch (e: Exception) {
                Log.e("StatusVM", "Error adding status: ${e.message}")
            }
        }
    }

    /**
     * Upload status with media using Cloudinary (NEW: Correct implementation).
     */
    fun uploadStatusWithMedia(
        mediaType: MediaType,
        mediaUri: Uri?,
        caption: String?,
        backgroundColor: String?,
        contentResolver: ContentResolver,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val uid = firebase.currentUserUid() ?: return

        viewModelScope.launch {
            try {
                when (mediaType) {
                    MediaType.IMAGE -> {
                        if (mediaUri == null) {
                            onError("No image selected")
                            return@launch
                        }

                        // 1. Convert URI to compressed bytes
                        val imageBytes = CloudinaryUploadHelper.uriToImageBytes(
                            uri = mediaUri,
                            contentResolver = contentResolver,
                            maxDimension = 1024,
                            quality = 85
                        )

                        // 2. Upload to Cloudinary
                        val publicId = "status_${uid}_${System.currentTimeMillis()}"
                        val cdnUrl = CloudinaryUploadHelper.uploadImage(
                            bytes = imageBytes,
                            publicId = publicId
                        )

                        // 3. Create status with CDN URL
                        addStatus(
                            mediaType = MediaType.IMAGE,
                            mediaUrl = cdnUrl,
                            caption = caption,
                            backgroundColor = null
                        )
                        onSuccess()
                    }
                    
                    MediaType.VIDEO -> {
                        if (mediaUri == null) {
                            onError("No video selected")
                            return@launch
                        }

                        // 1. Upload video to Cloudinary
                        val publicId = "status_${uid}_${System.currentTimeMillis()}"
                        val cdnUrl = CloudinaryUploadHelper.uploadVideo(
                            uri = mediaUri,
                            publicId = publicId,
                            contentResolver = contentResolver
                        )

                        // 2. Create status with CDN URL
                        addStatus(
                            mediaType = MediaType.VIDEO,
                            mediaUrl = cdnUrl,
                            caption = caption,
                            backgroundColor = null
                        )
                        onSuccess()
                    }
                    
                    MediaType.TEXT -> {
                        // Text status doesn't need media upload
                        addStatus(
                            mediaType = MediaType.TEXT,
                            mediaUrl = null,
                            caption = caption,
                            backgroundColor = backgroundColor
                        )
                        onSuccess()
                    }
                }
            } catch (e: Exception) {
                Log.e("StatusVM", "Error uploading status: ${e.message}", e)
                onError(e.message ?: "Unknown error")
            }
        }
    }

    /**
     * Delete a status (removes from Firestore and deletes media from Storage if applicable).
     */
    fun deleteStatus(statusId: String) {
        viewModelScope.launch {
            try {
                val uid = firebase.currentUserUid() ?: return@launch
                
                // Find the status to check if it has media
                val status = _myStatuses.value.find { it.statusId == statusId }
                
                // Delete from Firestore
                firebase.deleteStatus(uid, statusId)
                
                // If it's a media status, delete the media file from Cloudinary
                if (status != null && status.mediaUrl != null && 
                    (status.mediaType == MediaType.IMAGE || status.mediaType == MediaType.VIDEO)) {
                    val resourceType = if (status.mediaType == MediaType.VIDEO) "video" else "image"
                    firebase.deleteStatusMedia(status.mediaUrl, resourceType)
                }
                
                Log.d("StatusVM", "Status deleted successfully: $statusId")
            } catch (e: Exception) {
                Log.e("StatusVM", "Error deleting status: ${e.message}", e)
            }
        }
    }

    /**
     * Update a text status (caption and/or background color).
     */
    fun updateStatus(statusId: String, newCaption: String, newBackgroundColor: String?) {
        viewModelScope.launch {
            try {
                val uid = firebase.currentUserUid() ?: return@launch
                
                val updates = mutableMapOf<String, Any?>()
                updates["caption"] = newCaption
                if (newBackgroundColor != null) {
                    updates["backgroundColor"] = newBackgroundColor
                }
                
                firebase.updateStatus(uid, statusId, updates)
                Log.d("StatusVM", "Status updated successfully: $statusId")
            } catch (e: Exception) {
                Log.e("StatusVM", "Error updating status: ${e.message}", e)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        statusListeners.forEach { it.remove() }
        myStatusListener?.remove()
    }
}
