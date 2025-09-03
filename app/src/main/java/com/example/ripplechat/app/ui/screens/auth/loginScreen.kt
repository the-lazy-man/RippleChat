package com.example.ripplechat.app.data.model.ui.theme.screens.login


import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

@Composable
fun LoginScreen(navController: NavController, viewModel: AuthViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    val ctx = LocalContext.current
    val state by viewModel.authState.collectAsState()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showProgressbar by remember { mutableStateOf(false) }


    if(showProgressbar){
        CircularProgressIndicator()
    }

    LaunchedEffect(state) {
        when (state) {
            is AuthState.Success -> {
                showProgressbar = false
                Toast.makeText(ctx, "Login success", Toast.LENGTH_SHORT).show()
                navController.navigate("dashboard") { popUpTo("login") { inclusive = true } }
            }

            is AuthState.Loading -> showProgressbar = true

            is AuthState.Error -> {
                Toast.makeText(ctx, (state as AuthState.Error).message, Toast.LENGTH_SHORT).show()
                viewModel.clearLoginState()
            }
            else -> {}
        }
    }

    Box(modifier = Modifier.wrapContentSize(), contentAlignment = Alignment.Center) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Welcome back", style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, singleLine = true)
            OutlinedTextField(
                singleLine = true, visualTransformation = PasswordVisualTransformation(),value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done)
            )
            Button(onClick = { viewModel.login(email.trim(), password.trim()) }, modifier = Modifier.fillMaxWidth()) {
                Text("Login")
            }
            TextButton(onClick = { navController.navigate("signup") }) {
                Text("Create account")
            }
        }
    }
}


