package dev.findmy.radar

import dev.findmy.radar.crypto.AccessoryIdentity
import dev.findmy.radar.crypto.KeyType
import dev.findmy.radar.crypto.RollingKey
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.OffsetDateTime

/**
 * Vectors generated with FindMy.py 0.10.2 (random seed 1337): master key, SKN,
 * SKS, paired_at, the first SK-chain entries and the advertisement keys that
 * `FindMyAccessory.keys_at(index)` produces. The Kotlin port must reproduce
 * them bit-for-bit.
 */
class RollingKeyTest {

    companion object {
        private const val MASTER = "030d259e7be3efeca5178488cdb1bab5ff3ca85db0ad2b92f228f695"
        private const val SKN = "b8755dbb5f7b6a2a8b7b73c66e280ad36dae3155757a3a62e51887a2fd1024ca"
        private const val SKS = "5fd31e5cbb44f6eb539fe2fec60fc14e6db53c646fe703fe757376b258577734"
        private const val PAIRED_AT = "2024-03-01T10:00:00+00:00"
        private val PAIRED_MS = OffsetDateTime.parse(PAIRED_AT).toInstant().toEpochMilli()

        private val SK_CHAIN = listOf(
            "b8755dbb5f7b6a2a8b7b73c66e280ad36dae3155757a3a62e51887a2fd1024ca",
            "6dca7d98cd66fe7f10ea41c75536797165836f47dff4c9e9aff045710dce4e3c",
            "34c69dce99bdc80983d36cfedccea4cd325af6c84534be4780a46747c75c120a",
            "f268e1dd8984c3e55ab19deaba572306e3ff7073e0b30a72b08a8e9ccaabc3ab",
        )

        /** primary index -> 28-byte advertisement key (hex). */
        private val PRIMARY_KEYS = mapOf(
            0 to "980fa546930ed7dc26fcfc79e0090d43a128a6f9a67a4ed532e22c79",
            1 to "edb3bdafe7d6dcf1b3d0ebb3939f3079bcc32fdf6dbb006a1a059c32",
            2 to "299e949f3e91b30e6816721ddea935b9619cb05fca5af740ee9d0f6b",
            5 to "4f66a3041b2a0ce43a41335aabaeea6b0b41bf03bb3f39dbe11a3e21",
            97 to "877ee9995cf8b0e4dec5f262e6234a74abb35f336ea377e33caf67f1",
            100 to "b7107f76584446f26992f5d8871af1211c67cf032190ccf773200916",
        )

        /** secondary chain index j -> 28-byte advertisement key (hex). */
        private val SECONDARY_KEYS = mapOf(
            1 to "99e4f04db0f5a84aaf55779fa30a57d4856b134896955a547e49b107",
            2 to "84fac4e2720b5c609635e11d34be412e09df433c02a80ba284c9f7d8",
            3 to "1477cc46c212845247b7ce41cba312212f9e319d6a2bdffe6e45593f",
        )
    }

    private fun hex(s: String) = AccessoryIdentity.hexToBytes(s)

    private fun skAt(initialSk: ByteArray, index: Int): ByteArray {
        var sk = initialSk
        repeat(index) { sk = RollingKey.nextSk(sk) }
        return sk
    }

    private fun advKeyAt(initialSk: ByteArray, index: Int): ByteArray =
        RollingKey.advKey(RollingKey.derivePrivateKey(hex(MASTER), skAt(initialSk, index)))

    @Test
    fun `sk chain matches FindMy_py`() {
        var sk = hex(SKN)
        assertArrayEquals(hex(SK_CHAIN[0]), sk)
        for (i in 1 until SK_CHAIN.size) {
            sk = RollingKey.nextSk(sk)
            assertArrayEquals("sk($i)", hex(SK_CHAIN[i]), sk)
        }
    }

    @Test
    fun `primary advertisement keys match FindMy_py`() {
        for ((index, expected) in PRIMARY_KEYS) {
            assertArrayEquals("primary($index)", hex(expected), advKeyAt(hex(SKN), index))
        }
    }

    @Test
    fun `secondary advertisement keys match FindMy_py`() {
        for ((j, expected) in SECONDARY_KEYS) {
            assertArrayEquals("secondary($j)", hex(expected), advKeyAt(hex(SKS), j))
        }
    }

    private fun accessory(): AccessoryIdentity = AccessoryIdentity.fromJson(
        JSONObject(
            """{
                "type": "accessory",
                "master_key": "$MASTER",
                "skn": "$SKN",
                "sks": "$SKS",
                "paired_at": "$PAIRED_AT",
                "name": "TestTag",
                "identifier": "tag-1",
                "alignment_date": null,
                "alignment_index": null
            }""",
        ),
    )

    @Test
    fun `matches primary prefix in-window and aligns`() {
        val acc = accessory()
        val now = PAIRED_MS + 5 * 15 * 60_000L
        val match = acc.matchPrefix(hex(PRIMARY_KEYS.getValue(5)).copyOf(6), now)
        assertEquals(KeyType.PRIMARY, match?.type)
        assertEquals(5, match?.index)
    }

    @Test
    fun `matches secondary prefix`() {
        val acc = accessory()
        // index 97 -> secondary candidates j=2 and j=3
        val now = PAIRED_MS + 97 * 15 * 60_000L
        val match = acc.matchPrefix(hex(SECONDARY_KEYS.getValue(3)).copyOf(6), now)
        assertEquals(KeyType.SECONDARY, match?.type)
        assertEquals(97, match?.index)
    }

    @Test
    fun `no match for foreign prefix`() {
        val acc = accessory()
        val now = PAIRED_MS + 10 * 15 * 60_000L
        assertNull(acc.matchPrefix(ByteArray(6) { 0x42 }, now))
    }

    @Test
    fun `match beyond search back falls out of window`() {
        val acc = accessory()
        // index 0 is 300 intervals behind "now" — outside SEARCH_BACK, and the
        // alignIndex (0) extra check still finds it because it IS the alignment.
        val now = PAIRED_MS + 300 * 15 * 60_000L
        val m0 = acc.matchPrefix(hex(PRIMARY_KEYS.getValue(0)).copyOf(6), now)
        assertEquals(0, m0?.index)
        // But index 5 is neither the alignment nor inside the window.
        assertNull(acc.matchPrefix(hex(PRIMARY_KEYS.getValue(5)).copyOf(6), now))
    }

    @Test
    fun `json round trip preserves secrets and alignment`() {
        val acc = accessory()
        acc.updateAlignment(42, PAIRED_MS + 42 * 15 * 60_000L)
        val restored = AccessoryIdentity.fromJson(acc.toJson())
        assertEquals("TestTag", restored.name)
        assertEquals("tag-1", restored.identifier)
        assertEquals(PAIRED_MS, restored.pairedAtMs)
        assertEquals(42, restored.alignIndex)
        // same identity -> still derives the same key
        val now = PAIRED_MS + 100 * 15 * 60_000L
        assertEquals(
            KeyType.PRIMARY,
            restored.matchPrefix(hex(PRIMARY_KEYS.getValue(100)).copyOf(6), now)?.type,
        )
    }
}
