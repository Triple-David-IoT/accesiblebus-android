package dte.masteriot.accessiblebus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import dte.masteriot.accessiblebus.ui.AppNavigation

class MainActivity : ComponentActivity() {


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            /*
            TEMA {
                AppNavigation(viewModel = viewModel)
            }
            */
        }
    }
}