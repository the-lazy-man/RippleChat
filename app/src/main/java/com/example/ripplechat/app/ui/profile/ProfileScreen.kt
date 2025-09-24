package com.example.ripplechat.app.ui.profile

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.ripplechat.profile.ProfileState
import com.example.ripplechat.profile.ProfileViewModel

@OptIn(ExperimentalMaterial3Api::class)
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
    val brightnessMatrix = remember(brightness) {
        ColorMatrix(
            floatArrayOf(
                1f, 0f, 0f, 0f, 255f * brightness,
                0f, 1f, 0f, 0f, 255f * brightness,
                0f, 0f, 1f, 0f, 255f * brightness,
                0f, 0f, 0f, 1f, 0f
            )
        )
    }


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

    Scaffold(
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Spacer(modifier = Modifier.height(6.dp))

            // Profile Image + Name + Email in Column
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AsyncImage(
                    model = userState.profileImageUrl
                        ?: "https://ui-avatars.com/api/?name=${userState.name}",
                    contentDescription = null,
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                )
                Text(userState.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    userState.email,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { viewModel.updateName(name) },
                    modifier = Modifier.weight(1f)
                ) { Text("Save") }

                OutlinedButton(
                    onClick = { launcher.launch("image/*") },
                    modifier = Modifier.weight(1f)
                ) { Text("Change Photo") }
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
                                .clip(MaterialTheme.shapes.medium),
                            colorFilter = ColorFilter.colorMatrix(brightnessMatrix)

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
}