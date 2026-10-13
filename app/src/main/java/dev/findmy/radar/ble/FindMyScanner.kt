package dev.findmy.radar.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Scans for BLE advertisements of Apple Offline Finding devices (AirTags etc.).
 */
class FindMyScanner(context: Context) {

    private val adapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private val _devices = MutableStateFlow<Map<String, FindMyDevice>>(emptyMap())

    /** Devices keyed by current BLE MAC. Values are the most recent advertisement seen. */
    val devices: StateFlow<Map<String, FindMyDevice>> = _devices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    val bluetoothEnabled: Boolean get() = adapter?.isEnabled == true

    private val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val data = result.scanRecord
                ?.getManufacturerSpecificData(FindMyDevice.APPLE_COMPANY_ID)
                ?: return
            val device = FindMyDevice.parse(
                mac = result.device.address,
                manufacturerData = data,
                rssi = result.rssi,
                now = System.currentTimeMillis(),
            ) ?: return
            _devices.value = _devices.value + (device.mac to device)
        }

        override fun onBatchScanResults(results: List<ScanResult>) {
            results.forEach { onScanResult(0, it) }
        }
    }

    /** Start scanning. Caller must hold the required Bluetooth permissions. */
    @SuppressLint("MissingPermission")
    fun start() {
        val scanner = adapter?.bluetoothLeScanner ?: return
        if (_isScanning.value) return

        val filter = ScanFilter.Builder()
            .setManufacturerData(FindMyDevice.APPLE_COMPANY_ID, byteArrayOf(OF_TYPE_BYTE))
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .build()

        scanner.startScan(listOf(filter), settings, callback)
        _isScanning.value = true
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        if (!_isScanning.value) return
        runCatching { adapter?.bluetoothLeScanner?.stopScan(callback) }
        _isScanning.value = false
    }

    /** Drop devices not seen for [maxAgeMs]; call periodically to keep the list fresh. */
    fun prune(maxAgeMs: Long = 15_000) {
        val cutoff = System.currentTimeMillis() - maxAgeMs
        _devices.value = _devices.value.filterValues { it.detectedAt >= cutoff }
    }

    fun clear() {
        _devices.value = emptyMap()
    }

    private companion object {
        const val OF_TYPE_BYTE: Byte = 0x12
    }
}
