package com.example.ripplechat.app.data.model.ui.theme.screens.signup

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.ripplechat.app.ui.auth.AuthState
import com.example.ripplechat.app.ui.auth.AuthViewModel

@Composable
fun SignupScreen(navController: NavController, viewModel: AuthViewModel = hiltViewModel()) {
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

