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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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

        if (target != null) {
            RadarScreen(
                device = liveTarget,
                onBack = { target = null },
            )
        } else {
            DeviceListScreen(
                devices = devices.values.sortedByDescending { it.rssi },
                isScanning = isScanning,
                hasPermissions = hasPermissions,
                bluetoothOn = bluetoothOn,
                keyFilterHex = keyFilterHex,
                onKeyFilterChange = { hex ->
                    keyFilterHex = hex
                    getPreferences(MODE_PRIVATE).edit().putString(PREF_KEY_FILTER, hex).apply()
                },
                onRequestPermissions = { permissionLauncher.launch(requiredPermissions()) },
                onEnableBluetooth = {
                    btEnableLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                },
                onRefreshBluetooth = { bluetoothOn = scanner.bluetoothEnabled },
                onPickTarget = { target = it },
            )
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
