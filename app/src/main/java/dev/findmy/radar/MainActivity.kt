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
                        onKeyFilterChange = { hex ->
                            keyFilterHex = hex
                            getPreferences(MODE_PRIVATE).edit()
                                .putString(PREF_KEY_FILTER, hex).apply()
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

    companion object {
        private const val PREF_KEY_FILTER = "own_adv_key_hex"

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
