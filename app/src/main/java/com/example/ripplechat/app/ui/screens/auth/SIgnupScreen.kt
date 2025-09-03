package com.example.ripplechat.app.data.model.ui.theme.screens.signup

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.ripplechat.app.data.model.ui.theme.screens.login.AuthState
import com.example.ripplechat.app.data.model.ui.theme.screens.login.AuthViewModel

@Composable
fun SignupScreen(navController: NavController, viewModel: AuthViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    val ctx = LocalContext.current
    val state by viewModel.signupState.collectAsState()

    var name by remember { mutableStateOf("") }
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
                Toast.makeText(ctx, "Signup success", Toast.LENGTH_SHORT).show()
                navController.navigate("dashboard") { popUpTo("signup") { inclusive = true } }
            }
            is AuthState.Loading -> showProgressbar = true

            is AuthState.Error -> {
                Toast.makeText(ctx, (state as AuthState.Error).message, Toast.LENGTH_SHORT).show()
                viewModel.clearSignupState()
            }
            else -> {}
        }
    }

    Box(modifier = Modifier.wrapContentSize(), contentAlignment = Alignment.Center) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp),horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Create account", style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") })
            OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") })
            OutlinedTextField(singleLine = true, visualTransformation = PasswordVisualTransformation(),value = password, onValueChange = { password = it }, label = { Text("Password") })
            Button(onClick = { viewModel.signup(name.trim(), email.trim(), password.trim()) }, modifier = Modifier.fillMaxWidth()) {
                Text("Sign up")
            }
            TextButton(onClick = { navController.navigate("login") }) { Text("Already have an account?") }
        }
    }
}

