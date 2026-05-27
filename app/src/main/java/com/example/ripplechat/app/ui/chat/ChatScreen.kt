package com.example.ripplechat.app.ui.chat

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import com.google.android.gms.location.LocationServices
import android.annotation.SuppressLint
import android.content.Intent
import androidx.compose.material.icons.filled.LocationOn
import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import java.io.File
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.ripplechat.app.data.model.ChatMessage
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    navController: NavController,
    chatId: String,
    peerUid: String,
    peerName: String,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val ctx = LocalContext.current
    val messages by viewModel.messages.collectAsState()
    val typing by viewModel.otherTyping.collectAsState()
    val peerOnline by viewModel.peerOnline.collectAsState()
    val peerLastSeen by viewModel.peerLastSeen.collectAsState()
    val isDeleting by viewModel.isDeletingMessage.collectAsState()
    val isUploading by viewModel.isUploadingMedia.collectAsState()
    val uploadError by viewModel.uploadError.collectAsState()

    val myUid = viewModel.currentUserId

    var text by remember { mutableStateOf("") }
    val coroutine = rememberCoroutineScope()
    var sending by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    var messageToEdit by remember { mutableStateOf<ChatMessage?>(null) }
    var editInput by remember { mutableStateOf("") }

    // --- MEDIA PREVIEW STATE ---
    var showMediaPreview by remember { mutableStateOf(false) }
    var pickedUri by remember { mutableStateOf<Uri?>(null) }
    
    // --- LOCATION SHARING STATE ---
    var showGpsDialog by remember { mutableStateOf(false) }

    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(ctx) }
    val locationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        val fine = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarse = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        if (fine || coarse) {
            @SuppressLint("MissingPermission")
            fusedLocationClient.getCurrentLocation(com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { location ->
                    if (location != null) {
                        viewModel.sendLocationMessage(location.latitude, location.longitude)
                    } else {
                        showGpsDialog = true
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(ctx, "Failed to get location", Toast.LENGTH_SHORT).show()
                }
        } else {
            Toast.makeText(ctx, "Location permission is required to share location", Toast.LENGTH_SHORT).show()
        }
    }
    
    // --- AUDIO RECORDING STATE ---
    var isRecording by remember { mutableStateOf(false) }
    var mediaRecorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var audioOutputFile by remember { mutableStateOf<File?>(null) }
    val recordPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (!isGranted) {
            Toast.makeText(ctx, "Microphone permission required", Toast.LENGTH_SHORT).show()
        }
    }

    // --- FULL SCREEN VIEWER STATE ---
    var fullScreenImageUrl by remember { mutableStateOf<String?>(null) }
    var fullScreenVideoUrl by remember { mutableStateOf<String?>(null) }
    var fullScreenLocationUrl by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(chatId) { viewModel.init(chatId, peerUid) }
    DisposableEffect(Unit) { onDispose { viewModel.removeListeners(); viewModel.closeChat() } }

    // --- SCROLL FIX: JUMP on initial load, ANIMATE on new message ---
    val firstLoadScrollDone = remember { mutableStateOf(false) }
    LaunchedEffect(messages.isNotEmpty()) {
        if (messages.isNotEmpty() && !firstLoadScrollDone.value) {
            listState.scrollToItem(messages.lastIndex)
            firstLoadScrollDone.value = true
        }
    }
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty() && firstLoadScrollDone.value && !listState.isScrollInProgress) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    // --- ERROR TOAST ---
    LaunchedEffect(uploadError) {
        uploadError?.let { msg ->
            Toast.makeText(ctx, "Upload Failed: $msg", Toast.LENGTH_LONG).show()
            viewModel.clearUploadError()
        }
    }

    // --- MEDIA LAUNCHER (TRIGGERS PREVIEW) ---
    val mediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            if (uri != null) {
                pickedUri = uri
                showMediaPreview = true
            }
        }
    )

    // --- MAIN SCAFFOLD ---
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(peerName, style = MaterialTheme.typography.titleMedium)
                            if (typing) {
                                Text("$peerName is typing...", style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.primary))
                            } else if (!peerOnline) {
                                Text(
                                    peerLastSeen?.let { "Last seen: ${SimpleDateFormat("hh:mm a").format(Date(it))}" } ?: "Offline",
                                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                                )
                            } else {
                                Text("Online", style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)))
                            }
                        }
                        // Status Indicator (Simplified)
                        Box(modifier = Modifier.padding(horizontal = 8.dp).size(10.dp).clip(CircleShape).background(if (peerOnline) Color.Green else MaterialTheme.colorScheme.error))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Close Chat Action
                    IconButton(onClick = {
                        viewModel.removeListeners()
                        viewModel.closeChat()
                        navController.navigate("dashboard") {
                            popUpTo(navController.graph.startDestinationId) { inclusive = true }
                        }
                    }) {
                        Icon(Icons.Default.Close, contentDescription = "Close Chat")
                    }
                }
            )
        }
    ) { padding ->



        // --- EDIT MESSAGE DIALOG --- (Renders on top of content but under loader)
        messageToEdit?.let { msg ->
            AlertDialog(
                onDismissRequest = { messageToEdit = null },
                title = { Text("Edit Message") },
                text = {
                    OutlinedTextField(
                        value = editInput,
                        onValueChange = { editInput = it },
                        label = { Text("New Message") },
                        singleLine = false,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    Button(onClick = { viewModel.editExistingMessage(msg.messageId, editInput.trim()); messageToEdit = null }, enabled = editInput.isNotBlank() && editInput.trim() != msg.text.trim()) { Text("Save") }
                },
                dismissButton = { TextButton(onClick = { messageToEdit = null }) { Text("Cancel") } }
            )
        }

        // --- GPS SETTINGS DIALOG ---
        if (showGpsDialog) {
            AlertDialog(
                onDismissRequest = { showGpsDialog = false },
                title = { Text("Enable GPS") },
                text = { Text("Your GPS seems to be turned off. Do you want to open settings to turn it on?") },
                confirmButton = {
                    Button(onClick = { 
                        showGpsDialog = false
                        ctx.startActivity(Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                    }) {
                        Text("Okay")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { 
                        showGpsDialog = false
                        Toast.makeText(ctx, "Cannot share location without GPS", Toast.LENGTH_SHORT).show()
                    }) {
                        Text("No")
                    }
                }
            )
        }

        // --- MAIN CHAT CONTENT ---
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                contentPadding = PaddingValues(bottom = 8.dp)
            ) {
                items(messages, key = { it.messageId }) { msg ->
                    val isMine = msg.senderId == myUid
                    MessageRow(
                        message = msg,
                        isMine = isMine,
                        onImageClick = { url, type ->
                            if (type == "image") {
                                fullScreenImageUrl = url
                            } else if (type == "video") {
                                fullScreenVideoUrl = url
                            } else if (type == "location") {
                                fullScreenLocationUrl = url
                            } else {
                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW)
                                val extension = android.webkit.MimeTypeMap.getFileExtensionFromUrl(url) ?: ""
                                val mimeType = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "*/*"
                                intent.setDataAndType(Uri.parse(url), mimeType)
                                intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                val chooser = android.content.Intent.createChooser(intent, "Open Document")
                                try {
                                    ctx.startActivity(chooser)
                                } catch (e: Exception) {
                                    Toast.makeText(ctx, "No app found to open this file", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onEditClicked = {
                            messageToEdit = msg
                            editInput = msg.text
                        },
                        onDeleteClicked = {
                            viewModel.deleteExistingMessage(msg.messageId)
                        }
                    )
                }
            }

            // Input row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .imePadding(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        viewModel.updateTyping(true)
                        viewModel.scheduleStopTyping()
                    },
                    keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (text.isNotBlank() && !sending && !isUploading) {
                            sending = true
                            coroutine.launch { viewModel.sendMessage(text.trim()); text = ""; viewModel.updateTyping(false); sending = false }
                        }
                    }),
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Message...") },
                    singleLine = true,
                    enabled = !isUploading
                )
                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = { mediaLauncher.launch("*/*") },
                    enabled = !isUploading
                ) {
                    Icon(
                        imageVector = Icons.Default.AttachFile,
                        contentDescription = "Attach Media",
                        modifier = Modifier.size(24.dp)
                    )
                }
                IconButton(
                    onClick = { 
                        locationPermissionLauncher.launch(
                            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                        )
                    },
                    enabled = !isUploading
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = "Share Location",
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                if (text.isNotBlank()) {
                    Button(
                        onClick = {
                            if (!sending && !isUploading) {
                                sending = true
                                coroutine.launch { viewModel.sendMessage(text.trim()); text = ""; viewModel.updateTyping(false); sending = false }
                            }
                        },
                        enabled = !sending && !isUploading
                    ) {
                        Text("Send")
                    }
                } else {
                    val permissionCheck = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO)
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(if (isRecording) Color.Red else MaterialTheme.colorScheme.primary)
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onPress = {
                                        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
                                            recordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                            return@detectTapGestures
                                        }
                                        
                                        val file = File(ctx.cacheDir, "audio_${System.currentTimeMillis()}.m4a")
                                        audioOutputFile = file
                                        isRecording = true
                                        
                                        val recorder = MediaRecorder().apply {
                                            setAudioSource(MediaRecorder.AudioSource.MIC)
                                            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                                            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                                            setOutputFile(file.absolutePath)
                                            try { prepare(); start() } catch (e: Exception) { e.printStackTrace() }
                                        }
                                        mediaRecorder = recorder
                                        
                                        tryAwaitRelease()
                                        
                                        isRecording = false
                                        try {
                                            mediaRecorder?.stop()
                                            mediaRecorder?.release()
                                        } catch (e: Exception) {}
                                        mediaRecorder = null
                                        
                                        audioOutputFile?.let {
                                            if (it.exists() && it.length() > 0) {
                                                viewModel.uploadMediaFile(ctx.contentResolver, Uri.fromFile(it), "")
                                            }
                                        }
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Mic, contentDescription = "Record Audio", tint = Color.White)
                    }
                }
            }
        }

        // --- FULL SCREEN IMAGE VIEWER (Renders on top of content/loader) ---
        fullScreenImageUrl?.let { url ->
            FullScreenImageViewer(
                imageUrl = url,
                onDismiss = { fullScreenImageUrl = null }
            )
        }

        // --- FULL SCREEN VIDEO VIEWER ---
        fullScreenVideoUrl?.let { url ->
            FullScreenVideoViewer(
                url = url,
                onClose = { fullScreenVideoUrl = null }
            )
        }

        // --- FULL SCREEN LOCATION VIEWER ---
        fullScreenLocationUrl?.let { url ->
            FullScreenLocationViewer(
                locationUrl = url,
                onClose = { fullScreenLocationUrl = null }
            )
        }

        // --- MEDIA PREVIEW DIALOG (Renders on top of content/loader) ---
        if (showMediaPreview && pickedUri != null) {
            MediaPreviewDialog(
                pickedUri = pickedUri!!,
                onDismiss = { showMediaPreview = false; pickedUri = null },
                onUpload = { caption ->
                    val resolver = ctx.contentResolver
                    viewModel.uploadMediaFile(resolver, pickedUri!!, caption)
                    showMediaPreview = false
                    pickedUri = null
                }
            )
        }

        // --- GLOBAL LOADER OVERLAY (FIXED Z-ORDER: Renders on top of everything) ---
        if (isDeleting || isUploading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f))
                    .clickable(enabled = false) {}
                    .padding(padding) // Apply TopBar padding
                    .statusBarsPadding(), // Ensures it doesn't overlap system status bar
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.tertiary)
            }
        }
    }
}

// --- FULL SCREEN IMAGE VIEWER ---
@Composable
fun FullScreenImageViewer(
    imageUrl: String,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(onClick = onDismiss)
            .statusBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = "Full screen image",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )
        // Dismiss Button (Cross Icon)
        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.5f))
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Close",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

// --- MEDIA PREVIEW DIALOG ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaPreviewDialog(
    pickedUri: Uri,
    onDismiss: () -> Unit,
    onUpload: (caption: String) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var caption by remember { mutableStateOf("") }
    
    val mimeType = remember(pickedUri) { context.contentResolver.getType(pickedUri) ?: "" }
    val isImage = mimeType.startsWith("image/")
    val isVideo = mimeType.startsWith("video/")
    
    val title = when {
        isImage -> "Preview Image"
        isVideo -> "Preview Video"
        else -> "Send Document"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onUpload(caption.trim()) }) { Text("Send") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (isImage) {
                    AsyncImage(
                        model = pickedUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .clip(MaterialTheme.shapes.medium)
                    )
                } else if (isVideo) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .background(Color.Black, MaterialTheme.shapes.medium),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.PlayCircleOutline, contentDescription = "Video", modifier = Modifier.size(64.dp), tint = Color.White)
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
                        Icon(Icons.Default.InsertDriveFile, contentDescription = "Document", modifier = Modifier.size(48.dp))
                        Spacer(Modifier.width(16.dp))
                        Text("Ready to send document")
                    }
                }
                
                OutlinedTextField(
                    value = caption,
                    onValueChange = { caption = it },
                    label = { Text("Caption (Optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        }
    )
}

// --- MESSAGE ROW ---
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageRow(
    message: ChatMessage,
    isMine: Boolean,
    onImageClick: (String, String) -> Unit,
    onEditClicked: () -> Unit,
    onDeleteClicked: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val timestampText = remember(message.timestamp) {
        SimpleDateFormat("hh:mm a").format(Date(message.timestamp))
    }

    val isMediaMessage = message.isMedia && !message.mediaUrl.isNullOrEmpty()

    val bubbleColor = if (isMediaMessage) Color.Transparent else if (isMine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.primary
    val textColor = if (isMediaMessage) MaterialTheme.colorScheme.onSurface else if (isMine) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onPrimary

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = bubbleColor,
            shape = if (isMediaMessage) RoundedCornerShape(8.dp) else MaterialTheme.shapes.medium,
            tonalElevation = if (isMediaMessage) 0.dp else 3.dp,
            shadowElevation = if (isMediaMessage) 0.dp else 2.dp,
            modifier = Modifier
                .padding(vertical = 4.dp, horizontal = 6.dp)
                .widthIn(min = 80.dp, max = 280.dp)
                .combinedClickable(
                    onClick = {
                        if (isMediaMessage && message.mediaUrl != null) {
                            onImageClick(message.mediaUrl, message.mediaType ?: "image")
                        }
                    },
                    onLongClick = { showMenu = true }
                )
        ) {
            Column(modifier = Modifier.padding(all = if (isMediaMessage) 4.dp else 10.dp)) {

                if (isMediaMessage && message.mediaUrl != null) {
                    val displayUrl = if (message.mediaType == "video" && message.mediaUrl.contains("cloudinary")) {
                        message.mediaUrl.substringBeforeLast(".") + ".jpg"
                    } else {
                        message.mediaUrl
                    }

                    if (message.mediaType == "raw") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .padding(4.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black.copy(alpha = 0.1f))
                                .padding(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.InsertDriveFile, contentDescription = "Document", modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = message.text.takeIf { it.isNotBlank() } ?: "Document",
                                color = textColor,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    } else if (message.mediaType == "audio") {
                        AudioPlayerBubble(message = message)
                    } else if (message.mediaType == "location") {
                        val coords = message.mediaUrl.split(",")
                        if (coords.size == 2) {
                            val lat = coords[0]
                            val lng = coords[1]
                            
                            // Calculate OSM Tile X and Y
                            val zoom = 15
                            val n = Math.pow(2.0, zoom.toDouble())
                            val x = ((lng.toDouble() + 180.0) / 360.0 * n).toInt()
                            val latRad = lat.toDouble() * Math.PI / 180.0
                            val y = ((1.0 - Math.log(Math.tan(latRad) + 1.0 / Math.cos(latRad)) / Math.PI) / 2.0 * n).toInt()
                            
                            // Using CartoDB Voyager tiles which are more permissive and look very clean
                            val tileUrl = "https://a.basemaps.cartocdn.com/rastertiles/voyager/$zoom/$x/$y.png"
                            
                            val imageRequest = coil.request.ImageRequest.Builder(ctx)
                                .data(tileUrl)
                                .addHeader("User-Agent", "RippleChatApp/1.0")
                                .build()
                            
                            Box(
                                modifier = Modifier
                                    .size(200.dp, 150.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        onImageClick(message.mediaUrl, "location")
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                // 1. Map Image
                                AsyncImage(
                                    model = imageRequest,
                                    contentDescription = "Map Snippet",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                // 2. Red Pin in the center
                                Icon(
                                    Icons.Default.LocationOn,
                                    contentDescription = "Pin",
                                    modifier = Modifier.size(36.dp).padding(bottom = 18.dp),
                                    tint = Color.Red
                                )
                            }
                        }
                    } else {
                        Box(contentAlignment = Alignment.Center) {
                            AsyncImage(
                                model = displayUrl,
                                contentDescription = message.text,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .sizeIn(minWidth = 200.dp, minHeight = 200.dp, maxHeight = 300.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                            if (message.mediaType == "video") {
                                Icon(Icons.Default.PlayCircleOutline, contentDescription = "Play Video", modifier = Modifier.size(48.dp), tint = Color.White)
                            }
                        }
                        if (message.text.isNotBlank() && !message.text.startsWith("Uploading")) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(message.text, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 4.dp))
                        }
                    }
                } else {
                    // --- TEXT DISPLAY ---
                    Text(message.text, color = textColor)
                }

                // --- TIMESTAMP ROW ---
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth().padding(top = if (isMediaMessage && message.text.isNotBlank()) 4.dp else 0.dp)
                ) {
                    if (message.edited) {
                        Text("(Edited)", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(end = 4.dp), color = Color.Gray)
                    }
                    Text(timestampText, style = MaterialTheme.typography.labelSmall, color = if (isMediaMessage) Color.Gray else textColor.copy(alpha = 0.6f))
                }

                // --- DROPDOWN MENU ---
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    if (isMine) {
                        DropdownMenuItem(text = { Text("Edit") }, onClick = { showMenu = false; onEditClicked() }, enabled = !isMediaMessage)
                    }
                    DropdownMenuItem(text = { Text("Delete") }, onClick = { showMenu = false; onDeleteClicked() })
                }
            }
        }
    }
}

@Composable
fun FullScreenVideoViewer(url: String, onClose: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onClose() }) // Tap outside video to close
            },
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { context ->
                android.widget.VideoView(context).apply {
                    setVideoPath(url)
                    val mediaController = android.widget.MediaController(context)
                    mediaController.setAnchorView(this)
                    setMediaController(mediaController)
                    setOnPreparedListener { mp ->
                        mp.isLooping = true
                        start()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().wrapContentHeight()
        )
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        ) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
        }
    }
}

@Composable
fun FullScreenLocationViewer(locationUrl: String, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val coords = locationUrl.split(",")
    if (coords.size != 2) return

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onClose() })
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(16.dp)
        ) {
            Icon(Icons.Default.LocationOn, contentDescription = "Location", modifier = Modifier.size(80.dp), tint = Color.Red)
            Spacer(modifier = Modifier.height(16.dp))
            Text("Shared Location", color = Color.White, style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = {
                val lat = coords[0]
                val lng = coords[1]
                val gmmIntentUri = Uri.parse("geo:${lat},${lng}?q=${lat},${lng}(Shared+Location)")
                val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
                try {
                    ctx.startActivity(mapIntent)
                } catch(e: Exception) {
                    Toast.makeText(ctx, "Google Maps is not installed", Toast.LENGTH_SHORT).show()
                }
            }) {
                Text("Show on Maps")
            }
        }
        
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        ) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
        }
    }
}

@Composable
fun AudioPlayerBubble(message: ChatMessage) {
    var isPlaying by remember { mutableStateOf(false) }
    var mediaPlayer by remember { mutableStateOf<android.media.MediaPlayer?>(null) }
    var progress by remember { mutableStateOf(0f) }
    
    LaunchedEffect(isPlaying) {
        while (isPlaying && mediaPlayer != null) {
            val dur = mediaPlayer?.duration ?: 0
            if (dur > 0) {
                progress = mediaPlayer!!.currentPosition.toFloat() / dur
            }
            kotlinx.coroutines.delay(50)
        }
    }
    
    DisposableEffect(message.mediaUrl) {
        onDispose {
            mediaPlayer?.release()
        }
    }
    
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth(0.7f)
            .padding(4.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color.Black.copy(alpha = 0.1f))
            .padding(vertical = 8.dp, horizontal = 12.dp)
    ) {
        IconButton(
            onClick = {
                if (isPlaying) {
                    mediaPlayer?.pause()
                    isPlaying = false
                } else {
                    if (mediaPlayer == null) {
                        mediaPlayer = android.media.MediaPlayer().apply {
                            setDataSource(message.mediaUrl)
                            prepareAsync()
                            setOnPreparedListener { 
                                start()
                                isPlaying = true
                            }
                            setOnCompletionListener { 
                                isPlaying = false
                            }
                        }
                    } else {
                        mediaPlayer?.start()
                        isPlaying = true
                    }
                }
            },
            modifier = Modifier.size(36.dp).background(MaterialTheme.colorScheme.primary, CircleShape)
        ) {
            Icon(
                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = "Play/Pause",
                tint = Color.White
            )
        }
        Spacer(Modifier.width(12.dp))
        // Dynamic visualization
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.weight(1f).height(4.dp).clip(CircleShape),
            color = MaterialTheme.colorScheme.primary,
            trackColor = Color.Gray.copy(alpha = 0.5f)
        )
        Spacer(Modifier.width(8.dp))
        Icon(Icons.Default.Mic, contentDescription = "Mic", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
    }
}