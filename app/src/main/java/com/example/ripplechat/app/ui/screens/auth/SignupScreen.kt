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
import androidx.compose.ui.graphics.Color
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

    // Validation states
    var nameError by remember { mutableStateOf<String?>(null) }
    var emailError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }

    // Real-time validation
    LaunchedEffect(name) {
        nameError = when {
            name.isBlank() -> null // Don't show error for empty field until user tries to submit
            name.length < 2 -> "Name must be at least 2 characters"
            name.length > 30 -> "Name must be less than 30 characters"
            else -> null
        }
    }

    LaunchedEffect(email) {
        emailError = when {
            email.isBlank() -> null
            !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() -> "Please enter a valid email"
            else -> null
        }
    }

    LaunchedEffect(password) {
        passwordError = when {
            password.isBlank() -> null
            password.length < 6 -> "Password must be at least 6 characters"
            password.length > 128 -> "Password is too long"
            else -> null
        }
    }

    // React to state changes (Success / Error)
    LaunchedEffect(state) {
        when (state) {
            is AuthState.Success -> {
                Toast.makeText(ctx, "Signup successful!", Toast.LENGTH_SHORT).show()
                // navigate and clear state so next time UI is idle
                navController.navigate("dashboard") {
                    popUpTo("signup") { inclusive = true }
                }
                viewModel.clearSignupState()
            }
            is AuthState.Error -> {
                Toast.makeText(ctx, (state as AuthState.Error).message, Toast.LENGTH_LONG).show()
                viewModel.clearSignupState()
            }
            else -> { /* Idle / Loading -> no-op */ }
        }
    }

    // Check if form is valid
    val isFormValid = name.isNotBlank() &&
            email.isNotBlank() &&
            password.isNotBlank() &&
            nameError == null &&
            emailError == null &&
            passwordError == null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Create account", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))

        // Name Field
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name") },
            modifier = Modifier.fillMaxWidth(),
            isError = nameError != null,
            supportingText = nameError?.let {
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

        // Email Field
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth(),
            isError = emailError != null,
            supportingText = emailError?.let {
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
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = passwordError != null,
            supportingText = passwordError?.let {
                { Text(it, color = MaterialTheme.colorScheme.error) }
            },
            visualTransformation = if (passwordVisible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
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
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (passwordVisible) "Hide password" else "Show password"
                    )
                }
            }
        )

        Spacer(Modifier.height(20.dp))

        // Sign Up Button
        Button(
            onClick = {
                if (isFormValid) {
                    viewModel.signup(name.trim(), email.trim(), password.trim())
                }
            },
            enabled = state !is AuthState.Loading && isFormValid,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (state is AuthState.Loading) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Creating account...")
                }
            } else {
                Text("Sign up")
            }
        }

        // Show form validation message
        if (!isFormValid && (name.isNotBlank() || email.isNotBlank() || password.isNotBlank())) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = when {
                    name.isBlank() -> "Please enter your name"
                    email.isBlank() -> "Please enter your email"
                    password.isBlank() -> "Please enter a password"
                    else -> "Please fix the errors above"
                },
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(Modifier.height(12.dp))

        TextButton(onClick = { navController.navigate("login") }) {
            Text("Already have an account?")
        }
    }
}