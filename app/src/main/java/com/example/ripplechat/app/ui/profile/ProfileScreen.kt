package com.example.ripplechat.app.ui.profile

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter

@Composable
fun ProfileScreen(viewModel: ProfileViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    val ctx = LocalContext.current
    val userState by viewModel.user.collectAsState()

    var name by remember { mutableStateOf(userState.name) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { viewModel.uploadPicture(it) }
    }

    LaunchedEffect(viewModel.updateState) {
        when (viewModel.updateState) {
            is ProfileState.Success -> Toast.makeText(ctx, "Profile updated", Toast.LENGTH_SHORT).show()
            is ProfileState.Error -> Toast.makeText(ctx, (viewModel.updateState as ProfileState.Error).message, Toast.LENGTH_SHORT).show()
            else -> {}
        }
    }

    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Profile", style = MaterialTheme.typography.headlineSmall)

        val painter = rememberAsyncImagePainter(userState.photoUrl ?: "")
        Box(modifier = Modifier.size(110.dp).clickable { launcher.launch("image/*") }) {
            Image(painter = painter, contentDescription = "avatar", modifier = Modifier.fillMaxSize())
        }

        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") })
        Button(onClick = { viewModel.updateName(name) }, modifier = Modifier.fillMaxWidth()) { Text("Save") }
    }
}

