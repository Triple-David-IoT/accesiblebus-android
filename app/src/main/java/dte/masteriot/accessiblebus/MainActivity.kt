package dte.masteriot.accessiblebus

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dte.masteriot.accessiblebus.navigation.AppNav
import dte.masteriot.accessiblebus.sensors.SensorTestActivity
import dte.masteriot.accessiblebus.ui.theme.AppTheme
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val container: AppContainer
        get() = (application as AccessibleBusApplication).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val repository = container.repository
        val appConfigRepository = container.appConfigRepository

        lifecycleScope.launch {
            val activeProfile = appConfigRepository.activeProfile().first()

            lifecycleScope.launch {
                val activeProfile = appConfigRepository.activeProfile().first()

                Log.d("MainActivity", "Active profile: $activeProfile")

                repository.connectMqtt(activeProfile)
            }

            val connected = repository.connectMqtt(activeProfile)

        }

        setContent {
            AppTheme(useHighContrast = false) {
                AppNav()
            }
        }
    }
}
