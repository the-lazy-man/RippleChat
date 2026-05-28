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
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Image

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
    var upiId by remember(userState.uid, userState.upiId) { mutableStateOf(userState.upiId ?: "") }

    var showPreview by remember { mutableStateOf(false) }
    var showQrDialog by remember { mutableStateOf(false) }
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

            OutlinedTextField(
                value = upiId,
                onValueChange = { upiId = it },
                label = { Text("UPI ID (VPA) for receiving payments") },
                modifier = Modifier.fillMaxWidth()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { 
                        viewModel.updateName(name) 
                        viewModel.updateUpiId(upiId)
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Save Profile") }

                OutlinedButton(
                    onClick = { launcher.launch("image/*") },
                    modifier = Modifier.weight(1f)
                ) { Text("Change Photo") }
            }

            // QR Code Generator Button
            OutlinedButton(
                onClick = { 
                    if (upiId.isBlank()) {
                        Toast.makeText(ctx, "Please save your UPI ID first to generate a QR Code.", Toast.LENGTH_SHORT).show()
                    } else {
                        showQrDialog = true 
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("My QR Code") }

            OutlinedButton(
                onClick = {
                    val shareLink = "ripplechat://chat/${userState.uid}/${userState.name}"
                    val sendIntent = android.content.Intent().apply {
                        action = android.content.Intent.ACTION_SEND
                        putExtra(android.content.Intent.EXTRA_TEXT, "Chat with me on RippleChat! Click here: $shareLink")
                        type = "text/plain"
                    }
                    val shareIntent = android.content.Intent.createChooser(sendIntent, "Share your profile link")
                    ctx.startActivity(shareIntent)
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Share Profile Link") }

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

        if (showQrDialog) {
            val qrContent = """{"uid":"${userState.uid}","name":"${userState.name}","vpa":"${userState.upiId}"}"""
            val qrBitmap = generateQrCode(qrContent)
            
            AlertDialog(
                onDismissRequest = { showQrDialog = false },
                confirmButton = {
                    TextButton(onClick = { showQrDialog = false }) { Text("Close") }
                },
                title = { Text("My QR Code") },
                text = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Scan to add as friend or pay via UPI", style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(16.dp))
                        if (qrBitmap != null) {
                            Image(
                                bitmap = qrBitmap.asImageBitmap(),
                                contentDescription = "QR Code",
                                modifier = Modifier.size(200.dp)
                            )
                        } else {
                            Text("Failed to generate QR Code")
                        }
                    }
                }
            )
        }
    }
}

fun generateQrCode(content: String): Bitmap? {
    return try {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, 512, 512)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE)
            }
        }
        bitmap
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}