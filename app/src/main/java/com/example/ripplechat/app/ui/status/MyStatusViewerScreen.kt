package com.example.ripplechat.app.ui.status

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.ripplechat.app.data.model.MediaType
import com.example.ripplechat.app.data.model.Status
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.widget.VideoView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MyStatusViewerScreen(
    navController: NavController,
    viewModel: StatusVM = hiltViewModel()
) {
    val myStatuses by viewModel.myStatuses.collectAsState()
    val scope = rememberCoroutineScope()
    
    // Filter non-expired statuses
    val activeStatuses = remember(myStatuses) {
        myStatuses.filter { it.expiresAt > System.currentTimeMillis() }
    }

    var showDeleteDialog by remember { mutableStateOf(false) }
    var statusToDelete by remember { mutableStateOf<Status?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }
    var statusToEdit by remember { mutableStateOf<Status?>(null) }

    if (activeStatuses.isEmpty()) {
        // No active statuses
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No active statuses", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(16.dp))
                TextButton(onClick = { navController.popBackStack() }) {
                    Text("Go Back")
                }
            }
        }
        return
    }

    val pagerState = rememberPagerState(pageCount = { activeStatuses.size })
    val currentStatus = activeStatuses.getOrNull(pagerState.currentPage)

    val progress = remember { Animatable(0f) }

    // Auto-advance timer and progress animation
    LaunchedEffect(pagerState.currentPage, currentStatus) {
        progress.snapTo(0f)
        currentStatus?.let { status ->
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = status.duration, easing = LinearEasing)
            )
            
            // Reached 100%
            if (pagerState.currentPage < activeStatuses.lastIndex) {
                pagerState.scrollToPage(pagerState.currentPage + 1)
            } else {
                navController.popBackStack() // Done viewing all
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("My Status", style = MaterialTheme.typography.titleMedium)
                        currentStatus?.let {
                            Text(
                                formatTimestamp(it.timestamp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.5f),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        containerColor = Color.Black
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Pager for statuses
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val status = activeStatuses[page]
                StatusContentView(status = status)
            }

            // Progress indicators at top
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 16.dp)
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                activeStatuses.forEachIndexed { index, _ ->
                    LinearProgressIndicator(
                        progress = { when {
                            index < pagerState.currentPage -> 1f
                            index == pagerState.currentPage -> progress.value
                            else -> 0f
                        } },
                        modifier = Modifier
                            .weight(1f)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.3f)
                    )
                }
            }

            // Action buttons at bottom
            currentStatus?.let { status ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Edit button (only for text statuses)
                    if (status.mediaType == MediaType.TEXT) {
                        Button(
                            onClick = {
                                statusToEdit = status
                                showEditDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(Icons.Default.Edit, "Edit", modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Edit")
                        }
                    }

                    // Delete button
                    Button(
                        onClick = {
                            statusToDelete = status
                            showDeleteDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.Delete, "Delete", modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Delete")
                    }
                }
            }
        }

        // Delete confirmation dialog
        if (showDeleteDialog && statusToDelete != null) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text("Delete Status?") },
                text = { Text("This status will be permanently deleted.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            statusToDelete?.let { viewModel.deleteStatus(it.statusId) }
                            showDeleteDialog = false
                            statusToDelete = null
                            // Navigate back if no more statuses
                            scope.launch {
                                delay(300)
                                if (activeStatuses.size <= 1) {
                                    navController.popBackStack()
                                }
                            }
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Edit dialog (for text statuses)
        if (showEditDialog && statusToEdit != null) {
            EditTextStatusDialog(
                status = statusToEdit!!,
                onDismiss = { showEditDialog = false; statusToEdit = null },
                onSave = { newCaption, newColor ->
                    viewModel.updateStatus(
                        statusId = statusToEdit!!.statusId,
                        newCaption = newCaption,
                        newBackgroundColor = newColor
                    )
                    showEditDialog = false
                    statusToEdit = null
                }
            )
        }
    }
}

@Composable
private fun StatusContentView(status: Status) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        when (status.mediaType) {
            MediaType.IMAGE -> {
                AsyncImage(
                    model = status.mediaUrl,
                    contentDescription = "Status image",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
                // Caption overlay
                if (!status.caption.isNullOrBlank()) {
                    Text(
                        text = status.caption,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.6f))
                            .padding(16.dp),
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
            MediaType.VIDEO -> {
                val context = androidx.compose.ui.platform.LocalContext.current
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    AndroidView(
                        factory = { ctx ->
                            VideoView(ctx).apply {
                                setVideoPath(status.mediaUrl)
                                setOnPreparedListener { mp ->
                                    mp.isLooping = true
                                    start()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            MediaType.TEXT -> {
                val bgColor = status.backgroundColor?.let { Color(android.graphics.Color.parseColor(it)) }
                    ?: MaterialTheme.colorScheme.primary
                
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(bgColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = status.caption ?: "",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(32.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditTextStatusDialog(
    status: Status,
    onDismiss: () -> Unit,
    onSave: (caption: String, backgroundColor: String?) -> Unit
) {
    var caption by remember { mutableStateOf(status.caption ?: "") }
    var selectedColor by remember { mutableStateOf(status.backgroundColor) }

    val colors = listOf(
        "#FF6B6B", "#4ECDC4", "#45B7D1", "#FFA07A",
        "#98D8C8", "#6C5CE7", "#A29BFE", "#FD79A8"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onSave(caption.trim(), selectedColor) }, enabled = caption.isNotBlank()) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        title = { Text("Edit Status") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = caption,
                    onValueChange = { caption = it },
                    label = { Text("Caption") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
                
                Text("Background Color:", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    colors.forEach { colorHex ->
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(android.graphics.Color.parseColor(colorHex)))
                                .clickable { selectedColor = colorHex }
                                .then(
                                    if (selectedColor == colorHex) {
                                        Modifier.padding(4.dp)
                                    } else Modifier
                                )
                        )
                    }
                }
            }
        }
    )
}

private fun formatTimestamp(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    
    return when {
        diff < 60_000 -> "Just now"
        diff < 3600_000 -> "${diff / 60_000}m ago"
        diff < 86400_000 -> "${diff / 3600_000}h ago"
        else -> SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault()).format(Date(timestamp))
    }
}
