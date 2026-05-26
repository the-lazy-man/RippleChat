package com.example.ripplechat.app.ui.status

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.ripplechat.app.data.model.MediaType
import com.example.ripplechat.app.data.model.Status
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.widget.VideoView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun OthersStatusViewerScreen(
    navController: NavController,
    userId: String,
    viewModel: StatusVM = hiltViewModel()
) {
    val contactStatuses by viewModel.contactStatuses.collectAsState()
    val userNames by viewModel.userNames.collectAsState()
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(userId) {
        viewModel.startListeningContactStatuses(listOf(userId))
        kotlinx.coroutines.delay(1000) // Short delay to allow Firestore to fetch
        isLoading = false
    }

    // Get statuses for this specific user
    val userStatuses = remember(contactStatuses) {
        contactStatuses[userId]?.filter { it.expiresAt > System.currentTimeMillis() }
            ?: emptyList()
    }

    val userName = userNames[userId] ?: "User"

    if (userStatuses.isEmpty()) {
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color.White)
            }
            return
        }
        
        // No statuses available
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No active statuses", color = Color.White, style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(16.dp))
                TextButton(onClick = { navController.popBackStack() }) {
                    Text("Go Back", color = Color.White)
                }
            }
        }
        return
    }

    val pagerState = rememberPagerState(pageCount = { userStatuses.size })
    val currentStatus = userStatuses.getOrNull(pagerState.currentPage)
    
    var isPaused by remember { mutableStateOf(false) }

    val progress = remember { Animatable(0f) }

    // Auto-advance timer and progress animation
    LaunchedEffect(pagerState.currentPage, currentStatus, isPaused) {
        currentStatus?.let { status ->
            if (!isPaused) {
                // If progress was reset or paused, we calculate remaining time
                val remainingTime = (status.duration * (1f - progress.value)).toLong()
                
                if (remainingTime > 0) {
                    progress.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(durationMillis = remainingTime.toInt(), easing = LinearEasing)
                    )
                }
                
                // Done animating
                if (progress.value >= 1f) {
                    if (pagerState.currentPage < userStatuses.lastIndex) {
                        progress.snapTo(0f) // reset for next page
                        pagerState.scrollToPage(pagerState.currentPage + 1)
                    } else {
                        navController.popBackStack() // Done viewing
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
                        // Tap left third = previous
                        // Tap right third = next
                        val width = size.width
                        scope.launch {
                            when {
                                offset.x < width / 3 && pagerState.currentPage > 0 -> {
                                    progress.snapTo(0f)
                                    pagerState.scrollToPage(pagerState.currentPage - 1)
                                }
                                offset.x > 2 * width / 3 && pagerState.currentPage < userStatuses.lastIndex -> {
                                    progress.snapTo(0f)
                                    pagerState.scrollToPage(pagerState.currentPage + 1)
                                }
                                offset.x > 2 * width / 3 && pagerState.currentPage == userStatuses.lastIndex -> {
                                    navController.popBackStack()
                                }
                            }
                        }
                    },
                    onPress = {
                        // Pause while pressing
                        isPaused = true
                        tryAwaitRelease()
                        isPaused = false
                    }
                )
            }
    ) {
        // Status content pager
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val status = userStatuses[page]
            OthersStatusContent(status = status, isPaused = isPaused)
        }

        // Top header with progress bars
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(Color.Black.copy(alpha = 0.4f))
                .padding(8.dp)
        ) {
            // Progress bars
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                userStatuses.forEachIndexed { index, _ ->
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

            Spacer(modifier = Modifier.height(8.dp))

            // User info header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = userName,
                            color = Color.White,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        currentStatus?.let {
                            Text(
                                text = formatTimestamp(it.timestamp),
                                color = Color.White.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                IconButton(onClick = { navController.popBackStack() }) {
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

@Composable
private fun OthersStatusContent(status: Status, isPaused: Boolean = false) {
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
