package com.example.ripplechat.app.ui.dashboard

import android.annotation.SuppressLint
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
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import com.example.ripplechat.app.data.model.User
import com.example.ripplechat.app.data.model.ui.theme.screens.home.DashBoardVM
import com.example.ripplechat.app.ui.profile.ProfileScreen
import com.example.ripplechat.app.ui.screens.dashboard.AiAssistantDialog
import com.example.ripplechat.profile.ProfileViewModel
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

// Assume showToast is available or implemented/removed if not.

// Main Screen with Tabs
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DashboardScreen(
    navController: NavController,
    dashboardViewModel: DashBoardVM = hiltViewModel(),
    profileViewModel: ProfileViewModel = hiltViewModel()
) {
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 2 })
    val scope = rememberCoroutineScope()
    val tabs = listOf("Chats", "Profile")
    val showAssistant = remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ripple Chat") } // Title can be generic or based on selected tab
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAssistant.value = true }
            ) {
                Icon(Icons.Default.Android, contentDescription = "AI Assistant")
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
                        dashboardViewModel = dashboardViewModel
                    )
                    1 -> ProfileTab(navController = navController, profileVM = profileViewModel)
                }
            }
            // Show AI Assistant dialog when FAB is clicked
            if (showAssistant.value) {
                AiAssistantDialog(
                    onDismiss = { showAssistant.value = false }
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
    dashboardViewModel: DashBoardVM
) {
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) } // Controls whether search results or contacts are shown

    val currentUser = FirebaseAuth.getInstance().currentUser

    var contactToDelete by remember { mutableStateOf<User?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val chatUsers by dashboardViewModel.users.collectAsState()
    val searchResults by dashboardViewModel.searchResults.collectAsState()
    val allUsersForSearch by dashboardViewModel.allUsersForSearch.collectAsState()

    // --- Logic for determining the list to display ---
    val listToShow = remember(searchQuery, isSearchActive, chatUsers, searchResults, allUsersForSearch) {
        when {
            searchQuery.isNotEmpty() -> searchResults
            isSearchActive -> allUsersForSearch
            else -> chatUsers
        }
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
        if (isSearchActive && listToShow.isEmpty()) {
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
        } else if (!isSearchActive && chatUsers.isNotEmpty()) {
            Text(
                "Your Chats",
                modifier = Modifier.padding(horizontal = 12.dp),
                style = MaterialTheme.typography.labelMedium
            )
        }


        // 📋 User lists
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(listToShow, key = { it.uid }) { user ->
                // Check if the current user object is an existing contact
                val isActualContact = chatUsers.any { it.uid == user.uid } && !isSearchActive && searchQuery.isEmpty()

                val chatRoute = {
                    val currentUid = currentUser?.uid
                    if (currentUid != null) {
                        val chatId = if (currentUid < user.uid) "$currentUid-${user.uid}" else "${user.uid}-$currentUid"
                        navController.navigate("chat/$chatId/${user.uid}/${user.name}")
                    }
                }

                if (isActualContact) {
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { value ->
                            if (value == SwipeToDismissBoxValue.EndToStart) {
                                contactToDelete = user
                                showDeleteDialog = true
                                false // Prevent auto-remove, wait for dialog confirmation
                            } else false
                        }
                    )

                    SwipeToDismissBox(
                        state = dismissState,
                        modifier = Modifier.animateItemPlacement(),
                        backgroundContent = { SwipeToDismissBackground(dismissState) },
                        content = {
                            UserCard(
                                user = user,
                                onChatClick = chatRoute,
                                dashboardViewModel = dashboardViewModel,
                                onAddContact = { },
                                isContact = true
                            )
                        },
                        enableDismissFromEndToStart = true,
                        enableDismissFromStartToEnd = false
                    )
                } else {
                    UserCard(
                        user = user,
                        onChatClick = chatRoute,
                       dashboardViewModel = dashboardViewModel,
                        onAddContact = { uid ->
                            scope.launch {
                                dashboardViewModel.addContact(uid)
                                // Close search view after adding a contact
                                isSearchActive = false
                                searchQuery = ""
                                keyboardController?.hide()
                            }
                        },
                        isContact = isActualContact
                    )
                }
            }
        }

        // 🗑️ Delete confirm dialog
        if (showDeleteDialog && contactToDelete != null) {
            val userToDelete = contactToDelete!!
            AlertDialog(
                onDismissRequest = {
                    showDeleteDialog = false
                },
                title = { Text("Delete Contact") },
                text = { Text("Are you sure you want to delete ${userToDelete.name}?") },
                confirmButton = {
                    Button(
                        onClick = {
                            scope.launch {
                                dashboardViewModel.removeContact(userToDelete.uid)
                                contactToDelete = null
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