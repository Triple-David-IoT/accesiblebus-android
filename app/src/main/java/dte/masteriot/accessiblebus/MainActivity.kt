package dte.masteriot.accessiblebus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dte.masteriot.accessiblebus.navigation.AppNav
import dte.masteriot.accessiblebus.ui.theme.AppTheme

class MainActivity : ComponentActivity() {

    private val container: AppContainer
        get() = (application as AccessibleBusApplication).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = container.repository

        setContent {
            AppTheme(useHighContrast = false) {
                AppNav()
            }
        }
    }
}
