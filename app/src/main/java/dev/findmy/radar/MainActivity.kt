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
import dev.findmy.radar.ble.ScanController
import dev.findmy.radar.crypto.AccessoryIdentity
import dev.findmy.radar.util.IdLog
import dev.findmy.radar.ui.DeviceListScreen
import dev.findmy.radar.ui.MapScreen
import dev.findmy.radar.ui.RadarScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : ComponentActivity() {

    private lateinit var scanner: FindMyScanner

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ScanController.init(this)
        scanner = ScanController.scanner

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
        if (!ScanController.backgroundScan) scanner.stop()
        super.onDestroy()
    }

    @Composable
    private fun AppContent(scanner: FindMyScanner) {
        var hasPermissions by remember { mutableStateOf(requiredPermissions().all(::isGranted)) }
        var bluetoothOn by remember { mutableStateOf(scanner.bluetoothEnabled) }
        var backgroundScan by remember { mutableStateOf(ScanController.backgroundScan) }
        var target by remember { mutableStateOf<FindMyDevice?>(null) }
        var keyFilterHex by remember {
            mutableStateOf(getPreferences(MODE_PRIVATE).getString(PREF_KEY_FILTER, "") ?: "")
        }
        var deviceNames by remember { mutableStateOf(loadDeviceNames()) }
        var accessories by remember { mutableStateOf(loadAccessories()) }
        // Identities resolved via rolling keys this session: nameKey -> display name.
        var derivedNames by remember { mutableStateOf(mapOf<String, String>()) }
        // nameKey -> last rolling-key match attempt, throttled so EC work stays rare.
        val matchAttempts = remember { mutableMapOf<String, Long>() }
        val idLog = remember { IdLog.decode(getPreferences(MODE_PRIVATE).getString(PREF_ID_LOG, "") ?: "") }
        var idLogSize by remember { mutableStateOf(idLog.entries.size) }

        val permissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) { grants ->
            hasPermissions = grants.values.all { it }
        }
        val notifPermissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { }
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

        // Nicknames override derived names; derived names never rotate away.
        val effectiveNames = derivedNames + deviceNames

        // Rolling-key attribution: for every still-unidentified device, test the
        // observed key prefix against every imported accessory's derivable keys.
        LaunchedEffect(devices, accessories, effectiveNames) {
            if (accessories.isEmpty()) return@LaunchedEffect
            val now = System.currentTimeMillis()
            val unmatched = devices.values.filter {
                effectiveNames[it.nameKey] == null &&
                    now - (matchAttempts[it.nameKey] ?: 0L) >= MATCH_RETRY_MS
            }
            if (unmatched.isEmpty()) return@LaunchedEffect
            unmatched.forEach { matchAttempts[it.nameKey] = now }
            val resolved = withContext(Dispatchers.Default) {
                unmatched.mapNotNull { dev ->
                    val prefix = dev.advKeyBytes.copyOf(6)
                    accessories.firstNotNullOfOrNull { acc ->
                        acc.matchPrefix(prefix, now)?.let { Triple(dev.nameKey, acc, it) }
                    }
                }
            }
            if (resolved.isNotEmpty()) {
                val names = derivedNames.toMutableMap()
                resolved.forEach { (key, acc, match) ->
                    acc.updateAlignment(match.index, now)
                    names[key] = acc.displayName
                }
                derivedNames = names
                saveAccessories(accessories)
            }
        }

        // For each unnamed live device: did a named identity just rotate into it?
        val now = System.currentTimeMillis()
        val liveKeys = devices.values.mapTo(HashSet()) { it.nameKey }
        val suggestions = devices.values
            .filter { effectiveNames[it.nameKey] == null }
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
                        displayName = liveTarget?.let { effectiveNames[it.nameKey] },
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
                        deviceNames = effectiveNames,
                        suggestions = suggestions,
                        existingNames = effectiveNames.values.toSortedSet().toList(),
                        idLogSize = idLogSize,
                        accessories = accessories,
                        backgroundScan = backgroundScan,
                        onBackgroundScanChange = { on ->
                            backgroundScan = on
                            if (on) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                    !isGranted(Manifest.permission.POST_NOTIFICATIONS)
                                ) {
                                    notifPermissionLauncher.launch(
                                        Manifest.permission.POST_NOTIFICATIONS,
                                    )
                                }
                                ContextCompat.startForegroundService(
                                    this@MainActivity,
                                    Intent(this@MainActivity, ScanService::class.java),
                                )
                            } else {
                                startService(
                                    Intent(this@MainActivity, ScanService::class.java)
                                        .setAction(ScanService.ACTION_STOP),
                                )
                            }
                        },
                        onImportAccessory = { json ->
                            runCatching { AccessoryIdentity.fromJson(JSONObject(json)) }
                                .fold(
                                    onSuccess = { acc ->
                                        accessories = accessories
                                            .filterNot {
                                                it.identifier != null &&
                                                    it.identifier == acc.identifier
                                            } + acc
                                        saveAccessories(accessories)
                                        null
                                    },
                                    onFailure = { it.message ?: "Ungültiges JSON" },
                                )
                        },
                        onDeleteAccessory = { acc ->
                            accessories = accessories - acc
                            saveAccessories(accessories)
                        },
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

    /** Imported FindMy accessories whose rolling keys identify tags permanently. */
    private fun loadAccessories(): List<AccessoryIdentity> =
        runCatching {
            val raw = getPreferences(MODE_PRIVATE).getString(PREF_ACCESSORIES, "[]") ?: "[]"
            val arr = JSONArray(raw)
            List(arr.length()) { AccessoryIdentity.fromJson(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList())

    private fun saveAccessories(list: List<AccessoryIdentity>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        getPreferences(MODE_PRIVATE).edit().putString(PREF_ACCESSORIES, arr.toString()).apply()
    }

    companion object {
        private const val PREF_KEY_FILTER = "own_adv_key_hex"
        private const val PREF_NAME_PREFIX = "name:"
        private const val PREF_ID_LOG = "id_log"
        private const val PREF_ACCESSORIES = "accessories_v1"
        private const val UNNAMED_TTL_MS = 24L * 3600_000
        private const val NAMED_TTL_MS = 30L * 24 * 3600_000
        private const val MATCH_RETRY_MS = 60_000L

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
