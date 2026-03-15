package com.aymanelbanhawy.enterprisepdf.app.security

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aymanelbanhawy.editor.core.security.AppLockSettingsModel
import com.aymanelbanhawy.editor.core.security.AppLockStateModel

@Composable
fun AppLockDialog(
    settings: AppLockSettingsModel,
    state: AppLockStateModel,
    onUnlockWithPin: (String) -> Unit,
    onUnlockWithBiometric: () -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = {},
        confirmButton = {
            Button(onClick = { onUnlockWithPin(pin) }) { Text("Unlock") }
        },
        dismissButton = {
            if (settings.biometricsEnabled) {
                Button(onClick = onUnlockWithBiometric) { Text("Use biometrics") }
            }
        },
        title = { Text("App Locked") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Enter your PIN to continue. Failed attempts: ${state.failedPinAttempts}")
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it },
                    label = { Text("PIN") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        },
    )
}
