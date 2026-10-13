package dev.findmy.radar

import dev.findmy.radar.util.distanceBucket
import dev.findmy.radar.util.proximityIntervalMs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProximityMathTest {

    @Test
    fun `beep interval is fast when close and slow when far`() {
        assertEquals(90L, proximityIntervalMs(0.0))
        assertTrue(proximityIntervalMs(0.5) < proximityIntervalMs(5.0))
        assertEquals(1500L, proximityIntervalMs(100.0))
        assertTrue(proximityIntervalMs(9.9) <= 1500L)
    }

    @Test
    fun `distance bucket groups similar distances`() {
        assertEquals(-1, distanceBucket(Double.NaN))
        assertEquals(0, distanceBucket(0.1))
        assertEquals(distanceBucket(1.0), distanceBucket(1.1))
        assertTrue(distanceBucket(1.0) != distanceBucket(5.0))
    }
}
