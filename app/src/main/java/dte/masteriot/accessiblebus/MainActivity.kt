package dte.masteriot.accessiblebus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dte.masteriot.accessiblebus.core.net.ApiSource
import dte.masteriot.accessiblebus.navigation.AppNav
import dte.masteriot.accessiblebus.ui.theme.AppTheme

class MainActivity : ComponentActivity() {

    private val container: AppContainer
        get() = (application as AccessibleBusApplication).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val apiSource : ApiSource = container.apiSource

        setContent {
            AppTheme(useHighContrast = false) {
                AppNav()
            }
        }
    }
}