package com.example.ripplechat.app.ui.chat

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


    LaunchedEffect(chatId) { viewModel.init(chatId, peerUid) }
    DisposableEffect(Unit) { onDispose { viewModel.removeListeners(); viewModel.closeChat() } }

    // --- SCROLL FIX: JUMP on initial load, ANIMATE on new message ---
    val firstLoadScrollDone = remember { mutableStateOf(false) }
    LaunchedEffect(messages.isNotEmpty()) {
        if (messages.isNotEmpty() && !firstLoadScrollDone.value) {
            listState.scrollToItem(messages.lastIndex) // Jump to bottom immediately
            firstLoadScrollDone.value = true
        }
    }
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty() && firstLoadScrollDone.value && !listState.isScrollInProgress) {
            listState.animateScrollToItem(messages.lastIndex) // Animate on new message
        }
    }

    // --- ERROR TOAST ---
    LaunchedEffect(uploadError) {
        uploadError?.let { msg ->
            Toast.makeText(ctx, "Upload Failed: $msg", Toast.LENGTH_LONG).show()
            viewModel.clearUploadError()
        }
    }

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
                        Icon(Icons.Filled.Close, contentDescription = null, tint = if (peerOnline) Color.Green else MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 8.dp).size(10.dp))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
//                actions = {
//                    IconButton(onClick = {
//                        viewModel.removeListeners()
//                        viewModel.closeChat()
//                        navController.navigate("dashboard") {
//                            popUpTo(navController.graph.startDestinationId) { inclusive = true }
//                        }
//                    }) {
//                        Icon(Icons.Default.Close, contentDescription = "Close Chat")
//                    }
//                }
            )
        }
    ) { padding ->

        // Global Delete/Upload Loader Overlay
        if (isDeleting || isUploading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(enabled = false) {}
                    .background(Color.Black.copy(alpha = 0.3f))
                    .imePadding()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.tertiary)
            }
        }

        // Edit Message Dialog
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

                val mediaLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.GetContent(),
                    onResult = { uri ->
                        if (uri != null) {
                            val resolver = ctx.contentResolver
                            viewModel.uploadMediaFile(resolver, uri)
                        }
                    }
                )
                IconButton(
                    onClick = { mediaLauncher.launch("image/*") },
                    enabled = !isUploading
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
    }
}

// Message Row with Media Display Logic
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageRow(
    message: ChatMessage,
    isMine: Boolean,
    onEditClicked: () -> Unit,
    onDeleteClicked: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val timestampText = remember(message.timestamp) {
        SimpleDateFormat("hh:mm a").format(Date(message.timestamp))
    }

    val bubbleColor = if (isMine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.primary
    val textColor = if (isMine) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onPrimary

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = bubbleColor,
            shape = MaterialTheme.shapes.medium,
            tonalElevation = 3.dp,
            shadowElevation = 2.dp,
            modifier = Modifier
                .padding(vertical = 4.dp, horizontal = 6.dp)
                .widthIn(min = 80.dp, max = 280.dp)
                .combinedClickable(
                    onClick = { /* Optional: full screen image view */ },
                    onLongClick = { showMenu = true }
                )
        ) {
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {

                // --- MEDIA DISPLAY ---
                if (message.isMedia && !message.mediaUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = message.mediaUrl,
                        contentDescription = message.text,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .sizeIn(minWidth = 150.dp, minHeight = 150.dp, maxHeight = 200.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .padding(bottom = 6.dp),
                        // Fallback/Error/Placeholder can be added here
                    )
                    // Display the accompanying text/caption below the image
//                    if (message.text.isNotBlank() && message.text != "Uploading Image...") {
//                        Text(message.text, color = textColor)
//                    }
                } else {
                    // --- TEXT DISPLAY ---
                    Text(message.text, color = textColor)
                }

                // --- TIMESTAMP ROW ---
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth().padding(top = if (message.isMedia) 4.dp else 0.dp)
                ) {
                    if (message.edited) {
                        Text("(Edited)", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(end = 4.dp), color = textColor.copy(alpha = 0.6f))
                    }
                    Text(timestampText, style = MaterialTheme.typography.labelSmall, color = textColor.copy(alpha = 0.6f))
                }

                // --- DROPDOWN MENU ---
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    if (isMine) {
                        DropdownMenuItem(text = { Text("Edit") }, onClick = { showMenu = false; onEditClicked() }, enabled = !message.isMedia) // Disable edit for media
                    }
                    DropdownMenuItem(text = { Text("Delete") }, onClick = { showMenu = false; onDeleteClicked() })
                }
            }
        }
    }
}