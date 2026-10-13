package dev.findmy.radar.ui

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.findmy.radar.ble.FindMyDevice
import dev.findmy.radar.util.distanceBucket
import dev.findmy.radar.util.proximityIntervalMs
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Max distance shown on the outermost ring. */
private const val RADAR_RANGE_M = 10f
private const val RING_COUNT = 4

@Composable
fun RadarScreen(
    device: FindMyDevice?,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val distance = device?.estimatedDistanceMeters ?: Double.NaN

    // Beep faster the closer the tag is; vibrate when very close.
    LaunchedEffect(device?.mac, distanceBucket(distance)) {
        val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 80)
        val vibrator = context.getSystemService(Vibrator::class.java)
        try {
            while (true) {
                if (!distance.isNaN()) {
                    tone.startTone(ToneGenerator.TONE_PROP_BEEP, 50)
                    if (distance < 1.0) {
                        vibrator?.vibrate(
                            VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE),
                        )
                    }
                    delay(proximityIntervalMs(distance))
                } else {
                    delay(500)
                }
            }
        } finally {
            tone.release()
        }
    }
    DisposableEffect(Unit) { onDispose { } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            device?.let { "${it.deviceType} · ${it.mac}" } ?: "Signal verloren",
            style = MaterialTheme.typography.titleMedium,
            fontFamily = FontFamily.Monospace,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (distance.isNaN()) "–" else String.format(Locale.GERMANY, "%.1f m", distance),
            style = MaterialTheme.typography.displayLarge,
            color = proximityColor(distance),
        )
        Text(
            device?.let { "${it.rssi} dBm · Batterie ${it.batteryLevel}" } ?: "Suche…",
            color = Color.Gray,
        )

        Spacer(Modifier.height(24.dp))
        RadarCanvas(
            distanceM = if (distance.isNaN()) null else distance.toFloat(),
            angleSeed = device?.mac?.hashCode() ?: 0,
            modifier = Modifier.size(300.dp),
        )
        Spacer(Modifier.weight(1f))
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Zurück zur Liste")
        }
    }
}

@Composable
private fun RadarCanvas(distanceM: Float?, angleSeed: Int, modifier: Modifier = Modifier) {
    val sweep by rememberInfiniteTransition(label = "sweep").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart),
        label = "sweep",
    )
    val ringColor = Color(0xFF4FC3F7)
    val blipColor = Color(0xFF69F0AE)

    Canvas(modifier = modifier) {
        val radius = min(size.width, size.height) / 2f
        val center = Offset(size.width / 2f, size.height / 2f)

        for (i in 1..RING_COUNT) {
            drawCircle(
                color = ringColor,
                radius = radius * i / RING_COUNT,
                center = center,
                style = Stroke(width = 2f, alpha = 0.8f - i * 0.12f),
            )
        }

        // Rotating sweep line.
        val sweepRad = Math.toRadians(sweep.toDouble())
        drawLine(
            color = ringColor.copy(alpha = 0.6f),
            start = center,
            end = Offset(
                center.x + radius * cos(sweepRad).toFloat(),
                center.y + radius * sin(sweepRad).toFloat(),
            ),
            strokeWidth = 3f,
        )

        // Target blip: radius ∝ distance, stable angle per device.
        if (distanceM != null) {
            val frac = (distanceM / RADAR_RANGE_M).coerceIn(0.04f, 1f)
            val angle = (angleSeed % 360 + 360) % 360 * Math.PI / 180.0
            val pos = Offset(
                center.x + radius * frac * cos(angle).toFloat(),
                center.y + radius * frac * sin(angle).toFloat(),
            )
            drawCircle(color = blipColor, radius = 10f, center = pos)
            drawCircle(color = blipColor.copy(alpha = 0.3f), radius = 22f, center = pos)
        }
    }
}

private fun proximityColor(distanceM: Double): Color = when {
    distanceM.isNaN() -> Color.Gray
    distanceM < 1.0 -> Color(0xFF69F0AE)
    distanceM < 3.0 -> Color(0xFFFFD54F)
    else -> Color(0xFFFF8A65)
}
