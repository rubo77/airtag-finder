package dev.findmy.radar.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dev.findmy.radar.R

/** Notifications for device appear/disappear events (own channel, heads-up). */
object Notifier {

    private const val EVENT_CHANNEL = "device-events"

    private fun notificationsAllowed(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED

    fun ensureEventChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(EVENT_CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    EVENT_CHANNEL,
                    "Geräte-Ereignisse",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
            )
        }
    }

    fun notifyDeviceEvent(context: Context, name: String, appeared: Boolean) {
        if (!notificationsAllowed(context)) return
        ensureEventChannel(context)
        val notification = NotificationCompat.Builder(context, EVENT_CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(
                if (appeared) "„$name“ ist wieder da" else "„$name“ außer Reichweite",
            )
            .setContentText(
                if (appeared) {
                    "Gerät wurde gerade wieder gesichtet."
                } else {
                    "Seit über 2 Minuten kein Advertisement mehr."
                },
            )
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java)
            .notify(name.hashCode(), notification)
    }
}
