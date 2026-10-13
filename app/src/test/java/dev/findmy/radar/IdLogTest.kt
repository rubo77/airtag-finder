package dev.findmy.radar

import dev.findmy.radar.util.IdLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IdLogTest {

    @Test
    fun `observe creates and updates entries`() {
        val log = IdLog()
        log.observe("a1b2", "AA:BB:CC:DD:EE:01", 1000)
        log.observe("a1b2", "AA:BB:CC:DD:EE:02", 2000)

        val e = log.entries["a1b2"]!!
        assertEquals(1000, e.firstSeen)
        assertEquals(2000, e.lastSeen)
        assertEquals("AA:BB:CC:DD:EE:02", e.mac)
        assertEquals(1, log.entries.size)
    }

    @Test
    fun `encode decode roundtrip`() {
        val log = IdLog()
        log.observe("a1b2", "AA:BB:CC:DD:EE:01", 1000)
        log.observe("ff00", "AA:BB:CC:DD:EE:02", 1500)

        val back = IdLog.decode(log.encode())
        assertEquals(2, back.entries.size)
        assertEquals(1000, back.entries["a1b2"]!!.firstSeen)
        assertEquals(1500, back.entries["ff00"]!!.lastSeen)
    }

    @Test
    fun `decode of garbage yields empty log`() {
        assertEquals(0, IdLog.decode("not json").entries.size)
    }

    @Test
    fun `suggests recently-lost named device for a new id`() {
        val log = IdLog()
        log.observe("oldkey", "AA:BB:CC:DD:EE:01", 10_000)
        log.observe("oldkey", "AA:BB:CC:DD:EE:01", 100_000) // last seen 100s
        log.observe("newkey", "AA:BB:CC:DD:EE:02", 110_000) // appeared right after

        val names = mapOf("oldkey" to "Mein Schlüssel")
        val suggestion = IdLog.findLikelyPredecessor(
            log, names, newKey = "newkey", liveKeys = setOf("newkey"), now = 120_000,
        )
        assertEquals("Mein Schlüssel", suggestion)
    }

    @Test
    fun `still-visible named device is never suggested as predecessor`() {
        val log = IdLog()
        log.observe("active", "AA:BB:CC:DD:EE:01", 100_000)
        log.observe("gone", "AA:BB:CC:DD:EE:02", 60_000)

        val names = mapOf("active" to "Aktiv", "gone" to "Weg")
        // "active" is live -> only "gone" may be suggested.
        assertEquals(
            "Weg",
            IdLog.findLikelyPredecessor(
                log, names, "new", liveKeys = setOf("active", "new"), now = 100_500,
            ),
        )
    }

    @Test
    fun `no suggestion for unnamed or stale predecessors`() {
        val log = IdLog()
        log.observe("unnamed", "AA:BB:CC:DD:EE:01", 100_000)
        log.observe("named", "AA:BB:CC:DD:EE:02", 1_000) // seen 200 s ago -> outside window

        val names = mapOf("named" to "Alt")
        val none = emptySet<String>()
        assertNull(IdLog.findLikelyPredecessor(log, names, "x", none, now = 201_000))
        assertNull(IdLog.findLikelyPredecessor(log, names, "x", none, now = 500_000))
    }

    @Test
    fun `picks the most recently seen named predecessor`() {
        val log = IdLog()
        log.observe("older", "AA:BB:CC:DD:EE:01", 50_000)
        log.observe("newer", "AA:BB:CC:DD:EE:02", 90_000)

        val names = mapOf("older" to "A", "newer" to "B")
        assertEquals(
            "B",
            IdLog.findLikelyPredecessor(log, names, "fresh", emptySet(), now = 95_000),
        )
    }

    @Test
    fun `prune keeps named longer than unnamed`() {
        val log = IdLog()
        log.observe("named", "AA:BB:CC:DD:EE:01", 0)
        log.observe("unnamed", "AA:BB:CC:DD:EE:02", 0)
        val names = mapOf("named" to "A")

        val day = 86_400_000L
        log.prune(now = 25 * 3_600_000L, names = names, unnamedTtlMs = day, namedTtlMs = 7 * day)

        assertEquals(setOf("named"), log.entries.keys)
    }
}
