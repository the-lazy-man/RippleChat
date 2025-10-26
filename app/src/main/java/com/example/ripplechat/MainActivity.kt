package com.example.ripplechat

import android.Manifest // <-- NEW IMPORT
import android.content.pm.PackageManager // <-- NEW IMPORT
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
// ... (existing compose imports)
import androidx.core.app.ActivityCompat // <-- NEW IMPORT
import androidx.core.content.ContextCompat // <-- NEW IMPORT
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.navigation.compose.rememberNavController
import com.example.ripplechat.data.repository.ChatRepository
import com.example.ripplechat.ui.theme.RippleChatTheme
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var chatRepository: ChatRepository

    // Define the request code for the permission check
    private val NOTIFICATION_PERMISSION_REQUEST_CODE = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 🚨 CALL THE PERMISSION LOGIC HERE 🚨
        requestNotificationPermission()
        lifecycle.addObserver(AppPresenceObserver())

        setContent {
            RippleChatTheme {
                val navController = rememberNavController()
                NavGraph(navController = navController)
            }
        }
    }

    // NEW INNER CLASS: Observer to handle app foreground/background
    private inner class AppPresenceObserver : DefaultLifecycleObserver {
        private val userId = FirebaseAuth.getInstance().currentUser?.uid

        override fun onStart(owner: LifecycleOwner) {
            super.onStart(owner)
            // App is in foreground, set user online
            userId?.let { chatRepository.setPresence(it, true) }
        }

        override fun onStop(owner: LifecycleOwner) {
            super.onStop(owner)
            // App is in background, set user offline
            userId?.let { chatRepository.setPresence(it, false) }
        }
    }
    /**
     * Handles the explicit request for POST_NOTIFICATIONS on Android 13 (API 33) and above.
     * This stops the silent denial on app reinstalls.
     */
    private fun requestNotificationPermission() {
        // Check only for Android 13 (Tiramisu) and higher
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // Permission is not granted, request it
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_PERMISSION_REQUEST_CODE
                )
            }
        }
        // Note: You can optionally override onRequestPermissionsResult
        // to handle the user's choice, though it's often not strictly
        // necessary just for a simple notification service.
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    RippleChatTheme {
        Greeting("Android")
    }
}
