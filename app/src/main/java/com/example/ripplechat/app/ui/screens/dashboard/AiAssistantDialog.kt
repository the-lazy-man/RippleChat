package com.example.ripplechat.app.ui.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pos.core.viewmodel.AiMessage
import com.pos.core.viewmodel.AiViewModel


@Composable
fun AiAssistantDialog(onDismiss: () -> Unit, viewModel: AiViewModel = hiltViewModel()) {
    // Scroll state for the conversation history
    val scrollState = rememberScrollState()
    val messages by viewModel.messages.collectAsState()
    var input by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ripple AI Assistant") },
        text = {
            Column {
                // Conversation history area - now with scrolling
                Column(
                    modifier = Modifier
                        .height(300.dp) // Slightly taller height for better viewing
                        .fillMaxWidth()
                        .background(Color.LightGray.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                        .padding(8.dp)
                        .verticalScroll(scrollState) // Makes the column vertically scrollable
                ) {
                    messages.forEach { msg ->
                        MessageBubble(msg = msg)
                        Spacer(Modifier.height(8.dp))
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Input box and Send button
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Ask something...") },
                        // Disables the input field if a reply is currently pending (optional)
                        enabled = true
                    )
                    IconButton(
                        onClick = {
                            if (input.isNotBlank()) {
                                viewModel.sendMessage(input)
                                input = "" // Clear input after sending
                            }
                        },
                        // Disable button if input is empty
                        enabled = input.isNotBlank()
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "Send")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

/**
 * Helper Composable to display a single chat message bubble.
 */
@Composable
private fun MessageBubble(msg: AiMessage) {
    val horizontalArrangement = if (msg.isUser) Arrangement.End else Arrangement.Start
    val bubbleColor = if (msg.isUser) MaterialTheme.colorScheme.primaryContainer else Color.White
    val textColor = if (msg.isUser) MaterialTheme.colorScheme.onPrimaryContainer else Color.Black

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = horizontalArrangement
    ) {
        Text(
            // FIX: Changed 'msg.fromUser' to 'msg.isUser' to match ViewModel's data class
            text = msg.text,
            color = textColor,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .background(bubbleColor, RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}
