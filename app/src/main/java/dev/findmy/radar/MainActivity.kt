package dev.findmy.radar

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dev.findmy.radar.ble.FindMyDevice
import dev.findmy.radar.ble.FindMyScanner
import dev.findmy.radar.util.IdLog
import dev.findmy.radar.ui.DeviceListScreen
import dev.findmy.radar.ui.MapScreen
import dev.findmy.radar.ui.RadarScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var scanner: FindMyScanner

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scanner = FindMyScanner(this)

        // Periodically drop devices that went silent so the list stays fresh.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    delay(2_000)
                    scanner.prune()
                }
            }
        }

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppContent(scanner)
                }
            }
        }
    }

    override fun onDestroy() {
        scanner.stop()
        super.onDestroy()
    }

    @Composable
    private fun AppContent(scanner: FindMyScanner) {
        var hasPermissions by remember { mutableStateOf(requiredPermissions().all(::isGranted)) }
        var bluetoothOn by remember { mutableStateOf(scanner.bluetoothEnabled) }
        var target by remember { mutableStateOf<FindMyDevice?>(null) }
        var keyFilterHex by remember {
            mutableStateOf(getPreferences(MODE_PRIVATE).getString(PREF_KEY_FILTER, "") ?: "")
        }
        var deviceNames by remember { mutableStateOf(loadDeviceNames()) }
        val idLog = remember { IdLog.decode(getPreferences(MODE_PRIVATE).getString(PREF_ID_LOG, "") ?: "") }
        var idLogSize by remember { mutableStateOf(idLog.entries.size) }

        val permissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) { grants ->
            hasPermissions = grants.values.all { it }
        }
        val btEnableLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) {
            bluetoothOn = scanner.bluetoothEnabled
        }

        // React to permission/bluetooth state: start or stop the scan.
        LaunchedEffect(hasPermissions, bluetoothOn) {
            if (hasPermissions && bluetoothOn) {
                scanner.start()
            } else {
                scanner.stop()
            }
        }

        val devices by scanner.devices.collectAsState()
        val isScanning by scanner.isScanning.collectAsState()

        // Log every observed identity and persist it (SharedPreferences.apply is async).
        LaunchedEffect(devices) {
            val now = System.currentTimeMillis()
            devices.values.forEach { idLog.observe(it.nameKey, it.mac, now) }
            idLog.prune(now, deviceNames, UNNAMED_TTL_MS, NAMED_TTL_MS)
            idLogSize = idLog.entries.size
            getPreferences(MODE_PRIVATE).edit().putString(PREF_ID_LOG, idLog.encode()).apply()
        }

        // For each unnamed live device: did a named identity just rotate into it?
        val now = System.currentTimeMillis()
        val liveKeys = devices.values.mapTo(HashSet()) { it.nameKey }
        val suggestions = devices.values
            .filter { deviceNames[it.nameKey] == null }
            .mapNotNull { dev ->
                IdLog.findLikelyPredecessor(idLog, deviceNames, dev.nameKey, liveKeys, now)
                    ?.let { dev.nameKey to it }
            }
            .toMap()

        // Resolve the pinned target against the latest advertisements: by MAC, or by
        // advertisement-key prefix (survives the nearby<->separated state switch).
        val liveTarget = target?.let { t ->
            devices[t.mac] ?: devices.values.firstOrNull { it.matchesAdvKey(t.advKeyBytes) }
        }

        var tab by remember { mutableStateOf(0) }

        Scaffold(
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = tab == 0,
                        onClick = { tab = 0 },
                        icon = { Icon(Icons.Default.Search, contentDescription = null) },
                        label = { Text("Scanner") },
                    )
                    NavigationBarItem(
                        selected = tab == 1,
                        onClick = { tab = 1 },
                        icon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                        label = { Text("Karte") },
                    )
                }
            },
        ) { innerPadding ->
            Box(Modifier.padding(innerPadding)) {
                if (target != null) {
                    RadarScreen(
                        device = liveTarget,
                        displayName = liveTarget?.let { deviceNames[it.nameKey] },
                        onBack = { target = null },
                    )
                } else if (tab == 1) {
                    MapScreen()
                } else {
                    DeviceListScreen(
                        devices = devices.values.sortedByDescending { it.rssi },
                        isScanning = isScanning,
                        hasPermissions = hasPermissions,
                        bluetoothOn = bluetoothOn,
                        keyFilterHex = keyFilterHex,
                        deviceNames = deviceNames,
                        suggestions = suggestions,
                        existingNames = deviceNames.values.toSortedSet().toList(),
                        idLogSize = idLogSize,
                        onKeyFilterChange = { hex ->
                            keyFilterHex = hex
                            getPreferences(MODE_PRIVATE).edit()
                                .putString(PREF_KEY_FILTER, hex).apply()
                        },
                        onRenameDevice = { device, name ->
                            saveDeviceName(device.nameKey, name)
                            deviceNames = loadDeviceNames()
                        },
                        onRequestPermissions = {
                            permissionLauncher.launch(requiredPermissions())
                        },
                        onEnableBluetooth = {
                            btEnableLauncher.launch(
                                Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE),
                            )
                        },
                        onRefreshBluetooth = { bluetoothOn = scanner.bluetoothEnabled },
                        onPickTarget = { target = it },
                    )
                }
            }
        }
    }

    private fun isGranted(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    /** All stored device names, keyed by the device's 6-byte key-prefix hex. */
    private fun loadDeviceNames(): Map<String, String> =
        getPreferences(MODE_PRIVATE).all
            .filterKeys { it.startsWith(PREF_NAME_PREFIX) }
            .mapKeys { it.key.removePrefix(PREF_NAME_PREFIX) }
            .mapValues { it.value.toString() }

    private fun saveDeviceName(nameKey: String, name: String?) {
        getPreferences(MODE_PRIVATE).edit().apply {
            if (name == null) remove(PREF_NAME_PREFIX + nameKey)
            else putString(PREF_NAME_PREFIX + nameKey, name)
        }.apply()
    }

    companion object {
        private const val PREF_KEY_FILTER = "own_adv_key_hex"
        private const val PREF_NAME_PREFIX = "name:"
        private const val PREF_ID_LOG = "id_log"
        private const val UNNAMED_TTL_MS = 24L * 3600_000
        private const val NAMED_TTL_MS = 30L * 24 * 3600_000

        fun requiredPermissions(): Array<String> =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                )
            } else {
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
            }
    }
}
