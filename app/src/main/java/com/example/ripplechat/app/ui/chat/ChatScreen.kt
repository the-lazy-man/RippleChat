import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.ripplechat.app.ui.chat.ChatViewModel
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
    var sending by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    LaunchedEffect(chatId) { viewModel.init(chatId, peerUid) }

    DisposableEffect(Unit) {
        onDispose { viewModel.removeListeners() }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(peerName, style = MaterialTheme.typography.titleMedium)
                        if (typing) {
                            Text(
                                "$peerName is typing...",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            LazyColumn(
                state = listState,
                // Fix: Use padding on the LazyColumn that fills the available space.
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp, vertical = 10.dp)
            ) {
                items(messages) { msg ->
                    val isMine = msg.senderId == viewModel.currentUserId
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
                    ) {
                        Surface(
                            color = if (isMine)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.primary,
                            shape = MaterialTheme.shapes.medium,
                            tonalElevation = 3.dp,
                            shadowElevation = 2.dp,
                            modifier = Modifier
                                .padding(vertical = 4.dp, horizontal = 6.dp)
                                .widthIn(max = 250.dp)
                        ) {
                            Text(
                                msg.text,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                color = if (isMine)
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                else
                                    MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .imePadding(), // Fix: Keep imePadding() only on the input row
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        viewModel.updateTyping(true)
                        viewModel.scheduleStopTyping()
                    },
                    keyboardOptions = KeyboardOptions.Default.copy(
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (text.isNotBlank() && !sending) {
                                sending = true
                                coroutine.launch {
                                    viewModel.sendMessage(text.trim())
                                    text = ""
                                    viewModel.updateTyping(false)
                                    sending = false
                                }
                            }
                        }
                    ),
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Message...") },
                    singleLine = true
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (text.isNotBlank() && !sending) {
                            sending = true
                            coroutine.launch {
                                viewModel.sendMessage(text.trim())
                                text = ""
                                viewModel.updateTyping(false)
                                sending = false
                            }
                        }
                    },
                    enabled = text.isNotBlank() && !sending
                ) {
                    Text("Send")
                }
            }
        }
    }
}