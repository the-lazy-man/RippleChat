package com.example.ripplechat.app.data.model.ui.theme.navigation



import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.ripplechat.app.data.model.ui.theme.screens.home.DashboardScreen
import com.example.ripplechat.app.data.model.ui.theme.screens.login.LoginScreen
import com.example.ripplechat.app.data.model.ui.theme.screens.signup.SignupScreen
import com.example.ripplechat.app.data.model.ui.theme.screens.splash.SplashScreen
import com.example.ripplechat.app.ui.chat.ChatScreen

@Composable
fun NavGraph(navController: NavHostController) {
    NavHost(navController, startDestination = "splash") {
        composable("splash") { SplashScreen(navController) }
        composable("login") { LoginScreen(navController) }
        composable("signup") { SignupScreen(navController) }
        composable("dashboard") { DashboardScreen(navController)  }
        composable(
            route = "chat/{chatId}/{peerUid}/{peerName}"
        ) { backStackEntry ->
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
