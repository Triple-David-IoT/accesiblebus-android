
package dte.masteriot.accessiblebus.ui.profile

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
fun ProfileScreen(
    onPassengerSelected: () -> Unit,
    onDriverSelected: () -> Unit
) {
    val landscape =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Select your profile",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics {
                heading()
            }
        )

        Spacer(modifier = Modifier.height(32.dp))

        if (landscape) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = onPassengerSelected,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 56.dp)
                ) {
                    Text("Passenger")
                }

                Button(
                    onClick = onDriverSelected,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 56.dp)
                ) {
                    Text("Driver")
                }
            }
        } else {
            Button(
                onClick = onPassengerSelected,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
            ) {
                Text("Passenger")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onDriverSelected,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
            ) {
                Text("Driver")
            }
        }
    }
}
