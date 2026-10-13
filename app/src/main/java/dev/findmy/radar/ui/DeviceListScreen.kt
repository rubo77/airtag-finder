package dev.findmy.radar.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.findmy.radar.ble.FindMyDevice
import dev.findmy.radar.util.parseKeyBytes
import java.util.Locale

@Composable
fun DeviceListScreen(
    devices: List<FindMyDevice>,
    isScanning: Boolean,
    hasPermissions: Boolean,
    bluetoothOn: Boolean,
    keyFilterHex: String,
    onKeyFilterChange: (String) -> Unit,
    onRequestPermissions: () -> Unit,
    onEnableBluetooth: () -> Unit,
    onRefreshBluetooth: () -> Unit,
    onPickTarget: (FindMyDevice) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Text("AirTag Finder", style = MaterialTheme.typography.headlineMedium)
        Text(
            if (isScanning) "Scanne nach FindMy-Geräten…" else "Scanner gestoppt",
            style = MaterialTheme.typography.bodyMedium,
            color = if (isScanning) Color(0xFF69F0AE) else Color.Gray,
        )

        Spacer(Modifier.height(12.dp))

        when {
            !hasPermissions -> PermissionCard(
                text = "Die App braucht die Berechtigung „Geräte in der Nähe\" " +
                    "(bzw. Standort auf Android < 12), um nach Bluetooth-Geräten zu suchen.",
                buttonText = "Berechtigung erteilen",
                onClick = onRequestPermissions,
            )
            !bluetoothOn -> PermissionCard(
                text = "Bluetooth ist ausgeschaltet.",
                buttonText = "Bluetooth aktivieren",
                onClick = onEnableBluetooth,
            )
        }

        OutlinedTextField(
            value = keyFilterHex,
            onValueChange = onKeyFilterChange,
            label = { Text("Eigener Advertisement-Key (Hex/Base64, optional)") },
            supportingText = {
                Text("Aus FindMy.py: adv_key_b64 deines AirTags — filtert die Liste auf dein Gerät.")
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(12.dp))

        val keyFilter = remember(keyFilterHex) { parseKeyBytes(keyFilterHex) }
        val filtered = devices.filter { dev ->
            keyFilterHex.isBlank() || keyFilter == null || dev.matchesAdvKey(keyFilter)
        }

        if (filtered.isEmpty()) {
            Text(
                "Noch keine Geräte gefunden. Halte dich in der Nähe deines AirTags auf —\n" +
                    "er sendet alle ~2 s ein Advertisement.",
                color = Color.Gray,
            )
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(filtered, key = { it.mac }) { device ->
                DeviceCard(
                    device,
                    isMine = keyFilter != null && device.matchesAdvKey(keyFilter),
                    onPickTarget = { onPickTarget(device) },
                )
            }
        }
    }
}

@Composable
private fun PermissionCard(text: String, buttonText: String, onClick: () -> Unit) {
    Card(modifier = Modifier
        .fillMaxWidth()
        .padding(bottom = 12.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(text)
            Spacer(Modifier.height(8.dp))
            Button(onClick = onClick) { Text(buttonText) }
        }
    }
}

@Composable
private fun DeviceCard(device: FindMyDevice, isMine: Boolean, onPickTarget: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(device.deviceType, style = MaterialTheme.typography.titleMedium)
                        if (isMine) {
                            Spacer(Modifier.width(8.dp))
                            Surface(
                                color = Color(0xFF1B5E20),
                                shape = MaterialTheme.shapes.small,
                            ) {
                                Text(
                                    "DEINS",
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFA5D6A7),
                                )
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (device is FindMyDevice.Separated) "getrennt" else "in der Nähe",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray,
                        )
                    }
                    Text(device.mac, fontFamily = FontFamily.Monospace, color = Color.Gray)
                    Text(
                        "Key: ${device.keyHexShort}…",
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                    )
                    Text(
                        "Batterie: ${device.batteryLevel} · ${device.rssi} dBm · " +
                            String.format(Locale.GERMANY, "%.1f m", device.estimatedDistanceMeters),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onPickTarget, modifier = Modifier.fillMaxWidth()) {
                Text("Anpeilen")
            }
        }
    }
}


