package com.example.ripplechat.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.ripplechat.app.data.model.ui.theme.screens.home.DashBoardVM
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import android.net.Uri
import com.example.ripplechat.app.util.CloudinaryUploadHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewGroupScreen(
    navController: NavController,
    dashboardViewModel: DashBoardVM
) {
    val chatList by dashboardViewModel.chatList.collectAsState()
    
    // Filter out groups, only show 1-on-1 contacts
    val contacts = chatList.filter { !it.isGroup }
    
    val selectedUids = remember { mutableStateListOf<String>() }
    var groupName by remember { mutableStateOf("") }
    var groupIconUri by remember { mutableStateOf<Uri?>(null) }
    var isCreating by remember { mutableStateOf(false) }
    
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: return
    
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri -> groupIconUri = uri }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Group") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            if (selectedUids.isNotEmpty() && groupName.isNotBlank()) {
                FloatingActionButton(onClick = {
                    if (isCreating) return@FloatingActionButton
                    isCreating = true
                    scope.launch {
                        try {
                            val groupId = UUID.randomUUID().toString()
                            val participants = selectedUids.toList() + currentUid
                            
                            var uploadedIconUrl: String? = null
                            if (groupIconUri != null) {
                                val bytes = CloudinaryUploadHelper.uriToImageBytes(uri = groupIconUri!!, contentResolver = ctx.contentResolver)
                                if (bytes != null) {
                                    uploadedIconUrl = CloudinaryUploadHelper.uploadImage(bytes, "group_icons/$groupId")
                                }
                            }
                            
                            val db = FirebaseFirestore.getInstance()
                            
                            // For each participant, create the group metadata document
                            participants.forEach { pUid ->
                                val docRef = db.collection("users").document(pUid)
                                    .collection("chats").document(groupId)
                                
                                val data = mapOf(
                                    "peerUid" to groupId,
                                    "isGroup" to true,
                                    "groupName" to groupName.trim(),
                                    "groupIcon" to uploadedIconUrl,
                                    "adminUid" to currentUid,
                                    "participants" to participants,
                                    "lastMessage" to "Group created",
                                    "lastTimestamp" to com.google.firebase.Timestamp.now()
                                )
                                docRef.set(data)
                            }
                            
                            navController.popBackStack()
                            navController.navigate("chat/$groupId/group/$groupName")
                            
                        } catch (e: Exception) {
                            e.printStackTrace()
                            isCreating = false
                        }
                    }
                }) {
                    if (isCreating) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimaryContainer)
                    } else {
                        Icon(Icons.Default.Check, contentDescription = "Create Group")
                    }
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            
            // Group Icon Picker
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { launcher.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    if (groupIconUri != null) {
                        AsyncImage(
                            model = groupIconUri,
                            contentDescription = "Group Icon",
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text("Add Icon")
                    }
                }
            }
            
            // Group Name Input
            OutlinedTextField(
                value = groupName,
                onValueChange = { groupName = it },
                label = { Text("Group Name") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                singleLine = true
            )
            
            Text(
                text = "Select Participants (${selectedUids.size})",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            
            // Contacts List
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(contacts) { contact ->
                    val isSelected = selectedUids.contains(contact.peerUid)
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (isSelected) {
                                    selectedUids.remove(contact.peerUid)
                                } else {
                                    selectedUids.add(contact.peerUid)
                                }
                            }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Profile Pic
                        Box {
                            AsyncImage(
                                model = contact.peerProfilePic ?: "https://ui-avatars.com/api/?name=${contact.peerName}",
                                contentDescription = null,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                            )
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.4f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = "Selected", tint = Color.White)
                                }
                            }
                        }
                        
                        Spacer(Modifier.width(16.dp))
                        
                        Text(
                            text = contact.peerName,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        }
    }
}
