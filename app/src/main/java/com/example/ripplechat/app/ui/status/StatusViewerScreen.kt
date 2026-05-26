package com.example.ripplechat.app.ui.status

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.ripplechat.app.data.model.MediaType
import com.example.ripplechat.app.data.model.Status
import kotlinx.coroutines.delay
import android.widget.VideoView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import kotlinx.coroutines.launch

@Composable
fun StatusViewerScreen(
    statuses: List<Status>,
    userName: String,
    userProfilePic: String?,
    onClose: () -> Unit,
    onStatusComplete: () -> Unit = {}
) {
    var currentIndex by remember { mutableStateOf(0) }
    var isPaused by remember { mutableStateOf(false) }
    val currentStatus = statuses.getOrNull(currentIndex)
    
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(currentIndex, isPaused) {
        currentStatus?.let { status ->
            if (!isPaused) {
                val remainingTime = (status.duration * (1f - progress.value)).toLong()
                if (remainingTime > 0) {
                    progress.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(durationMillis = remainingTime.toInt(), easing = LinearEasing)
                    )
                }
                
                if (progress.value >= 1f) {
                    if (currentIndex < statuses.size - 1) {
                        progress.snapTo(0f)
                        currentIndex++
                    } else {
                        onStatusComplete()
                        onClose()
                    }
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { offset ->
                        val third = size.width / 3
                        when {
                            offset.x < third && currentIndex > 0 -> {
                                scope.launch { progress.snapTo(0f) }
                                currentIndex--
                            }
                            offset.x > third * 2 && currentIndex < statuses.size - 1 -> {
                                scope.launch { progress.snapTo(0f) }
                                currentIndex++
                            }
                        }
                    },
                    onPress = {
                        isPaused = true
                        tryAwaitRelease()
                        isPaused = false
                    }
                )
            }
    ) {
        // Status Content
        currentStatus?.let { status ->
            when (status.mediaType) {
                MediaType.IMAGE -> {
                    AsyncImage(
                        model = status.mediaUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
                MediaType.TEXT -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(android.graphics.Color.parseColor(status.backgroundColor ?: "#6200EE"))),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = status.caption ?: "",
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(32.dp)
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
                                        // Adjust duration logic or simply start
                                        start()
                                    }
                                }
                            },
                            update = { view ->
                                if (isPaused && view.isPlaying) {
                                    view.pause()
                                } else if (!isPaused && !view.isPlaying) {
                                    view.start()
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
            
            // Caption overlay for images
            if (status.mediaType == MediaType.IMAGE && !status.caption.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(16.dp)
                ) {
                    Text(
                        text = status.caption,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
        
        // Top bar with progress indicators
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Progress bars
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                statuses.forEachIndexed { index, _ ->
                    LinearProgressIndicator(
                        progress = { when {
                            index < currentIndex -> 1f
                            index == currentIndex -> progress.value
                            else -> 0f
                        } },
                        modifier = Modifier
                            .weight(1f)
                            .height(2.dp)
                            .clip(RoundedCornerShape(1.dp)),
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.3f)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // User info and close button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = userProfilePic ?: "https://ui-avatars.com/api/?name=$userName",
                        contentDescription = null,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = userName,
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White
                        )
                        currentStatus?.let {
                            Text(
                                text = getTimeAgo(it.timestamp),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
                
                IconButton(onClick = onClose) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

private fun getTimeAgo(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    
    return when {
        diff < 60_000 -> "Just now"
        diff < 3600_000 -> "${diff / 60_000}m ago"
        diff < 86400_000 -> "${diff / 3600_000}h ago"
        else -> "${diff / 86400_000}d ago"
    }
}
