package dev.findmy.radar.ble

import android.content.Context

/**
 * App-wide scan state shared between MainActivity and ScanService. The scanner
 * lives here (not in the activity) so the foreground service can keep it
 * running when the UI is gone.
 */
object ScanController {

    private var _scanner: FindMyScanner? = null

    val scanner: FindMyScanner
        get() = requireNotNull(_scanner) { "ScanController.init() zuerst aufrufen" }

    val scannerOrNull: FindMyScanner? get() = _scanner

    /** True while the foreground service keeps the scan alive beyond the UI. */
    @Volatile
    var backgroundScan: Boolean = false

    fun init(context: Context) {
        if (_scanner == null) {
            _scanner = FindMyScanner(context.applicationContext)
        }
    }
}
