package dev.findmy.radar.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.findmy.radar.util.LocationEntry
import dev.findmy.radar.util.fetchHistory
import dev.findmy.radar.util.fetchLocations
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import kotlin.math.abs
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private const val REFRESH_MS = 60_000L

@Composable
fun MapScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("findmy_prefs", android.content.Context.MODE_PRIVATE) }

    var backendUrl by remember { mutableStateOf(prefs.getString("backend_url", "") ?: "") }
    var locations by remember { mutableStateOf<List<LocationEntry>>(emptyList()) }
    var history by remember { mutableStateOf<List<LocationEntry>>(emptyList()) }
    var statusText by remember { mutableStateOf("Backend-URL eintragen, z. B. http://192.168.1.10:8000") }
    var refreshTick by remember { mutableStateOf(0) }

    // Poll the backend periodically once a URL is configured.
    LaunchedEffect(backendUrl, refreshTick) {
        if (backendUrl.isBlank()) return@LaunchedEffect
        while (true) {
            statusText = "Lade Positionen…"
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    fetchLocations(backendUrl) to fetchHistory(backendUrl)
                }
            }
            result.onSuccess { (locs, hist) ->
                locations = locs
                history = hist
                statusText = if (locs.isEmpty()) {
                    "Keine Positionsreports vorhanden"
                } else {
                    "Aktualisiert · ${locs.size} Tag(s) · ${hist.size} Trail-Punkte"
                }
            }.onFailure {
                statusText = "Fehler: ${it.message}"
            }
            delay(REFRESH_MS)
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = backendUrl,
                onValueChange = { backendUrl = it },
                label = { Text("Backend-URL") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                prefs.edit().putString("backend_url", backendUrl).apply()
                refreshTick++
            }) { Text("OK") }
        }
        Text(statusText, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        Spacer(Modifier.height(8.dp))

        OsmMap(locations, history, modifier = Modifier.weight(1f).fillMaxWidth())

        locations.forEach { loc ->
            Text(
                "${loc.name}: ${formatTimestamp(loc.timestamp)} · ±${loc.horizontalAccuracy} m" +
                    " · Akku ${loc.battery}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/** Stable per-tag trail colors, picked from the palette by name hash. */
private val TRAIL_COLORS = intArrayOf(
    0xFF4FC3F7.toInt(), 0xFF69F0AE.toInt(), 0xFFFFD54F.toInt(),
    0xFFFF8A65.toInt(), 0xFFBA68C8.toInt(), 0xFFFFF176.toInt(),
)

private fun trailColor(name: String) = TRAIL_COLORS[abs(name.hashCode()) % TRAIL_COLORS.size]

@Composable
private fun OsmMap(
    locations: List<LocationEntry>,
    history: List<LocationEntry>,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        factory = { ctx ->
            val p = ctx.getSharedPreferences("osmdroid", android.content.Context.MODE_PRIVATE)
            Configuration.getInstance().load(ctx, p)
            Configuration.getInstance().userAgentValue = ctx.packageName
            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                controller.setZoom(15.0)
                controller.setCenter(GeoPoint(52.52, 13.405)) // Berlin default
            }
        },
        update = { map ->
            map.overlays.removeIf { it is Marker || it is Polyline }

            // Position trail per tag, oldest -> newest.
            history.groupBy { it.name }.forEach { (name, entries) ->
                if (entries.size >= 2) {
                    map.overlays.add(
                        Polyline(map).apply {
                            setPoints(entries.map { GeoPoint(it.latitude, it.longitude) })
                            outlinePaint.color = trailColor(name)
                            outlinePaint.strokeWidth = 6f
                        },
                    )
                }
            }

            locations.forEach { loc ->
                map.overlays.add(
                    Marker(map).apply {
                        position = GeoPoint(loc.latitude, loc.longitude)
                        title = "${loc.name} · ${formatTimestamp(loc.timestamp)}"
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    },
                )
            }
            locations.firstOrNull()?.let {
                map.controller.setCenter(GeoPoint(it.latitude, it.longitude))
            }
            map.invalidate()
        },
        modifier = modifier,
    )
}

private fun formatTimestamp(iso: String): String =
    runCatching {
        val instant = java.time.OffsetDateTime.parse(iso).toInstant()
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
            .withZone(ZoneId.systemDefault())
            .format(instant)
    }.getOrElse { iso }
