package com.example.ripplechat.app.data.model.ui.theme.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(navController: NavController, viewModel: DashBoardVM = hiltViewModel<DashBoardVM>()) {
    val users by viewModel.users.collectAsState()
    val currentUser = FirebaseAuth.getInstance().currentUser

    Scaffold(topBar = {
        TopAppBar(title = { Text("Chats") }, actions = {
            IconButton(onClick = { navController.navigate("profile") }) { Icon(Icons.Default.AccountCircle, contentDescription = "profile") }
        })
    }) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            if (users.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No users found", style = MaterialTheme.typography.bodyLarge)
                }
            }else{
                LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(users.size) { idx ->
                        val user = users[idx]
                        Card(modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val currentUid = currentUser?.uid ?: return@clickable
                                val chatId = if (currentUid < user.uid) "$currentUid-${user.uid}" else "${user.uid}-$currentUid"
                                navController.navigate("chat/$chatId/${user.uid}/${user.name}")
                            }
                        ) {
                            Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column {
                                    AsyncImage(
                                        model = user.profileImageUrl ?: "https://via.placeholder.com/100",
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(Color.Gray)
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text(user.name, style = MaterialTheme.typography.titleMedium)
                                    if (!user.email.isNullOrEmpty()) Text(user.email, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

