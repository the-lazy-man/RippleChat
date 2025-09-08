package com.example.ripplechat.app.ui.profile

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
@Composable
fun ProfileScreen(
    navController: NavController,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val ctx = LocalContext.current
    val userState by viewModel.user.collectAsState()
    var name by remember(userState.uid, userState.name) { mutableStateOf(userState.name) }

    var showPreview by remember { mutableStateOf(false) }
    var pickedUri by remember { mutableStateOf<Uri?>(null) }
    var brightness by remember { mutableFloatStateOf(0f) } // -1..+1

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            pickedUri = uri
            showPreview = true
        }
    }

    LaunchedEffect(viewModel.updateState) {
        when (val s = viewModel.updateState) {
            is ProfileState.Success -> Toast.makeText(ctx, "Updated", Toast.LENGTH_SHORT).show()
            is ProfileState.Error -> Toast.makeText(ctx, s.message, Toast.LENGTH_SHORT).show()
            else -> {}
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Profile", style = MaterialTheme.typography.headlineSmall)

        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = userState.photoUrl ?: "https://via.placeholder.com/120",
                contentDescription = null,
                modifier = Modifier.size(84.dp).clip(CircleShape)
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(userState.name, style = MaterialTheme.typography.titleMedium)
                Text(userState.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name") },
            modifier = Modifier.fillMaxWidth()
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { viewModel.updateName(name) }, modifier = Modifier.weight(1f)) { Text("Save") }
            OutlinedButton(onClick = { launcher.launch("image/*") }, modifier = Modifier.weight(1f)) { Text("Change Photo") }
        }

        OutlinedButton(
            onClick = {
                viewModel.logout {
                    navController.navigate("login") {
                        popUpTo("dashboard") { inclusive = true }
                    }
                }
            },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Logout") }
    }

    if (showPreview && pickedUri != null) {
        AlertDialog(
            onDismissRequest = { showPreview = false },
            confirmButton = {
                TextButton(onClick = {
                    val resolver = ctx.contentResolver
                    viewModel.uploadProcessedPicture(resolver, pickedUri!!, brightness)
                    showPreview = false
                }) { Text("Upload") }
            },
            dismissButton = {
                TextButton(onClick = { showPreview = false }) { Text("Cancel") }
            },
            title = { Text("Preview & Adjust") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    AsyncImage(
                        model = pickedUri,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .clip(MaterialTheme.shapes.medium)
                    )
                    Text("Brightness")
                    Slider(
                        value = brightness,
                        onValueChange = { brightness = it },
                        valueRange = -1f..1f
                    )
                    Text("Tip: For cropping, integrate uCrop before upload for a richer UX.")
                }
            }
        )
    }
}

