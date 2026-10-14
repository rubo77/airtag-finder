package dev.findmy.radar

import dev.findmy.radar.util.fetchLocations
import dev.findmy.radar.util.parseLocations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.net.ServerSocket
import kotlin.concurrent.thread

class LocationClientTest {

    private val sampleJson = """
        [{"name":"Schluessel","latitude":52.5200,"longitude":13.4050,
          "timestamp":"2026-10-13T12:34:56+02:00","confidence":3,
          "horizontal_accuracy":17,"status":16,"battery":"Full"}]
    """.trimIndent()

    @Test
    fun `parses backend location json`() {
        val list = parseLocations(sampleJson)
        assertEquals(1, list.size)
        val loc = list[0]
        assertEquals("Schluessel", loc.name)
        assertEquals(52.52, loc.latitude, 0.0001)
        assertEquals(13.405, loc.longitude, 0.0001)
        assertEquals(3, loc.confidence)
        assertEquals(17, loc.horizontalAccuracy)
        assertEquals("Full", loc.battery)
    }

    @Test
    fun `parses empty list`() {
        assertEquals(emptyList<Any>(), parseLocations("[]"))
    }

    @Test
    fun `fetch roundtrip against local http server`() {
        val (port, server) = serveOnce(200, sampleJson)
        try {
            val list = fetchLocations("http://localhost:$port")
            assertEquals(1, list.size)
            assertEquals("Schluessel", list[0].name)
        } finally {
            server.join(5_000)
        }
    }

    @Test
    fun `fetch throws on non-200`() {
        val (port, server) = serveOnce(503, "")
        try {
            assertThrows(Exception::class.java) {
                fetchLocations("http://localhost:$port")
            }
        } finally {
            server.join(5_000)
        }
    }

    /** One-shot HTTP responder on a random port, for tests only. */
    private fun serveOnce(status: Int, body: String): Pair<Int, Thread> {
        val socket = ServerSocket(0)
        val t = thread {
            socket.accept().use { conn ->
                // Drain request headers (until empty line).
                val reader = conn.getInputStream().bufferedReader()
                while (reader.readLine()?.isNotEmpty() == true) Unit
                val payload = body.toByteArray()
                val head = "HTTP/1.1 $status X\r\nContent-Type: application/json\r\n" +
                    "Content-Length: ${payload.size}\r\nConnection: close\r\n\r\n"
                conn.getOutputStream().apply {
                    write(head.toByteArray())
                    write(payload)
                    flush()
                }
            }
            socket.close()
        }
        return socket.localPort to t
    }
}
