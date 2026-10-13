package dev.findmy.radar.ble

import kotlin.math.pow

/**
 * A device broadcasting Apple's Bluetooth-based Offline Finding protocol.
 *
 * Ported from FindMy.py (findmy/scanner/scanner.py).
 */
sealed class FindMyDevice {
    abstract val mac: String
    abstract val status: Int
    abstract val rssi: Int
    abstract val detectedAt: Long

    /** The advertisement key bytes this device currently broadcasts (full 28 B or partial 6 B). */
    abstract val advKeyBytes: ByteArray

    /** True if this advertisement plausibly comes from a key with the given prefix. */
    fun matchesAdvKey(prefix: ByteArray): Boolean =
        advKeyBytes.size >= prefix.size &&
            advKeyBytes.copyOf(prefix.size).contentEquals(prefix)

    val deviceType: String
        get() = when ((status shr 4) and 0b11) {
            0b00 -> "Apple-Gerät"
            0b01 -> "AirTag"
            0b10 -> "FindMy-Zubehör"
            0b11 -> "AirPods"
            else -> "Unbekannt"
        }

    val batteryLevel: String
        get() = when ((status shr 6) and 0b11) {
            0b00 -> "Voll"
            0b01 -> "Mittel"
            0b10 -> "Niedrig"
            else -> "Sehr niedrig"
        }

    /** Crude distance estimate in meters from RSSI (log-distance path loss, n = 2.5). */
    val estimatedDistanceMeters: Double
        get() = if (rssi >= 0) Double.NaN else 10.0.pow((TX_POWER_1M - rssi) / (10.0 * 2.5))

    val keyHexShort: String
        get() = advKeyBytes.joinToString("") { "%02x".format(it) }.take(16)

    /**
     * Stable identity within one key period: the first 6 key bytes are broadcast in
     * BOTH nearby and separated state, so this survives the state switch. It does
     * NOT survive advertisement-key rotation (~every 15 min on real AirTags).
     */
    val nameKey: String
        get() = advKeyBytes.copyOf(6).joinToString("") { "%02x".format(it) }

    /** AirTag in "separated" state: broadcasts the full 28-byte advertisement key. */
    data class Separated(
        override val mac: String,
        override val status: Int,
        override val rssi: Int,
        override val detectedAt: Long,
        override val advKeyBytes: ByteArray,
        val hint: Int,
    ) : FindMyDevice()

    /** AirTag in "nearby" state (close to its owner device): only 6 key bytes are recoverable. */
    data class Nearby(
        override val mac: String,
        override val status: Int,
        override val rssi: Int,
        override val detectedAt: Long,
        override val advKeyBytes: ByteArray,
    ) : FindMyDevice()

    companion object {
        const val APPLE_COMPANY_ID = 0x004C
        const val OF_MESSAGE_TYPE = 0x12
        const val TX_POWER_1M = -59

        private fun macToBytes(mac: String): ByteArray? =
            mac.split(":", "-")
                .mapNotNull { it.toIntOrNull(16)?.toByte() }
                .toByteArray()
                .takeIf { it.size == 6 }

        /**
         * Parse Apple manufacturer data (payload after company id 0x004C).
         * Returns null if this is not an Offline Finding advertisement.
         */
        fun parse(mac: String, manufacturerData: ByteArray, rssi: Int, now: Long): FindMyDevice? {
            if (manufacturerData.size < 2) return null
            if (manufacturerData[0].toInt() and 0xFF != OF_MESSAGE_TYPE) return null
            val payloadLen = manufacturerData[1].toInt() and 0xFF
            if (manufacturerData.size < 2 + payloadLen) return null
            val payload = manufacturerData.copyOfRange(2, 2 + payloadLen)
            val macBytes = macToBytes(mac) ?: return null

            return when (payloadLen) {
                0x19 -> parseSeparated(mac, macBytes, payload, rssi, now)
                0x02 -> parseNearby(mac, macBytes, payload, rssi, now)
                else -> null
            }
        }

        private fun parseSeparated(
            mac: String, macBytes: ByteArray, p: ByteArray, rssi: Int, now: Long,
        ): Separated {
            val status = p[0].toInt() and 0xFF
            val pubKeyEnd = p.copyOfRange(1, 23)
            val pubKeyMiddle = macBytes.copyOfRange(1, 6)
            val pubKeyStart = (((p[23].toInt() and 0b11) shl 6) or (macBytes[0].toInt() and 0b111111))
                .toByte()
            val pubKey = byteArrayOf(pubKeyStart) + pubKeyMiddle + pubKeyEnd
            val hint = p[24].toInt() and 0xFF
            return Separated(mac, status, rssi, now, pubKey, hint)
        }

        private fun parseNearby(
            mac: String, macBytes: ByteArray, p: ByteArray, rssi: Int, now: Long,
        ): Nearby {
            val status = p[0].toInt() and 0xFF
            val pubKeyStart = (((p[1].toInt() and 0b11) shl 6) or (macBytes[0].toInt() and 0b111111))
                .toByte()
            val partialKey = byteArrayOf(pubKeyStart) + macBytes.copyOfRange(1, 6)
            return Nearby(mac, status, rssi, now, partialKey)
        }
    }
}
