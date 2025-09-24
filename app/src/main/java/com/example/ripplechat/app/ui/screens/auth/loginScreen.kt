import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.ripplechat.app.ui.auth.AuthState
import com.example.ripplechat.app.ui.auth.AuthViewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff

@Composable
fun LoginScreen(navController: NavController, viewModel: AuthViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val state by viewModel.loginState.collectAsState()

    var username by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var passVisible by remember { mutableStateOf(false) }

    // Validation states
    var usernameError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }

    val savedCreds by viewModel.savedCredentials.collectAsState(initial = null)

    // Load saved credentials
    LaunchedEffect(savedCreds) {
        savedCreds?.let { (savedUsername, savedPass) ->
            if (!savedUsername.isNullOrBlank()) username = savedUsername
            if (!savedPass.isNullOrBlank()) pass = savedPass
        }
    }

    // Real-time validation
    LaunchedEffect(username) {
        usernameError = when {
            username.isBlank() -> null // Don't show error for empty field until user tries to submit
            username.length < 2 -> "Username must be at least 2 characters"
            else -> null
        }
    }

    LaunchedEffect(pass) {
        passwordError = when {
            pass.isBlank() -> null
            pass.length < 6 -> "Password must be at least 6 characters"
            else -> null
        }
    }

    // Handle auth state changes
    LaunchedEffect(state) {
        when (state) {
            is AuthState.Success -> {
                Toast.makeText(context, "Login successful!", Toast.LENGTH_SHORT).show()
                navController.navigate("dashboard") {
                    popUpTo("login") { inclusive = true }
                }
                viewModel.clearLoginState()
            }
            is AuthState.Error -> {
                Toast.makeText(context, (state as AuthState.Error).message, Toast.LENGTH_LONG).show()
                viewModel.clearLoginState()
            }
            else -> {}
        }
    }

    // Check if form is valid
    val isFormValid = username.isNotBlank() &&
            pass.isNotBlank() &&
            usernameError == null &&
            passwordError == null

    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("RippleChat", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))

        // Username Field
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Username") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            isError = usernameError != null,
            supportingText = usernameError?.let {
                { Text(it, color = MaterialTheme.colorScheme.error) }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                focusedLabelColor = MaterialTheme.colorScheme.primary,
                unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                cursorColor = MaterialTheme.colorScheme.primary
            )
        )

        Spacer(Modifier.height(12.dp))

        // Password Field
        OutlinedTextField(
            value = pass,
            onValueChange = { pass = it },
            label = { Text("Password") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            isError = passwordError != null,
            supportingText = passwordError?.let {
                { Text(it, color = MaterialTheme.colorScheme.error) }
            },
            visualTransformation = if (passVisible) VisualTransformation.None else PasswordVisualTransformation(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                focusedLabelColor = MaterialTheme.colorScheme.primary,
                unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                cursorColor = MaterialTheme.colorScheme.primary
            ),
            trailingIcon = {
                IconButton(onClick = { passVisible = !passVisible }) {
                    Icon(
                        if (passVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (passVisible) "Hide password" else "Show password"
                    )
                }
            }
        )

        Spacer(Modifier.height(20.dp))

        // Login Button
        Button(
            onClick = {
                if (isFormValid) {
                    viewModel.login(username.trim(), pass.trim())
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = state != AuthState.Loading && isFormValid
        ) {
            if (state == AuthState.Loading) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Logging in...")
                }
            } else {
                Text("Login")
            }
        }

        // Show form validation message
        if (!isFormValid && (username.isNotBlank() || pass.isNotBlank())) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = when {
                    username.isBlank() -> "Please enter your username"
                    pass.isBlank() -> "Please enter your password"
                    else -> "Please fix the errors above"
                },
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(Modifier.height(12.dp))

        TextButton(onClick = { navController.navigate("signup") }) {
            Text("Create an account")
        }
    }
}