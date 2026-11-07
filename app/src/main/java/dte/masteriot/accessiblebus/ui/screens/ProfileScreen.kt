package dte.masteriot.accessiblebus.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dte.masteriot.accessiblebus.core.config.ConnectionProfile

@Composable
fun ProfileScreen(
    onSelectProfile: (ConnectionProfile) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Select profile",
            style = MaterialTheme.typography.headlineMedium
        )

        Button(
            onClick = {
                onSelectProfile(ConnectionProfile.LABORATORY)
            },
            modifier = Modifier.padding(top = 24.dp)
        ) {
            Text("Laboratory")
        }

        Button(
            onClick = {
                onSelectProfile(ConnectionProfile.COURSE)
            },
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Text("Course")
        }
    }
}