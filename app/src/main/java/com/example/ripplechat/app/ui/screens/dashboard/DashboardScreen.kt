package com.example.ripplechat.app.data.model.ui.theme.screens.home

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import com.example.ripplechat.app.data.model.User
import com.example.ripplechat.app.ui.profile.ProfileScreen
import com.example.ripplechat.app.ui.profile.ProfileState
import com.example.ripplechat.app.ui.profile.ProfileViewModel
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DashboardScreen(
    navController: NavController,
    viewModel: DashBoardVM = hiltViewModel(),
    profileVM: ProfileViewModel = hiltViewModel()
) {
    val contacts by viewModel.contacts.collectAsState()
    val users by viewModel.users.collectAsState()              // contacts as full docs
    val searchResults by viewModel.searchResults.collectAsState()
    val me = FirebaseAuth.getInstance().currentUser
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 2 })
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    TabRow(selectedTabIndex = pagerState.currentPage) {
                        Tab(selected = pagerState.currentPage == 0, onClick = { scope.launch { pagerState.animateScrollToPage(0) } }, text = { Text("Chats") })
                        Tab(selected = pagerState.currentPage == 1, onClick = { scope.launch { pagerState.animateScrollToPage(1) } }, text = { Text("Profile") })
                    }
                }
            )
        }
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) { page ->
            when (page) {
                0 -> ChatListTab(
                    users = users,
                    onChatClick = { user ->
                        val currentUid = me?.uid ?: return@ChatListTab
                        val chatId = if (currentUid < user.uid) "$currentUid-${user.uid}" else "${user.uid}-$currentUid"
                        navController.navigate("chat/$chatId/${user.uid}/${user.name}")
                    },
                    onSearch = { viewModel.search(it) },
                    onClearSearch = { viewModel.clearSearch() },
                    searchResults = searchResults,
                    onAddContact = { viewModel.addContact(it) }
                )
                1 -> ProfileTab(navController = navController, profileVM = profileVM)
            }
        }
    }
}

@Composable
private fun ChatListTab(
    users: List<User>,
    onChatClick: (User) -> Unit,
    onSearch: (String) -> Unit,
    onClearSearch: () -> Unit,
    searchResults: List<User>,
    onAddContact: (String) -> Unit
) {
    var query by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        // Search bar
        OutlinedTextField(
            value = query,
            onValueChange = {
                query = it
                if (query.isBlank()) onClearSearch() else onSearch(query)
            },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = {
                if (query.isNotBlank()) {
                    val controller = LocalSoftwareKeyboardController.current
                    IconButton(onClick = {
                        query = "";
                        onClearSearch();
                        controller?.hide(); // Correctly hide the keyboard
                    }) {
                        Icon(Icons.Default.Close, null)
                    }
                }
            },
            placeholder = { Text("Search users by name") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        )

        if (query.isNotBlank()) {
            Text("Search results", modifier = Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.labelMedium)
            LazyColumn(
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(searchResults.size) { i ->
                    val user = searchResults[i]
                    ElevatedCard(
                        onClick = {},
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = user.profileImageUrl ?: "https://via.placeholder.com/80",
                                contentDescription = null,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(user.name, style = MaterialTheme.typography.titleMedium)
                                if (user.email.isNotEmpty())
                                    Text(user.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            OutlinedButton(onClick = { onAddContact(user.uid) }) { Text("Add") }
                        }
                    }
                }
            }
        } else {
            // Contacts at top, no center alignment
            LazyColumn(
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(users.size) { i ->
                    val user = users[i]
                    ElevatedCard(
                        onClick = { onChatClick(user) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = user.profileImageUrl ?: "https://via.placeholder.com/80",
                                contentDescription = null,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(user.name, style = MaterialTheme.typography.titleMedium)
                                if (user.email.isNotEmpty())
                                    Text(user.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.Default.ArrowForward, contentDescription = null)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileTab(navController: NavController, profileVM: ProfileViewModel) {
    ProfileScreen(navController, profileVM)
}

