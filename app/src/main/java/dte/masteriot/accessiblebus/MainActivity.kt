package dte.masteriot.accessiblebus

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dte.masteriot.accessiblebus.navigation.AppNav
import dte.masteriot.accessiblebus.ui.theme.AppTheme

class MainActivity : ComponentActivity() {

    private val container: AppContainer
        get() = (application as AccessibleBusApplication).container

    private val powerManager: PowerManager
        get() = getSystemService(Context.POWER_SERVICE) as PowerManager

    private var isActivityResumed = false
    private var receiverRegistered = false

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            updateAnnouncementAvailability()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AppTheme(useHighContrast = false) {
                AppNav()
            }
        }
    }

    override fun onStart() {
        super.onStart()

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(
                screenStateReceiver,
                filter,
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(screenStateReceiver, filter)
        }

        receiverRegistered = true
        updateAnnouncementAvailability()
        container.refreshOnReturn()
    }

    override fun onResume() {
        super.onResume()
        isActivityResumed = true
        updateAnnouncementAvailability()
    }

    override fun onPause() {
        isActivityResumed = false
        updateAnnouncementAvailability()
        super.onPause()
    }

    override fun onStop() {
        if (receiverRegistered) {
            unregisterReceiver(screenStateReceiver)
            receiverRegistered = false
        }

        super.onStop()
    }

    private fun updateAnnouncementAvailability() {
        container.setAnnouncementAvailability(
            isActivityResumed && powerManager.isInteractive
        )
    }
}