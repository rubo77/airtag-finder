package dev.findmy.radar

import dev.findmy.radar.util.parseKeyBytes
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KeyFormatTest {

    @Test
    fun `parses plain hex`() {
        assertArrayEquals(byteArrayOf(0x01, 0xAB.toByte(), 0xFF.toByte()), parseKeyBytes("01abff"))
    }

    @Test
    fun `parses hex with separators`() {
        assertArrayEquals(byteArrayOf(0x01, 0x02), parseKeyBytes("01:02"))
        assertArrayEquals(byteArrayOf(0x01, 0x02), parseKeyBytes(" 01 02 "))
    }

    @Test
    fun `parses base64`() {
        assertArrayEquals(byteArrayOf(0x01, 0x02), parseKeyBytes("AQI="))
    }

    @Test
    fun `rejects invalid input`() {
        assertNull(parseKeyBytes(""))
        assertNull(parseKeyBytes("   "))
        assertNull(parseKeyBytes("zz"))
        assertNull(parseKeyBytes("abc")) // odd-length hex, invalid base64
        assertNull(parseKeyBytes("!!!"))
    }
}
