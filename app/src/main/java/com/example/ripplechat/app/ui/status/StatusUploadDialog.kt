package com.example.ripplechat.app.ui.status

import android.content.ContentResolver
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.ripplechat.app.data.model.MediaType

@Composable
fun StatusUploadDialog(
    contentResolver:
    ContentResolver,
    onDismiss: () -> Unit,
    onUpload: (MediaType, Uri?, String?, String?) -> Unit
) {
    var selectedMediaType by remember { mutableStateOf<MediaType?>(null) }
    var selectedMediaUri by remember { mutableStateOf<Uri?>(null) }
    var caption by remember { mutableStateOf("") }
    var textContent by remember { mutableStateOf("") }
    var backgroundColor by remember { mutableStateOf("#6200EE") }
    var isUploading by remember { mutableStateOf(false) }

    // Photo picker launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let {
            selectedMediaUri = it
            selectedMediaType = MediaType.IMAGE
        }
    }

    // Video picker launcher
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let {
            selectedMediaUri = it
            selectedMediaType = MediaType.VIDEO
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.8f),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Add Status",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Content based on selection
                when {
                    selectedMediaUri != null && selectedMediaType == MediaType.IMAGE -> {
                        // Image preview
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            AsyncImage(
                                model = selectedMediaUri,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Caption input
                        OutlinedTextField(
                            value = caption,
                            onValueChange = { caption = it },
                            label = { Text("Caption (optional)") },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 3
                        )
                    }

                    selectedMediaUri != null && selectedMediaType == MediaType.VIDEO -> {
                        // Video preview placeholder
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .background(Color.Black),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.VideoLibrary,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = Color.White
                                )
                                Text(
                                    "Video selected",
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Caption input
                        OutlinedTextField(
                            value = caption,
                            onValueChange = { caption = it },
                            label = { Text("Caption (optional)") },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 3
                        )
                    }

                    selectedMediaType == MediaType.TEXT -> {
                        // Text status creator
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .background(
                                    Color(android.graphics.Color.parseColor(backgroundColor)),
                                    RoundedCornerShape(8.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            OutlinedTextField(
                                value = textContent,
                                onValueChange = { textContent = it },
                                placeholder = { Text("Type your status...") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                textStyle = MaterialTheme.typography.headlineSmall.copy(
                                    color = Color.White
                                ),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = Color.Transparent,
                                    unfocusedBorderColor = Color.Transparent
                                ),
                                maxLines = 5
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Color picker (simple version)
                        Text("Background Color", style = MaterialTheme.typography.labelMedium)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("#6200EE", "#03DAC5", "#FF5722", "#4CAF50", "#FFC107", "#E91E63").forEach { color ->
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(
                                            Color(android.graphics.Color.parseColor(color)),
                                            RoundedCornerShape(20.dp)
                                        )
                                        .clickable { backgroundColor = color }
                                )
                            }
                        }
                    }

                    else -> {
                        // Selection screen
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                "Choose status type:",
                                style = MaterialTheme.typography.titleMedium
                            )

                            StatusTypeCard(
                                icon = Icons.Default.Image,
                                title = "Photo",
                                description = "Share an image",
                                onClick = {
                                    imagePickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                            )

                            StatusTypeCard(
                                icon = Icons.Default.VideoLibrary,
                                title = "Video",
                                description = "Share a video",
                                onClick = {
                                    videoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                    )
                                }
                            )

                            StatusTypeCard(
                                icon = Icons.Default.TextFields,
                                title = "Text",
                                description = "Create a text status",
                                onClick = { selectedMediaType = MediaType.TEXT }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Upload button
                if (selectedMediaType != null) {
                    Button(
                        onClick = {
                            isUploading = true
                            when (selectedMediaType) {
                                MediaType.IMAGE, MediaType.VIDEO -> {
                                    selectedMediaUri?.let { uri ->
                                        onUpload(selectedMediaType!!, uri, caption.ifBlank { null }, null)
                                    }
                                }
                                MediaType.TEXT -> {
                                    if (textContent.isNotBlank()) {
                                        onUpload(MediaType.TEXT, null, textContent, backgroundColor)
                                    }
                                }
                                else -> {}
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isUploading && when (selectedMediaType) {
                            MediaType.TEXT -> textContent.isNotBlank()
                            MediaType.IMAGE, MediaType.VIDEO -> selectedMediaUri != null
                            else -> false
                        }
                    ) {
                        if (isUploading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text("Post Status")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusTypeCard(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
