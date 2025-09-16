package com.example.ripplechat

import LoginScreen
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.ripplechat.app.data.model.ui.theme.screens.signup.SignupScreen
import com.example.ripplechat.app.data.model.ui.theme.screens.splash.SplashScreen
import com.example.ripplechat.app.ui.chat.ChatScreen
import com.example.ripplechat.app.ui.dashboard.DashboardScreen // FIX: Use the correct path for DashboardScreen
import com.example.ripplechat.app.ui.profile.ProfileScreen

@Composable
fun NavGraph(navController: NavHostController) {
    NavHost(navController, startDestination = "splash") {
        composable("splash") { SplashScreen(navController) }
        composable("login") { LoginScreen(navController) }
        composable("signup") { SignupScreen(navController) }
        composable("dashboard") { DashboardScreen(navController)  }
        // FIX: The ProfileScreen in DashboardScreen now navigates to the "profile" route.
        // The composable block will handle creating the ViewModel.
        composable("profile") { ProfileScreen(navController)}
        composable(
            route = "chat/{chatId}/{peerUid}/{peerName}"
        ) { backStackEntry ->
            // Use safe argument retrieval with default empty strings
            val chatId = backStackEntry.arguments?.getString("chatId") ?: ""
            val peerUid = backStackEntry.arguments?.getString("peerUid") ?: ""
            val peerName = backStackEntry.arguments?.getString("peerName") ?: ""

            ChatScreen(
                navController = navController,
                chatId = chatId,
                peerUid = peerUid,
                peerName = peerName
            )
        }
    }
}