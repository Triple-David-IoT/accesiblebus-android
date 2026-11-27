package dte.masteriot.accessiblebus

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import dte.masteriot.accessiblebus.core.config.AccessibilityPreferences
import java.util.Locale
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
            val accessibilityPreferences by container.appConfigRepository
                .accessibilityPreferences()
                .collectAsState(
                    initial = AccessibilityPreferences()
                )

            val localizedContext = remember(accessibilityPreferences.language) {
                val locale = Locale.forLanguageTag(
                    accessibilityPreferences.language.tag
                )
                Locale.setDefault(locale)
                val configuration = Configuration(resources.configuration)
                configuration.setLocale(locale)
                createConfigurationContext(configuration)
            }

            CompositionLocalProvider(
                LocalContext provides localizedContext,
                LocalConfiguration provides localizedContext.resources.configuration
            ) {
                AppTheme(
                    useHighContrast = accessibilityPreferences.highContrastEnabled
                ) {
                    AppNav()
                }
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
