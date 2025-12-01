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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
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

    // --- FULL SCREEN VIEWER STATE ---
    var fullScreenImageUrl by remember { mutableStateOf<String?>(null) }


    LaunchedEffect(chatId) { viewModel.init(chatId, peerUid) }
    DisposableEffect(Unit) { onDispose { viewModel.removeListeners(); viewModel.closeChat() } }

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
                showMediaPreview = true // <-- CORRECT FIX: Trigger the dialog
            }
        }
    )

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
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = null,
                            tint = if (peerOnline) Color.Green else MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 8.dp).size(10.dp)
                        )
                    }
                },
                navigationIcon = {
                    Spacer(Modifier.width(7.dp))
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->

        // --- EDIT MESSAGE DIALOG ---
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
                    Button(
                        onClick = {
                            viewModel.editExistingMessage(msg.messageId, editInput.trim())
                            messageToEdit = null
                        },
                        enabled = editInput.isNotBlank() && editInput.trim() != msg.text.trim()
                    ) { Text("Save") }
                },
                dismissButton = {
                    TextButton(onClick = { messageToEdit = null }) { Text("Cancel") }
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
                        onImageClick = { url -> fullScreenImageUrl = url },
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
                            coroutine.launch {
                                viewModel.sendMessage(text.trim())
                                text = ""
                                viewModel.updateTyping(false)
                                sending = false
                            }
                        }
                    }),
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Message...") },
                    singleLine = true,
                    enabled = !isUploading
                )
                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = { mediaLauncher.launch("image/*") },
                    enabled = !isUploading,
                    modifier = Modifier.background(MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Attach Media",
                        modifier = Modifier
                            .rotate(90f)
                            .size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (text.isNotBlank() && !sending && !isUploading) {
                            sending = true
                            coroutine.launch {
                                viewModel.sendMessage(text.trim())
                                text = ""
                                viewModel.updateTyping(false)
                                sending = false
                            }
                        }
                    },
                    enabled = text.isNotBlank() && !sending && !isUploading
                ) {
                    Text("Send")
                }
            }
        }

        // --- GLOBAL LOADER OVERLAY (FIXED Z-ORDER: Renders on top of everything) ---
        if (isDeleting || isUploading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f))
                    .clickable(enabled = false) {},
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.tertiary)
            }
        }

        // --- FULL SCREEN IMAGE VIEWER (Highest Z-Order) ---
        fullScreenImageUrl?.let { url ->
            FullScreenImageViewer(
                imageUrl = url,
                onDismiss = { fullScreenImageUrl = null }
            )
        }

        // --- MEDIA PREVIEW DIALOG (Renders on top of everything) ---
        if (showMediaPreview && pickedUri != null) {
            MediaPreviewDialog(
                pickedUri = pickedUri!!,
                onDismiss = { showMediaPreview = false; pickedUri = null },
                onUpload = { caption ->
                    val resolver = ctx.contentResolver
                    viewModel.uploadMediaFile(resolver, pickedUri!!, caption) // Correct call
                    showMediaPreview = false
                    pickedUri = null
                }
            )
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
    var caption by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onUpload(caption.trim()) }) { Text("Send") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        title = { Text("Preview Image") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AsyncImage(
                    model = pickedUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(MaterialTheme.shapes.medium)
                )
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
    onImageClick: (String) -> Unit,
    onEditClicked: () -> Unit,
    onDeleteClicked: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
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
                            onImageClick(message.mediaUrl)
                        }
                    },
                    onLongClick = { showMenu = true }
                )
        ) {
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {

                if (isMediaMessage && message.mediaUrl != null) {
                    // --- MEDIA DISPLAY ---
                    AsyncImage(
                        model = message.mediaUrl,
                        contentDescription = message.text,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .sizeIn(minWidth = 150.dp, minHeight = 150.dp, maxHeight = 200.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .padding(bottom = if (message.text.isNotBlank() && message.text != "Uploading Image...") 6.dp else 0.dp)
                    )
                    // Display the accompanying text/caption below the image
                    if (message.text.isNotBlank() && message.text != "Uploading Image...") {
                        Text(message.text, color = MaterialTheme.colorScheme.onSurface)
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