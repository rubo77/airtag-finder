package dev.findmy.radar.crypto

import org.json.JSONObject
import java.time.Instant
import java.time.OffsetDateTime
import java.util.TreeMap

/** Rollover interval of official FindMy accessories (15 minutes). */
private const val INTERVAL_MS = 15L * 60_000

/** Primary index span searched backwards from the newest plausible index. */
private const val SEARCH_BACK = 192

/** Key type within the accessory's rotation schedule. */
enum class KeyType { PRIMARY, SECONDARY }

/** A matched key: which rotation index produced the observed prefix, and which chain. */
data class KeyMatch(val index: Int, val type: KeyType)

/**
 * One imported FindMy accessory (AirTag) with its pairing secrets. Given the
 * master key and the two shared secrets, every advertisement key the tag will
 * ever broadcast can be derived — so a sighted 6-byte key prefix identifies the
 * tag permanently, no matter how often the key rotates.
 *
 * Port of FindMy.py's `FindMyAccessory` / `_AccessoryKeyGenerator`.
 */
class AccessoryIdentity(
    val identifier: String?,
    var name: String?,
    masterKey: ByteArray,
    skn: ByteArray,
    sks: ByteArray,
    val pairedAtMs: Long,
    alignDateMs: Long?,
    alignIndex: Int?,
) {
    private val primaryChain = SkChain(masterKey, skn)
    private val secondaryChain = SkChain(masterKey, sks)

    var alignDateMs: Long = alignDateMs ?: pairedAtMs
        private set
    var alignIndex: Int = alignIndex ?: 0
        private set

    init {
        require(masterKey.size == 28) { "master_key muss 28 Bytes haben" }
        require(skn.size == 32) { "skn muss 32 Bytes haben" }
        require(sks.size == 32) { "sks muss 32 Bytes haben" }
    }

    /** Display label for a matched device. */
    val displayName: String get() = name ?: identifier ?: "AirTag"

    /** Highest primary index the tag could broadcast at [nowMs]. */
    fun maxIndex(nowMs: Long): Int =
        if (nowMs <= alignDateMs) {
            alignIndex
        } else {
            alignIndex + ((nowMs - alignDateMs) / INTERVAL_MS).toInt()
        }

    fun updateAlignment(index: Int, atMs: Long) {
        if (atMs < alignDateMs || index < alignIndex) return
        alignDateMs = atMs
        alignIndex = index
    }

    /**
     * Check whether the observed 6-byte advertisement [prefix] belongs to this
     * accessory around [nowMs]. Searches the primary indices
     * `max(alignIndex, maxIndex - SEARCH_BACK)..maxIndex` (plus `alignIndex`
     * itself — a tag may not have rolled since its last sighting) and the
     * associated secondary keys. Returns the match; the caller should then
     * [updateAlignment] and persist.
     */
    fun matchPrefix(prefix: ByteArray, nowMs: Long): KeyMatch? {
        require(prefix.size == 6)
        val maxInd = maxIndex(nowMs)
        val lo = maxOf(alignIndex, maxInd - SEARCH_BACK)
        val indices = (maxInd downTo lo).toMutableList()
        if (lo > alignIndex) indices += alignIndex

        // Secondary keys change ~daily, so their distinct j-indices over the
        // window are few — compute them once.
        val secPrefixes = HashMap<Int, ByteArray>()
        fun secPrefix(j: Int): ByteArray =
            secPrefixes.getOrPut(j) { secondaryChain.prefixAt(j) }

        for (i in indices) {
            if (primaryChain.prefixAt(i).contentEquals(prefix)) {
                return KeyMatch(i, KeyType.PRIMARY)
            }
            if (secPrefix(i / 96 + 1).contentEquals(prefix) ||
                secPrefix(i / 96 + 2).contentEquals(prefix)
            ) {
                return KeyMatch(i, KeyType.SECONDARY)
            }
        }
        return null
    }

    fun toJson(): JSONObject = JSONObject()
        .put("type", "accessory")
        .put("master_key", primaryChain.masterKeyHex)
        .put("skn", primaryChain.initialSkHex)
        .put("sks", secondaryChain.initialSkHex)
        .put("paired_at", Instant.ofEpochMilli(pairedAtMs).toString())
        .put("name", name ?: JSONObject.NULL)
        .put("identifier", identifier ?: JSONObject.NULL)
        .put("alignment_date", Instant.ofEpochMilli(alignDateMs).toString())
        .put("alignment_index", alignIndex)

    /** One secret-key chain (primary or secondary) with memoized SKs and prefixes. */
    private class SkChain(private val masterKey: ByteArray, initialSk: ByteArray) {
        val masterKeyHex: String = masterKey.toHex()
        val initialSkHex: String = initialSk.toHex()
        private val sks = TreeMap(mapOf(0 to initialSk))
        private val prefixes = HashMap<Int, ByteArray>()

        fun skAt(index: Int): ByteArray {
            var i = sks.floorKey(index) ?: 0
            var sk = sks.getValue(i)
            while (i < index) {
                sk = RollingKey.nextSk(sk)
                sks[++i] = sk
            }
            return sk
        }

        /** First 6 bytes of the advertisement key at [index] — the scanner's nameKey. */
        fun prefixAt(index: Int): ByteArray = prefixes.getOrPut(index) {
            RollingKey.advKey(RollingKey.derivePrivateKey(masterKey, skAt(index))).copyOf(6)
        }
    }

    companion object {
        /** Parse a FindMy.py `FindMyAccessory.to_json` export. */
        fun fromJson(o: JSONObject): AccessoryIdentity = AccessoryIdentity(
            identifier = o.optString("identifier").ifBlank { null },
            name = if (o.isNull("name")) null else o.optString("name").ifBlank { null },
            masterKey = hexToBytes(o.getString("master_key")),
            skn = hexToBytes(o.getString("skn")),
            sks = hexToBytes(o.getString("sks")),
            pairedAtMs = OffsetDateTime.parse(o.getString("paired_at")).toInstant().toEpochMilli(),
            alignDateMs = o.optString("alignment_date")
                .takeIf { it.isNotBlank() && it != "null" }
                ?.let { OffsetDateTime.parse(it).toInstant().toEpochMilli() },
            alignIndex = if (o.isNull("alignment_index")) null else o.getInt("alignment_index"),
        )

        fun hexToBytes(hex: String): ByteArray {
            val clean = hex.trim().lowercase()
            require(clean.length % 2 == 0 && clean.all { it in '0'..'9' || it in 'a'..'f' }) {
                "Ungültiger Hex-String"
            }
            return ByteArray(clean.length / 2) {
                ((clean[it * 2].digitToInt(16) shl 4) or clean[it * 2 + 1].digitToInt(16)).toByte()
            }
        }

        fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
    }
}
