package com.example.ripplechat.app.ui.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    navController: NavController,
    chatId: String,
    peerUid: String,
    peerName: String,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val messages by viewModel.messages.collectAsState()
    val typing by viewModel.otherTyping.collectAsState()
    var text by remember { mutableStateOf("") }
    val coroutine = rememberCoroutineScope()

    LaunchedEffect(chatId) { viewModel.init(chatId, peerUid) }
    DisposableEffect(Unit) {
        onDispose { viewModel.removeListeners() }
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text(peerName) })
    }) { padding ->
        Column(modifier = Modifier
            .padding(padding)
            .fillMaxSize()) {

            LazyColumn(modifier = Modifier.weight(1f).padding(8.dp), reverseLayout = false) {
                items(messages) { msg ->
                    val isMine = msg.senderId == viewModel.currentUserId
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
                    ) {
                        Surface(
                            tonalElevation = 2.dp,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.padding(4.dp)
                        ) {
                            Text(msg.text, modifier = Modifier.padding(8.dp))
                        }
                    }
                }
                item {
                    if (typing) Text("${peerName} is typing...", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(8.dp))
                }
            }

            Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        viewModel.updateTyping(true) // start typing
                        viewModel.scheduleStopTyping() // schedule to stop after debounce inside VM
                    },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Message...") }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = {
                    if (text.isNotBlank()) {
                        coroutine.launch { viewModel.sendMessage(text.trim()) }
                        text = ""
                        viewModel.updateTyping(false)
                    }
                }) {
                    Text("Send")
                }
            }
        }
    }
}

