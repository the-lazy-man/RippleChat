package com.example.ripplechat.app.data.model.ui.theme.screens.signup

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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

@Composable
fun SignupScreen(
    navController: NavController,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val ctx = LocalContext.current
    val state by viewModel.signupState.collectAsState()

    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    // react to state changes (Success / Error)
    LaunchedEffect(state) {
        when (state) {
            is AuthState.Success -> {
                Toast.makeText(ctx, "Signup success", Toast.LENGTH_SHORT).show()
                // navigate and clear state so next time UI is idle
                navController.navigate("dashboard") {
                    popUpTo("signup") { inclusive = true }
                }
                viewModel.clearSignupState()
            }
            is AuthState.Error -> {
                Toast.makeText(ctx, (state as AuthState.Error).message, Toast.LENGTH_SHORT).show()
                viewModel.clearSignupState()
            }
            else -> { /* Idle / Loading -> no-op */ }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Create account", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = password.trim(),
            onValueChange = { password = it },
            label = { Text("Password") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = if (passwordVisible) {VisualTransformation.None} else {PasswordVisualTransformation()},
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (passwordVisible) "Hide password" else {"Show password"}
                    )
                }
            }
        )

        Spacer(Modifier.height(20.dp))

        // INLINE LOADER inside the button
        Button(
            onClick = { viewModel.signup(name.trim(), email.trim(), password.trim()) },
            enabled = state !is AuthState.Loading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (state is AuthState.Loading) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Signing up...")
                }
            } else {
                Text("Sign up")
            }
        }

        Spacer(Modifier.height(12.dp))

        TextButton(onClick = { navController.navigate("login") }) {
            Text("Already have an account?")
        }
    }
}
