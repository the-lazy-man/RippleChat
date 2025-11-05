package com.example.ripplechat

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.example.ripplechat.app.data.local.NotificationPreferences
import com.example.ripplechat.data.repository.ChatRepository
import com.example.ripplechat.ui.theme.RippleChatTheme
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import androidx.work.WorkManager
import androidx.work.OneTimeWorkRequestBuilder
import java.util.concurrent.TimeUnit
import com.example.ripplechat.app.data.local.PresenceWorker


@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var chatRepository: ChatRepository

    @Inject
    lateinit var notificationPreferences: NotificationPreferences

    private var pendingNavigation: Triple<String, String, String>? = null
    private lateinit var navController: NavHostController

    var hasPendingNotificationNavigation = false
        private set

    private val NOTIFICATION_PERMISSION_REQUEST = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermission()
        lifecycle.addObserver(AppPresenceObserver())
        handleIntentForNavigation(intent)

        setContent {
            RippleChatTheme {
                navController = rememberNavController()
                NavGraph(navController)

                LaunchedEffect(navController) {
                    if (hasPendingNotificationNavigation) {
                        pendingNavigation?.let {
                            navController.navigate("chat/${it.first}/${it.second}/${it.third}") {
                                launchSingleTop = true
                            }
                            hasPendingNotificationNavigation = false
                            pendingNavigation = null
                        }
                    }
                }
            }
        }
    }

    private fun handleIntentForNavigation(intent: Intent?) {
        val chatId = intent?.getStringExtra("chatId")
        val peerUid = intent?.getStringExtra("peerUid")
        val peerName = intent?.getStringExtra("peerName")

        if (!chatId.isNullOrEmpty() &&
            !peerUid.isNullOrEmpty() &&
            !peerName.isNullOrEmpty()
        ) {
            pendingNavigation = Triple(chatId, peerUid, peerName)
            hasPendingNotificationNavigation = true
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        handleIntentForNavigation(intent)

        if (this::navController.isInitialized && hasPendingNotificationNavigation) {
            pendingNavigation?.let {
                navController.navigate("chat/${it.first}/${it.second}/${it.third}") {
                    launchSingleTop = true
                }
                hasPendingNotificationNavigation = false
                pendingNavigation = null
            }
        }
    }

    private inner class AppPresenceObserver : DefaultLifecycleObserver {
        private val userId = FirebaseAuth.getInstance().currentUser?.uid

        override fun onStart(owner: LifecycleOwner) {
            WorkManager.getInstance(this@MainActivity).cancelAllWork()
            userId?.let { chatRepository.setPresence(it, true) }
        }

        override fun onResume(owner: LifecycleOwner) {
            userId?.let { chatRepository.setPresence(it, true) }
        }

        override fun onStop(owner: LifecycleOwner) {
            userId?.let { chatRepository.setPresence(it, false) }
            val userId = FirebaseAuth.getInstance().currentUser?.uid

            if (userId != null) {
                val workRequest = OneTimeWorkRequestBuilder<PresenceWorker>()
                    .setInitialDelay(10, TimeUnit.SECONDS)
                    .build()

                WorkManager.getInstance(this@MainActivity).enqueue(workRequest)
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_PERMISSION_REQUEST
                )
            }
        }
    }
}
