package dev.findmy.radar

import dev.findmy.radar.util.PresenceWatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PresenceWatcherTest {

    @Test
    fun `first sighting seeds silently`() {
        val w = PresenceWatcher()
        assertTrue(w.update(setOf("Schlüssel"), 0).isEmpty())
    }

    @Test
    fun `appearing device fires once`() {
        val w = PresenceWatcher()
        w.update(setOf("Schlüssel"), 0)
        assertEquals(listOf("Rucksack" to true), w.update(setOf("Schlüssel", "Rucksack"), 1000))
        assertTrue(w.update(setOf("Schlüssel", "Rucksack"), 2000).isEmpty())
    }

    @Test
    fun `disappearing device fires after grace only`() {
        val w = PresenceWatcher()
        w.update(setOf("Schlüssel"), 0)
        // Brief flicker below the grace period: no event.
        assertTrue(w.update(emptySet(), 60_000).isEmpty())
        assertTrue(w.update(setOf("Schlüssel"), 90_000).isEmpty())
        // Gone for good.
        assertEquals(listOf("Schlüssel" to false), w.update(emptySet(), 300_000))
    }
}
