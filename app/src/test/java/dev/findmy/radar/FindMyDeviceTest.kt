package dev.findmy.radar

import dev.findmy.radar.ble.FindMyDevice
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FindMyDeviceTest {

    private fun ofAdvertisement(payload: ByteArray): ByteArray =
        byteArrayOf(0x12, payload.size.toByte()) + payload

    @Test
    fun `parses separated advertisement and reconstructs 28-byte key`() {
        // pubkey bytes 0x01..0x1C; pub[0]=0x01 -> top2=0b00, low6=0x01.
        val pub = (1..28).map { it.toByte() }.toByteArray()
        val mac = byteArrayOf(0xC1.toByte(), 0x02, 0x03, 0x04, 0x05, 0x06)
        val macStr = "C1:02:03:04:05:06"
        val status = 0x90.toByte() // battery=Niedrig(0b10), type=AirTag(0b01)
        val payload = byteArrayOf(status) +
            pub.copyOfRange(6, 28) +
            byteArrayOf(0x00, 0x22) // top2 bits byte, hint
        assertEquals(25, payload.size)

        val dev = FindMyDevice.parse(macStr, ofAdvertisement(payload), rssi = -55, now = 1234L)

        assertTrue(dev is FindMyDevice.Separated)
        dev as FindMyDevice.Separated
        assertArrayEquals(pub, dev.advKeyBytes)
        assertEquals(macStr, dev.mac)
        assertEquals(0x90, dev.status)
        assertEquals(0x22, dev.hint)
        assertEquals("AirTag", dev.deviceType)
        assertEquals("Niedrig", dev.batteryLevel)
        assertEquals(-55, dev.rssi)
        assertEquals(1234L, dev.detectedAt)
    }

    @Test
    fun `separated key keeps top 2 bits of first byte`() {
        // pub[0]=0xF1 (0b11110001) -> payload bit-byte=0b11, mac[0] low6=0x31.
        val pub = ByteArray(28) { it.toByte() }.also { it[0] = 0xF1.toByte() }
        val mac = byteArrayOf(0xF1.toByte(), 1, 2, 3, 4, 5)
        val payload = byteArrayOf(0x10) + pub.copyOfRange(6, 28) + byteArrayOf(0x03, 0x00)

        val dev = FindMyDevice.parse(
            "F1:01:02:03:04:05", ofAdvertisement(payload), rssi = -80, now = 0,
        ) as FindMyDevice.Separated

        assertArrayEquals(pub, dev.advKeyBytes)
        assertEquals(mac[0], dev.mac.split(":")[0].toInt(16).toByte())
    }

    @Test
    fun `parses nearby advertisement and reconstructs 6-byte partial key`() {
        // pstart=0xF1: top2=0b11 -> payload[1]=3; low6=0x31 -> mac[0]=0xF1.
        val macStr = "F1:11:22:33:44:55"
        val payload = byteArrayOf(0x40, 0x03) // status battery=Mittel,type=Apple-Geraet

        val dev = FindMyDevice.parse(macStr, ofAdvertisement(payload), rssi = -70, now = 5L)

        assertTrue(dev is FindMyDevice.Nearby)
        dev as FindMyDevice.Nearby
        assertArrayEquals(
            byteArrayOf(0xF1.toByte(), 0x11, 0x22, 0x33, 0x44, 0x55),
            dev.advKeyBytes,
        )
        assertEquals("Mittel", dev.batteryLevel)
        assertEquals("Apple-Gerät", dev.deviceType)
    }

    @Test
    fun `rejects non-offline-finding advertisements`() {
        assertNull(FindMyDevice.parse("AA:BB:CC:DD:EE:FF", byteArrayOf(0x10, 0x05, 1, 2, 3, 4, 5), -60, 0))
        assertNull(FindMyDevice.parse("AA:BB:CC:DD:EE:FF", byteArrayOf(0x12), -60, 0))
        assertNull(FindMyDevice.parse("AA:BB:CC:DD:EE:FF", byteArrayOf(0x12, 0x07, 1, 2, 3), -60, 0))
        assertNull(FindMyDevice.parse("not-a-mac", byteArrayOf(0x12, 0x02, 0x00, 0x00), -60, 0))
    }

    @Test
    fun `matchesAdvKey compares prefixes`() {
        val pub = (1..28).map { it.toByte() }.toByteArray()
        val payload = byteArrayOf(0x10) + pub.copyOfRange(6, 28) + byteArrayOf(0x00, 0x00)
        val dev = FindMyDevice.parse(
            "C1:02:03:04:05:06", ofAdvertisement(payload), rssi = -50, now = 0,
        )!!

        assertTrue(dev.matchesAdvKey(pub))
        assertTrue(dev.matchesAdvKey(pub.copyOf(6)))
        assertTrue(!dev.matchesAdvKey(byteArrayOf(9, 9, 9)))
    }

    @Test
    fun `distance estimate grows with weaker rssi`() {
        val payload = byteArrayOf(0x00, 0x00)
        val near = FindMyDevice.parse("C0:01:02:03:04:05", ofAdvertisement(payload), -45, 0)!!
        val far = FindMyDevice.parse("C0:01:02:03:04:05", ofAdvertisement(payload), -90, 0)!!
        assertTrue(near.estimatedDistanceMeters < far.estimatedDistanceMeters)
        assertTrue(near.estimatedDistanceMeters < 1.0)
        assertTrue(far.estimatedDistanceMeters > 10.0)
    }
}
