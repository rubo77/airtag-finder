package dev.findmy.radar.util

import org.json.JSONArray
import org.json.JSONObject
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

/** Parses the report list found under [key] in a backend response envelope. */
fun parseEntries(json: String, key: String): List<LocationEntry> {
    val arr: JSONArray = JSONObject(json).getJSONArray(key)
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

/** Parses GET /api/locations: latest position per tag. */
fun parseLocations(json: String): List<LocationEntry> = parseEntries(json, "locations")

/** Parses GET /api/history: full report trail per tag, oldest first. */
fun parseHistory(json: String): List<LocationEntry> = parseEntries(json, "history")

private fun httpGet(url: String, timeoutMs: Int): String {
    val conn = URL(url).openConnection() as HttpURLConnection
    try {
        conn.connectTimeout = timeoutMs
        conn.readTimeout = timeoutMs
        conn.requestMethod = "GET"
        if (conn.responseCode != 200) {
            throw LocationFetchException("HTTP ${conn.responseCode}")
        }
        return conn.inputStream.bufferedReader().readText()
    } finally {
        conn.disconnect()
    }
}

/** Fetches GET {baseUrl}/api/locations and parses the response. Throws on errors. */
fun fetchLocations(baseUrl: String, timeoutMs: Int = 10_000): List<LocationEntry> =
    parseLocations(httpGet(baseUrl.trimEnd('/') + "/api/locations", timeoutMs))

/** Fetches GET {baseUrl}/api/history and parses the response. Throws on errors. */
fun fetchHistory(baseUrl: String, timeoutMs: Int = 10_000): List<LocationEntry> =
    parseHistory(httpGet(baseUrl.trimEnd('/') + "/api/history", timeoutMs))

class LocationFetchException(message: String) : Exception(message)
