package com.example.ripplechat

import androidx.navigation.navDeepLink
import com.google.firebase.auth.FirebaseAuth
import androidx.compose.runtime.LaunchedEffect
import LoginScreen
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.ripplechat.app.data.model.ui.theme.screens.signup.SignupScreen
import com.example.ripplechat.app.data.model.ui.theme.screens.splash.SplashScreen
import com.example.ripplechat.app.ui.chat.ChatScreen
import com.example.ripplechat.app.ui.dashboard.DashboardScreen
import com.example.ripplechat.app.ui.profile.ProfileScreen
import com.example.ripplechat.app.ui.status.MyStatusViewerScreen
import com.example.ripplechat.app.ui.status.OthersStatusViewerScreen
import com.example.ripplechat.app.ui.chat.NewGroupScreen
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.ripplechat.app.data.model.ui.theme.screens.home.DashBoardVM

@Composable
fun NavGraph(navController: NavHostController) {
    NavHost(navController, startDestination = "splash") {
        composable("splash") { SplashScreen(navController) }
        composable("login") { LoginScreen(navController) }
        composable("signup") { SignupScreen(navController) }
        composable("dashboard") { DashboardScreen(navController) }
        composable("profile") { ProfileScreen(navController) }
        
        composable("create_group") {
            val dashboardVM: DashBoardVM = hiltViewModel()
            NewGroupScreen(navController, dashboardVM)
        }
        
        // Deep Link Route for starting a chat
        composable(
            route = "deeplink_chat/{peerUid}/{peerName}",
            deepLinks = listOf(
                navDeepLink { uriPattern = "ripplechat://chat/{peerUid}/{peerName}" }
            )
        ) { backStackEntry ->
            val peerUid = backStackEntry.arguments?.getString("peerUid") ?: ""
            val peerName = backStackEntry.arguments?.getString("peerName") ?: ""
            val currentUid = FirebaseAuth.getInstance().currentUser?.uid
            
            if (currentUid != null) {
                val chatId = if (currentUid < peerUid) "$currentUid-$peerUid" else "$peerUid-$currentUid"
                ChatScreen(
                    navController = navController,
                    chatId = chatId,
                    peerUid = peerUid,
                    peerName = peerName
                )
            } else {
                LaunchedEffect(Unit) {
                    navController.navigate("login") { popUpTo(0) }
                }
            }
        }
        
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
        
        // My Status Viewer
        composable("my_status_viewer") {
            MyStatusViewerScreen(navController = navController)
        }
        
        // Others' Status Viewer
        composable("status_viewer/{userId}") { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId") ?: ""
            OthersStatusViewerScreen(
                navController = navController,
                userId = userId
            )
        }
    }
}
