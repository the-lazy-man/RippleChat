package com.example.ripplechat.app.ui.status

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.ripplechat.app.data.model.Status

@Composable
fun StatusScreen(
    navController: NavController,
    viewModel: StatusVM,
    onAddStatusClick: () -> Unit
) {
    val myStatuses by viewModel.myStatuses.collectAsState()
    val contactStatuses by viewModel.contactStatuses.collectAsState()
    val userNames by viewModel.userNames.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // My Status Section
        StatusItem(
            title = "My Status",
            subtitle = if (myStatuses.isEmpty()) "Tap to add status update" else "Tap to view",
            imageUrl = myStatuses.firstOrNull()?.mediaUrl,
            hasStatus = myStatuses.isNotEmpty(),
            onClick = {
                if (myStatuses.isEmpty()) {
                    onAddStatusClick()
                } else {
                    // Navigate to my status viewer
                    navController.navigate("my_status_viewer")
                }
            }
        )
        
        if (contactStatuses.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Recent updates",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        // Contacts Status Section
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(contactStatuses.entries.toList()) { (userId, statuses) ->
                if (statuses.isNotEmpty()) {
                    StatusItem(
                        title = userNames[userId] ?: "Loading...",
                        subtitle = getTimeAgo(statuses.first().timestamp),
                        imageUrl = statuses.firstOrNull()?.mediaUrl,
                        hasStatus = true,
                        onClick = {
                            // Navigate to others' status viewer
                            navController.navigate("status_viewer/$userId")
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusItem(
    title: String,
    subtitle: String,
    imageUrl: String?,
    hasStatus: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Profile Picture with Status Ring
        Box {
            AsyncImage(
                model = imageUrl ?: "https://ui-avatars.com/api/?name=$title&background=random",
                contentDescription = null,
                modifier = Modifier
                    .size(56.dp)
                    .then(
                        if (hasStatus) {
                            Modifier.border(
                                width = 3.dp,
                                color = MaterialTheme.colorScheme.primary,
                                shape = CircleShape
                            )
                        } else Modifier
                    )
                    .padding(if (hasStatus) 2.dp else 0.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
