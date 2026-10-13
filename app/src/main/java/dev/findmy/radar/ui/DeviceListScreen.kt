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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.findmy.radar.ble.FindMyDevice
import dev.findmy.radar.crypto.AccessoryIdentity
import dev.findmy.radar.util.parseKeyBytes
import java.util.Locale

private enum class ListFilter(val label: String) {
    ALL("Alle"),
    AIRTAGS("AirTags"),
    NAMED("Benannt"),
    UNNAMED("Unbenannt"),
}

@Composable
fun DeviceListScreen(
    devices: List<FindMyDevice>,
    isScanning: Boolean,
    hasPermissions: Boolean,
    bluetoothOn: Boolean,
    keyFilterHex: String,
    deviceNames: Map<String, String>,
    suggestions: Map<String, String>,
    existingNames: List<String>,
    idLogSize: Int,
    accessories: List<AccessoryIdentity>,
    backgroundScan: Boolean,
    onBackgroundScanChange: (Boolean) -> Unit,
    onImportAccessory: (String) -> String?,
    onDeleteAccessory: (AccessoryIdentity) -> Unit,
    onKeyFilterChange: (String) -> Unit,
    onRenameDevice: (FindMyDevice, String?) -> Unit,
    onBuildExport: () -> String,
    onRequestPermissions: () -> Unit,
    onEnableBluetooth: () -> Unit,
    onRefreshBluetooth: () -> Unit,
    onPickTarget: (FindMyDevice) -> Unit,
) {
    var renaming by remember { mutableStateOf<FindMyDevice?>(null) }
    var managingTags by remember { mutableStateOf(false) }
    var chipFilter by remember { mutableStateOf(ListFilter.ALL) }
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "AirTag Finder",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { managingTags = true }) {
                Text("Eigene Tags (${accessories.size})")
            }
            IconButton(onClick = {
                val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "application/json"
                    putExtra(android.content.Intent.EXTRA_TEXT, onBuildExport())
                }
                context.startActivity(
                    android.content.Intent.createChooser(send, "ID-Log exportieren"),
                )
            }) {
                Icon(Icons.Default.Share, contentDescription = "Log exportieren")
            }
        }
        Text(
            if (isScanning) "Scanne nach FindMy-Geräten…" else "Scanner gestoppt",
            style = MaterialTheme.typography.bodyMedium,
            color = if (isScanning) Color(0xFF69F0AE) else Color.Gray,
        )
        Text(
            "Bekannte IDs im Log: $idLogSize",
            style = MaterialTheme.typography.labelSmall,
            color = Color.Gray,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Im Hintergrund weiter scannen",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = backgroundScan, onCheckedChange = onBackgroundScanChange)
        }

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

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ListFilter.entries.forEach { f ->
                FilterChip(
                    selected = chipFilter == f,
                    onClick = { chipFilter = f },
                    label = { Text(f.label) },
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        val keyFilter = remember(keyFilterHex) { parseKeyBytes(keyFilterHex) }
        val filtered = devices.filter { dev ->
            (keyFilterHex.isBlank() || keyFilter == null || dev.matchesAdvKey(keyFilter)) &&
                when (chipFilter) {
                    ListFilter.ALL -> true
                    ListFilter.AIRTAGS -> dev.isAirTag
                    ListFilter.NAMED -> deviceNames[dev.nameKey] != null
                    ListFilter.UNNAMED -> deviceNames[dev.nameKey] == null
                }
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
                    customName = deviceNames[device.nameKey],
                    suggestion = suggestions[device.nameKey],
                    isMine = keyFilter != null && device.matchesAdvKey(keyFilter),
                    onPickTarget = { onPickTarget(device) },
                    onRename = { renaming = device },
                    onAcceptSuggestion = { name -> onRenameDevice(device, name) },
                )
            }
        }
    }

    renaming?.let { device ->
        RenameDialog(
            device = device,
            currentName = deviceNames[device.nameKey],
            existingNames = existingNames,
            onDismiss = { renaming = null },
            onSave = { name ->
                onRenameDevice(device, name)
                renaming = null
            },
        )
    }

    if (managingTags) {
        AccessoryDialog(
            accessories = accessories,
            onImport = onImportAccessory,
            onDelete = onDeleteAccessory,
            onDismiss = { managingTags = false },
        )
    }
}

@Composable
private fun AccessoryDialog(
    accessories: List<AccessoryIdentity>,
    onImport: (String) -> String?,
    onDelete: (AccessoryIdentity) -> Unit,
    onDismiss: () -> Unit,
) {
    var json by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Eigene Tags") },
        text = {
            Column {
                Text(
                    "FindMyAccessory-JSON (aus backend/tags/) importieren. Danach wird " +
                        "der Tag über jede Schlüsselrotation hinweg erkannt.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                )
                Spacer(Modifier.height(8.dp))
                accessories.forEach { acc ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            acc.displayName + (acc.identifier?.let { " · $it" } ?: ""),
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { onDelete(acc) }) { Text("Löschen") }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = json,
                    onValueChange = { json = it; error = null },
                    label = { Text("Accessory-JSON einfügen") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let {
                    Text(it, color = Color(0xFFFF8A65), style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    error = onImport(json)
                    if (error == null) json = ""
                },
                enabled = json.isNotBlank(),
            ) { Text("Importieren") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Schließen") }
        },
    )
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
private fun RenameDialog(
    device: FindMyDevice,
    currentName: String?,
    existingNames: List<String>,
    onDismiss: () -> Unit,
    onSave: (String?) -> Unit,
) {
    var text by remember { mutableStateOf(currentName ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Gerät benennen") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Name") },
                    singleLine = true,
                )
                if (existingNames.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Bestehendem Gerät zuordnen:",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray,
                    )
                    Row {
                        existingNames.take(4).forEach { name ->
                            TextButton(onClick = { text = name }) { Text(name) }
                        }
                    }
                }
                Text(
                    "Bleibt gespeichert, bis der Advertisement-Key rotiert (~15 Min). " +
                        "Identität: ${device.nameKey}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(text.ifBlank { null }) }) { Text("Speichern") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        },
    )
}

@Composable
private fun DeviceCard(
    device: FindMyDevice,
    customName: String?,
    suggestion: String?,
    isMine: Boolean,
    onPickTarget: () -> Unit,
    onRename: () -> Unit,
    onAcceptSuggestion: (String) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            customName ?: device.deviceType,
                            style = MaterialTheme.typography.titleMedium,
                        )
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
                    if (customName != null) {
                        Text(
                            device.deviceType,
                            style = MaterialTheme.typography.bodySmall,
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
                IconButton(onClick = onRename) {
                    Icon(Icons.Default.Edit, contentDescription = "Umbenennen")
                }
            }
            if (customName == null && suggestion != null) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Vermutlich „$suggestion“? (Key rotiert)",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFFFD54F),
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { onAcceptSuggestion(suggestion) }) {
                        Text("Zuordnen")
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onPickTarget, modifier = Modifier.fillMaxWidth()) {
                Text("Anpeilen")
            }
        }
    }
}


