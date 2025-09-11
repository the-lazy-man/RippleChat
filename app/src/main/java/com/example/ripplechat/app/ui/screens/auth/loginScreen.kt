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
    val savedCreds by viewModel.savedCredentials.collectAsState(initial = null)

    LaunchedEffect(savedCreds) {
        savedCreds?.let { (savedUsername, savedPass) ->
            if (!savedUsername.isNullOrBlank()) username = savedUsername
            if (!savedPass.isNullOrBlank()) pass = savedPass
        }
    }

    LaunchedEffect(state) {
        when (state) {
            is AuthState.Success -> {
                Toast.makeText(context, "Login success", Toast.LENGTH_SHORT).show()
                navController.navigate("dashboard") {
                    popUpTo("login") { inclusive = true }
                }
                viewModel.clearLoginState()
            }
            is AuthState.Error -> {
                Toast.makeText(context, (state as AuthState.Error).message, Toast.LENGTH_SHORT).show()
                viewModel.clearLoginState()
            }
            else -> {}
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("RippleChat", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Username") },   // ✅ Changed from "Email" to "Username"
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = pass.trim(),
            onValueChange = { pass = it },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = if (passVisible) {VisualTransformation.None} else {PasswordVisualTransformation()},
            trailingIcon = {
                IconButton(onClick = { passVisible = !passVisible }) {
                    Icon(
                        if (passVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        // Corrected: Use a descriptive contentDescription for accessibility
                        contentDescription = if (passVisible) "Hide password" else "Show password"
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = {viewModel.login(username.trim(), pass) },
            modifier = Modifier.fillMaxWidth(),
            enabled = state != AuthState.Loading
        ) {
            if (state == AuthState.Loading) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Logging in...")
                }
            } else {
                Text("Login")
            }
        }

        TextButton(onClick = { navController.navigate("signup") }) {
            Text("Create an account")
        }
    }
}