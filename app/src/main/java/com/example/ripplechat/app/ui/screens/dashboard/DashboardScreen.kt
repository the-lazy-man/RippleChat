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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import com.example.ripplechat.app.ui.profile.ProfileState
import com.example.ripplechat.app.ui.profile.ProfileViewModel
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DashboardScreen(
    navController: NavController,
    viewModel: DashBoardVM = hiltViewModel()
) {
    val users by viewModel.users.collectAsState()
    val currentUser = FirebaseAuth.getInstance().currentUser

    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 2 })
    val coroutine = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TabRow(
                            selectedTabIndex = pagerState.currentPage,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Tab(
                                text = { Text("Chats") },
                                selected = pagerState.currentPage == 0,
                                onClick = {
                                    coroutine.launch { pagerState.animateScrollToPage(0) }
                                }
                            )
                            Tab(
                                text = { Text("Profile") },
                                selected = pagerState.currentPage == 1,
                                onClick = {
                                    coroutine.launch { pagerState.animateScrollToPage(1) }
                                }
                            )
                        }
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
                0 -> { // ✅ Chats
                    if (users.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No users found", style = MaterialTheme.typography.bodyLarge)
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(users.size) { idx ->
                                val user = users[idx]
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val currentUid = currentUser?.uid ?: return@clickable
                                            val chatId = if (currentUid < user.uid) {
                                                "$currentUid-${user.uid}"
                                            } else {
                                                "${user.uid}-$currentUid"
                                            }
                                            navController.navigate("chat/$chatId/${user.uid}/${user.name}")
                                        }
                                ) {
                                    Row(
                                        Modifier.padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        AsyncImage(
                                            model = user.profileImageUrl ?: "https://via.placeholder.com/100",
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape)
                                                .background(Color.Gray)
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Column {
                                            Text(user.name, style = MaterialTheme.typography.titleMedium)
                                            if (!user.email.isNullOrEmpty()) {
                                                Text(user.email, style = MaterialTheme.typography.bodySmall)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                1 -> { // ✅ Profile
                    ProfileScreen(navController)
                }
            }
        }
    }
}

@Composable
fun ProfileScreen(navController: NavController,viewModel: ProfileViewModel = hiltViewModel()) {
    val ctx = LocalContext.current
    val userState by viewModel.user.collectAsState()

    var name by remember { mutableStateOf(userState.name) }

    LaunchedEffect(userState) {
        if (name != userState.name) { // Only update if it's different to prevent unnecessary recompositions
            name = userState.name
        }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { viewModel.uploadPicture(it) }
    }

    // Handle update results (name or picture)
    LaunchedEffect(viewModel.updateState) {
        when (val state = viewModel.updateState) {
            is ProfileState.Success -> Toast.makeText(ctx, "Profile updated", Toast.LENGTH_SHORT).show()
            is ProfileState.Error -> Toast.makeText(ctx, state.message, Toast.LENGTH_SHORT).show()
            else -> {}
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Text("Profile", style = MaterialTheme.typography.headlineMedium)

        Spacer(Modifier.height(24.dp))

        // Avatar
        val painter = rememberAsyncImagePainter(userState.photoUrl ?: "")
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(Color.Gray)
                .clickable { launcher.launch("image/*") },
            contentAlignment = Alignment.Center
        ) {
            if (userState.photoUrl.isNullOrEmpty()) {
                Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = "avatar",
                    tint = Color.White,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Image(
                    painter = painter,
                    contentDescription = "avatar",
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // Name
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))

        // Email (readonly)
        OutlinedTextField(
            value = userState.email ?: "",
            onValueChange = {},
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth(),
            enabled = false
        )

        Spacer(Modifier.height(24.dp))

        // Save button
        Button(
            onClick = { viewModel.updateName(name) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save")
        }

        Spacer(Modifier.height(16.dp))

        // Logout button
        OutlinedButton(
            onClick = {
                viewModel.logout {
                    // Navigate back to login screen after logout
                    navController.navigate("login") {
                        popUpTo("dashboard") { inclusive = true } // remove dashboard from back stack
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red)
        ) {
            Text("Logout")
        }
    }
}
