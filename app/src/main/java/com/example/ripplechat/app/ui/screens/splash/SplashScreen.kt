package com.example.ripplechat.app.data.model.ui.theme.screens.splash


import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(navController: NavController, viewModel: SplashViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    val scale = remember { Animatable(0.6f) }

    LaunchedEffect(Unit) {
        scale.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing)
        )
        delay(800)
        val isLoggedIn = viewModel.isUserLoggedIn()
        if (isLoggedIn) navController.navigate("dashboard") { popUpTo("splash") { inclusive = true } }
        else navController.navigate("login") { popUpTo("splash") { inclusive = true } }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "RippleChat",
            fontSize = 40.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.scale(scale.value)
        )
    }
}
