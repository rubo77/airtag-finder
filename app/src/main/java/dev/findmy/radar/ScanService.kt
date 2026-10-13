package dev.findmy.radar

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import dev.findmy.radar.ble.ScanController

/**
 * Keeps the BLE scan running while the screen is off, via a foreground-service
 * notification with a stop action.
 */
class ScanService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopScanning()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        ensureChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        ScanController.init(this)
        ScanController.backgroundScan = true
        ScanController.scanner.start()
        return START_STICKY
    }

    override fun onDestroy() {
        stopScanning()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun stopScanning() {
        ScanController.backgroundScan = false
        ScanController.scannerOrNull?.stop()
    }

    private fun ensureChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "AirTag-Scan",
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        }
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, ScanService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("AirTag-Scan läuft")
            .setContentText("FindMy-Geräte werden im Hintergrund erfasst")
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(0, "Stopp", stop)
            .build()
    }

    companion object {
        const val ACTION_STOP = "dev.findmy.radar.action.STOP_SCAN"
        private const val CHANNEL_ID = "scan"
        private const val NOTIFICATION_ID = 1
    }
}
