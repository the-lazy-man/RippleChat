package com.example.ripplechat.app.ui.dashboard

import android.annotation.SuppressLint
    import android.util.Log
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.activity.compose.rememberLauncherForActivityResult
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import org.json.JSONObject
import android.widget.Toast
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.ripplechat.app.data.model.ChatListItem
import com.example.ripplechat.app.data.model.User
import com.example.ripplechat.app.data.model.ui.theme.screens.home.DashBoardVM
import com.example.ripplechat.app.ui.profile.ProfileScreen
import com.example.ripplechat.app.ui.screens.dashboard.AiAssistantDialog
import com.example.ripplechat.profile.ProfileViewModel
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.ripplechat.app.ui.status.StatusScreen
import com.example.ripplechat.app.ui.status.StatusVM
import com.example.ripplechat.app.ui.status.StatusViewerScreen
import com.example.ripplechat.app.ui.status.StatusUploadDialog
import com.example.ripplechat.app.data.model.Status

// Assume showToast is available or implemented/removed if not.

// Main Screen with Tabs
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DashboardScreen(
    navController: NavController,
    dashboardViewModel: DashBoardVM = hiltViewModel(),
    profileViewModel: ProfileViewModel = hiltViewModel(),
    statusViewModel: StatusVM = hiltViewModel()
) {
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 3 })
    val scope = rememberCoroutineScope()
    val tabs = listOf("Chats", "Updates", "Profile")
    val showAssistant = remember { mutableStateOf(false) }
    
    // Status viewer state
    var showStatusViewer by remember { mutableStateOf(false) }
    var currentStatusList by remember { mutableStateOf<List<Status>>(emptyList()) }
    var currentStatusUser by remember { mutableStateOf("") }
    var currentStatusUserPic by remember { mutableStateOf<String?>(null) }
    
    // Status upload state
    var showStatusUpload by remember { mutableStateOf(false) }
    
    // QR Scanner State
    val ctx = LocalContext.current
    var scannedUserJson by remember { mutableStateOf<JSONObject?>(null) }
    var showScannedUserDialog by remember { mutableStateOf(false) }

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            try {
                val json = JSONObject(result.contents)
                if (json.has("uid") && json.has("name") && json.has("vpa")) {
                    scannedUserJson = json
                    showScannedUserDialog = true
                } else {
                    Toast.makeText(ctx, "Invalid QR Code", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(ctx, "Invalid QR Code format", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val chatList by dashboardViewModel.chatList.collectAsState()
    val userNames by statusViewModel.userNames.collectAsState()
    val contactStatuses by statusViewModel.contactStatuses.collectAsState()

    val selectedChats = remember { mutableStateListOf<String>() }

    // Sync contact UIDs with StatusVM for status updates
    LaunchedEffect(chatList) {
        val contactIds = chatList.filter { !it.isGroup && it.peerUid.isNotBlank() }.map { it.peerUid }
        statusViewModel.startListeningContactStatuses(contactIds)
    }

    Scaffold(
        topBar = {
            if (selectedChats.isNotEmpty()) {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { selectedChats.clear() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Clear Selection")
                        }
                    },
                    title = { Text(selectedChats.size.toString()) },
                    actions = {
                        IconButton(onClick = {
                            selectedChats.clear()
                        }) {
                            Icon(Icons.Default.PushPin, contentDescription = "Pin Chat")
                        }
                        IconButton(onClick = {
                            selectedChats.forEach { uid ->
                                val isMuted = dashboardViewModel.mutedUsers.value.contains(uid)
                                if (isMuted) dashboardViewModel.unmuteUser(uid)
                                else dashboardViewModel.muteUser(uid)
                            }
                            selectedChats.clear()
                        }) {
                            Icon(Icons.Default.NotificationsOff, contentDescription = "Mute Chat")
                        }
                        IconButton(onClick = {
                            selectedChats.forEach { uid ->
                                dashboardViewModel.removeContact(uid)
                            }
                            selectedChats.clear()
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Chat")
                        }
                    },
                    colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            } else {
                TopAppBar(
                    title = { Text("Ripple Chat") },
                    actions = {
                        IconButton(onClick = { navController.navigate("create_group") }) {
                            Icon(Icons.Default.GroupAdd, contentDescription = "Create Group")
                        }
                        IconButton(onClick = {
                            val options = ScanOptions()
                            options.setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                            options.setPrompt("Scan RippleChat QR Code")
                            options.setCameraId(0)
                            options.setBeepEnabled(false)
                            options.setBarcodeImageEnabled(false)
                            options.setOrientationLocked(false)
                            scanLauncher.launch(options)
                        }) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan QR")
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            when (pagerState.currentPage) {
                0 -> { // Chats tab
                    FloatingActionButton(onClick = { showAssistant.value = true }) {
                        Icon(Icons.Default.Android, contentDescription = "AI Assistant")
                    }
                }
                1 -> { // Updates tab
                    FloatingActionButton(onClick = { showStatusUpload = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Status")
                    }
                }
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = pagerState.currentPage) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = { Text(title) }
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (page) {
                    // FIX: Renamed the complicated logic into a dedicated composable
                    0 -> ChatListAndSearchTab(
                        navController = navController,
                        dashboardViewModel = dashboardViewModel,
                        selectedChats = selectedChats
                    )
                    1 -> StatusScreen(
                        navController = navController,
                        viewModel = statusViewModel,
                        onAddStatusClick = {
                            showStatusUpload = true
                        }
                    )
                    2 -> ProfileTab(navController = navController, profileVM = profileViewModel)
                }
            }
            
            // Status Viewer Overlay
            if (showStatusViewer && currentStatusList.isNotEmpty()) {
                StatusViewerScreen(
                    statuses = currentStatusList,
                    userName = currentStatusUser,
                    userProfilePic = currentStatusUserPic,
                    onClose = { showStatusViewer = false }
                )
            }
            
            // Show AI Assistant dialog when FAB is clicked
            if (showAssistant.value) {
                AiAssistantDialog(
                    onDismiss = { showAssistant.value = false }
                )
            }
            
            // Status Upload Dialog
            if (showStatusUpload) {
                val context = LocalContext.current
                StatusUploadDialog(
                    contentResolver = context.contentResolver,
                    onDismiss = { showStatusUpload = false },
                    onUpload = { mediaType, uri, caption, backgroundColor ->
                        statusViewModel.uploadStatusWithMedia(
                            mediaType = mediaType,
                            mediaUri = uri,
                            caption = caption,
                            backgroundColor = backgroundColor,
                            contentResolver = context.contentResolver,
                            onSuccess = { 
                                showStatusUpload = false
                            },
                            onError = { error ->
                                // TODO: Show error toast/snackbar
                                Log.e("DashboardScreen", "Upload error: $error")
                                showStatusUpload = false
                            }
                        )
                    }
                )
            }
            // Scanned User Dialog
            if (showScannedUserDialog && scannedUserJson != null) {
                val json = scannedUserJson!!
                val scannedUid = json.getString("uid")
                val scannedName = json.getString("name")
                val scannedVpa = json.getString("vpa")
                
                AlertDialog(
                    onDismissRequest = { showScannedUserDialog = false },
                    title = { Text("User Scanned") },
                    text = {
                        Column {
                            Text("Name: $scannedName", style = MaterialTheme.typography.bodyLarge)
                            if (scannedVpa.isNotBlank() && scannedVpa != "null") {
                                Spacer(modifier = Modifier.     height(4.dp))
                                Text("UPI ID: $scannedVpa", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    },
                    confirmButton = {
                        Button(onClick = {
                            scope.launch {
                                dashboardViewModel.addContact(scannedUid)
                                showScannedUserDialog = false
                                val currentUid = FirebaseAuth.getInstance().currentUser?.uid
                                if (currentUid != null) {
                                    val chatId = if (currentUid < scannedUid) "$currentUid-$scannedUid" else "$scannedUid-$currentUid"
                                    navController.navigate("chat/$chatId/$scannedUid/$scannedName")
                                }
                            }
                        }) {
                            Text("Chat / Add")
                        }
                    },
                    dismissButton = {
                        if (scannedVpa.isNotBlank() && scannedVpa != "null") {
                            androidx.compose.material3.OutlinedButton(onClick = {
                                showScannedUserDialog = false
                                val upiUri = Uri.parse("upi://pay?pa=$scannedVpa&pn=$scannedName&cu=INR")
                                val intent = Intent(Intent.ACTION_VIEW, upiUri)
                                try {
                                    ctx.startActivity(Intent.createChooser(intent, "Pay via UPI"))
                                } catch(e: Exception) {
                                    Toast.makeText(ctx, "No UPI app found", Toast.LENGTH_SHORT).show()
                                }
                            }) {
                                Text("Pay")
                            }
                        } else {
                            androidx.compose.material3.TextButton(onClick = { showScannedUserDialog = false }) { Text("Cancel") }
                        }
                    }
                )
            }
        }
    }
}

// --- Tab Content 1: Chat List with Search and Swipe ---
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatListAndSearchTab(
    navController: NavController,
    dashboardViewModel: DashBoardVM,
    selectedChats: androidx.compose.runtime.snapshots.SnapshotStateList<String>
) {
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) } // Controls whether search results or contacts are shown

    val currentUser = FirebaseAuth.getInstance().currentUser

    var chatToDelete by remember { mutableStateOf<ChatListItem?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val chatList by dashboardViewModel.chatList.collectAsState()
    val searchResults by dashboardViewModel.searchResults.collectAsState()
    val allUsersForSearch by dashboardViewModel.allUsersForSearch.collectAsState()

    // --- Logic for determining what to display ---
    val showingChats = !isSearchActive && searchQuery.isEmpty()
    val usersToShow = when {
        searchQuery.isNotEmpty() -> searchResults
        isSearchActive -> allUsersForSearch
        else -> emptyList()
    }
    // --- End List Logic ---

    Column(modifier = Modifier.fillMaxSize()) {
        // Search Bar & Search Control Logic
        OutlinedTextField(
            value = searchQuery,
            onValueChange = {
                searchQuery = it
                if (it.isBlank()) {
                    dashboardViewModel.clearSearch()
                } else {
                    dashboardViewModel.search(it)
                }
            },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = {
                if (searchQuery.isNotBlank() || isSearchActive) {
                    IconButton(onClick = {
                        searchQuery = ""
                        isSearchActive = false
                        dashboardViewModel.clearSearch()
                        dashboardViewModel.clearAllUsersForSearch() // Clear the all-users list too
                        keyboardController?.hide()
                    }) {
                        Icon(Icons.Default.Close, null)
                    }
                }
            },
            placeholder = { Text(if (isSearchActive) "Search users..." else "Search contacts or users") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .onFocusChanged { state ->
                    if (state.isFocused && !isSearchActive) {
                        isSearchActive = true
                        dashboardViewModel.fetchAllUsers() // Preload all users
                    }
                }
        )

        // Title for the List
        if (isSearchActive && usersToShow.isEmpty()) {
            Text(
                "Find new friends above!",
                modifier = Modifier.padding(horizontal = 12.dp),
                style = MaterialTheme.typography.labelMedium
            )
        } else if (searchQuery.isNotBlank()) {
            Text(
                "Search results",
                modifier = Modifier.padding(horizontal = 12.dp),
                style = MaterialTheme.typography.labelMedium
            )
        } else if (!isSearchActive && chatList.isNotEmpty()) {
            Text(
                "Your Chats",
                modifier = Modifier.padding(horizontal = 12.dp),
                style = MaterialTheme.typography.labelMedium
            )
        }


        // 📋 Chat list and user search results
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Show existing chats when not searching
            if (showingChats) {
                items(chatList, key = { it.chatId }) { chatItem ->
                    val isSelected = selectedChats.contains(chatItem.peerUid)
                    
                    ChatCard(
                        chatItem = chatItem,
                        onChatClick = {
                            if (selectedChats.isNotEmpty()) {
                                if (isSelected) selectedChats.remove(chatItem.peerUid)
                                else selectedChats.add(chatItem.peerUid)
                            } else {
                                dashboardViewModel.markChatAsRead(chatItem.chatId)
                                val safeUid = chatItem.peerUid.takeIf { it.isNotBlank() } ?: "group"
                                val safeName = if (chatItem.isGroup) {
                                    chatItem.groupName?.takeIf { it.isNotBlank() } ?: "Group"
                                } else {
                                    chatItem.peerName.takeIf { it.isNotBlank() } ?: "Unknown"
                                }
                                navController.navigate("chat/${chatItem.chatId}/$safeUid/$safeName")
                            }
                        },
                        onChatLongClick = {
                            if (isSelected) selectedChats.remove(chatItem.peerUid)
                            else selectedChats.add(chatItem.peerUid)
                        },
                        isSelected = isSelected,
                        dashboardViewModel = dashboardViewModel
                    )
                }
            } else {
                // Show user search results
                items(usersToShow, key = { it.uid }) { user ->
                    UserCard(
                        user = user,
                        onChatClick = {
                            val currentUid = currentUser?.uid
                            if (currentUid != null) {
                                val chatId = if (currentUid < user.uid) "$currentUid-${user.uid}" else "${user.uid}-$currentUid"
                                navController.navigate("chat/$chatId/${user.uid}/${user.name}")
                            }
                        },
                        dashboardViewModel = dashboardViewModel,
                        onAddContact = { uid ->
                            scope.launch {
                                dashboardViewModel.addContact(uid)
                                isSearchActive = false
                                searchQuery = ""
                                keyboardController?.hide()
                            }
                        },
                        isContact = false
                    )
                }
            }
        }

        // 🗑️ Delete confirm dialog
        if (showDeleteDialog && chatToDelete != null) {
            val chatItem = chatToDelete!!
            AlertDialog(
                onDismissRequest = {
                    showDeleteDialog = false
                },
                title = { Text("Delete Chat") },
                text = { Text("Are you sure you want to delete chat with ${chatItem.peerName}?") },
                confirmButton = {
                    Button(
                        onClick = {
                            scope.launch {
                                dashboardViewModel.removeContact(chatItem.peerUid)
                                chatToDelete = null
                                showDeleteDialog = false
                            }
                        }
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    Button(
                        onClick = {
                            showDeleteDialog = false
                        }
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

// --- Tab Content 2: Profile Screen ---
@Composable
private fun ProfileTab(navController: NavController, profileVM: ProfileViewModel) {
    // The ProfileScreen now handles its own Scaffold/TopAppBar
    ProfileScreen(navController, profileVM)
}


// --- Shared Components ---

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatCard(
    chatItem: ChatListItem,
    onChatClick: () -> Unit,
    onChatLongClick: () -> Unit,
    isSelected: Boolean,
    dashboardViewModel: DashBoardVM
) {
    val mutedUsers by dashboardViewModel.mutedUsers.collectAsState(initial = emptySet())
    val isMuted = mutedUsers.contains(chatItem.peerUid)
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 8.dp)
            .combinedClickable(
                onClick = onChatClick,
                onLongClick = onChatLongClick
            ),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Profile Picture
            val displayName = if (chatItem.isGroup) (chatItem.groupName ?: "Group") else chatItem.peerName
            val displayPic = if (chatItem.isGroup) {
                chatItem.groupIcon ?: "https://ui-avatars.com/api/?name=${displayName}&background=random"
            } else {
                chatItem.peerProfilePic ?: "https://ui-avatars.com/api/?name=${displayName}"
            }
            
            Box {
                AsyncImage(
                    model = displayPic,
                    contentDescription = null,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                )
                
                // Unread count badge
                if (chatItem.unreadCount > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(20.dp)
                            .background(MaterialTheme.colorScheme.error, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (chatItem.unreadCount > 9) "9+" else chatItem.unreadCount.toString(),
                            color = MaterialTheme.colorScheme.onError,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
            
            Spacer(Modifier.width(16.dp))
            
            Column(Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    
                    // Timestamp
                    if (chatItem.lastTimestamp > 0) {
                        Text(
                            text = formatTimestamp(chatItem.lastTimestamp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                // Last message preview
                Text(
                    text = chatItem.lastMessage.ifEmpty { "No messages yet" },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (chatItem.unreadCount > 0) 
                        MaterialTheme.colorScheme.onSurface 
                    else 
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            
            Spacer(Modifier.width(8.dp))
            
            // Status Icons (Muted)
            if (isMuted) {
                Icon(
                    imageVector = Icons.Default.NotificationsOff,
                    contentDescription = "Muted",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// Helper function to format timestamp
private fun formatTimestamp(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    
    return when {
        diff < 60_000 -> "Just now"
        diff < 3600_000 -> "${diff / 60_000}m"
        diff < 86400_000 -> "${diff / 3600_000}h"
        diff < 604800_000 -> "${diff / 86400_000}d"
        else -> {
            val sdf = SimpleDateFormat("MMM dd", Locale.getDefault())
            sdf.format(Date(timestamp))
        }
    }
}

@Composable
private fun UserCard(
    user: User,
    dashboardViewModel: DashBoardVM ,
    onChatClick: () -> Unit,
    onAddContact: (String) -> Unit,
    isContact: Boolean
) {
    val mutedUsers by dashboardViewModel.mutedUsers.collectAsState(initial = emptySet())
    // Derive the boolean from the collected state (reusable)
    val isMuted = user?.let { mutedUsers.contains(it.uid) }
    Card(
        onClick = onChatClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 8.dp)
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = user.profileImageUrl
                    ?: "https://ui-avatars.com/api/?name=${user.name}",
                contentDescription = null,
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(user.name, style = MaterialTheme.typography.titleMedium)
                if (user.email.isNotEmpty())
                    Text(user.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(
                onClick = {
                    // Now use the pre-collected state (no collectAsState() here!)
                    if (isMuted == true) {
                        dashboardViewModel.unmuteUser (user.uid)
                    } else {
                        dashboardViewModel.muteUser (user.uid)
                    }
                }
            ) {
                Icon(
                    // Use the pre-collected state for the condition (no collectAsState() here!)
                    imageVector = if (isMuted == true) {
                        Icons.Default.NotificationsOff  // Muted state
                    } else {
                        Icons.Default.Notifications    // Not muted state
                    },
                    contentDescription = if (isMuted == true) "Unmute Notifications" else "Mute Notifications"
                )
            }

            if (!isContact && !user.uid.isNullOrBlank()) { // Only show Add button if they are NOT a contact
                Button(
                    onClick = { onAddContact(user.uid) }
                ) { Text("Add") }
            } else {
                Icon(Icons.Default.ArrowForward, contentDescription = "Open Chat")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDismissBackground(dismissState: SwipeToDismissBoxState) {
    val color = when (dismissState.targetValue) {
        SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.error
        else -> Color.Transparent
    }

    val scale by animateFloatAsState(
        if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) 1f else 0.75f,
        label = "DeleteIconScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(color),
        contentAlignment = Alignment.CenterEnd
    ) {
        Icon(
            Icons.Default.Delete,
            contentDescription = "Delete",
            tint = MaterialTheme.colorScheme.onError,
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .scale(scale)
        )
    }
}

@SuppressLint("SuspiciousModifierThen")
@Composable
fun Modifier.scale(scale: Float) = this.then(graphicsLayer { scaleX = scale; scaleY = scale })