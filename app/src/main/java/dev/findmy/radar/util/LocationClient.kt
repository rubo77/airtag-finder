package dev.findmy.radar.util

import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

/** One location report entry as served by the FindMy.py backend. */
data class LocationEntry(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val timestamp: String,
    val confidence: Int,
    val horizontalAccuracy: Int,
    val battery: String,
)

/** Parses the JSON array returned by GET /api/locations. */
fun parseLocations(json: String): List<LocationEntry> {
    val arr = JSONArray(json)
    return List(arr.length()) { i ->
        val o = arr.getJSONObject(i)
        LocationEntry(
            name = o.optString("name", "AirTag"),
            latitude = o.getDouble("latitude"),
            longitude = o.getDouble("longitude"),
            timestamp = o.optString("timestamp", ""),
            confidence = o.optInt("confidence", 0),
            horizontalAccuracy = o.optInt("horizontal_accuracy", 0),
            battery = o.optString("battery", ""),
        )
    }
}

/** Fetches GET {baseUrl}/api/locations and parses the response. Throws on errors. */
fun fetchLocations(baseUrl: String, timeoutMs: Int = 10_000): List<LocationEntry> {
    val url = URL(baseUrl.trimEnd('/') + "/api/locations")
    val conn = url.openConnection() as HttpURLConnection
    try {
        conn.connectTimeout = timeoutMs
        conn.readTimeout = timeoutMs
        conn.requestMethod = "GET"
        if (conn.responseCode != 200) {
            throw LocationFetchException("HTTP ${conn.responseCode}")
        }
        return parseLocations(conn.inputStream.bufferedReader().readText())
    } finally {
        conn.disconnect()
    }
}

class LocationFetchException(message: String) : Exception(message)
