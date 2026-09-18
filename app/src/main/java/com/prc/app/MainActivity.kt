package com.prc.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.navigation.compose.rememberNavController
import com.prc.app.data.AuthRepository
import com.prc.app.navigation.AppNavGraph
import com.prc.app.ui.theme.PRCTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Fully transparent system bars on every theme — pages paint their own
        // background (heroes, chrome) edge-to-edge behind the bars, so the
        // status bar always blends with the visible page (green hero, ivory,
        // AMOLED black, …). Icon appearance is synced per-screen via
        // SystemBarAppearance().
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        // Initial appearance: light pages show dark icons. Each screen root
        // re-syncs via SystemBarAppearance() as the user navigates, so the
        // transparent system bars always blend with the visible page.
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        AuthRepository.init(this)
        com.prc.app.data.PersistedState.init(this)
        com.prc.app.data.JobRepository.startFirestoreSync()
        com.prc.app.data.JobAlertRepository.startObserving()
        com.prc.app.data.NotificationRepository.startBoostExpiryWatcher(
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default)
        )
        com.prc.app.data.NotificationRepository.startProExpiryWatcher(
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default)
        )
        com.prc.app.data.SavedRepository.startFirestoreSync()
        com.prc.app.data.JobRepository.backfillIsCompanyForProviderJobs(this)
        com.prc.app.data.JobRepository.migrateLegacyCategories(this)
        com.prc.app.data.WeeklyDigestWorker.schedule(this)
        com.prc.app.data.DeadlineExpiryScheduler.schedule(this)
        // Register this device for real push notifications (FCM).
        com.google.firebase.messaging.FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
                com.prc.app.push.PushTokenStore.register(token)
            }
        setContent {
            PRCTheme {   // follows system light/dark; screens adapt via colorScheme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    AppNavGraph(navController)
                }
            }
        }
    }
}
