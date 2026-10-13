package dev.findmy.radar.util

import org.json.JSONObject

/** One observed device identity (a 6-byte advertisement-key prefix). */
data class IdEntry(
    val key: String,
    var mac: String,
    var firstSeen: Long,
    var lastSeen: Long,
)

/**
 * Persistent log of every advertisement identity ever seen. AirTag keys rotate
 * roughly every 15 minutes — this log is what lets the app guess that a brand-new
 * ID is probably the successor of a named device that just disappeared.
 */
class IdLog {
    val entries = mutableMapOf<String, IdEntry>()

    fun observe(key: String, mac: String, now: Long) {
        val e = entries[key]
        if (e == null) {
            entries[key] = IdEntry(key, mac, now, now)
        } else {
            e.mac = mac
            e.lastSeen = now
        }
    }

    /** Drop stale entries: unnamed after [unnamedTtlMs], named after [namedTtlMs]. */
    fun prune(now: Long, names: Map<String, String>, unnamedTtlMs: Long, namedTtlMs: Long) {
        entries.values.removeIf { e ->
            val ttl = if (names[e.key] != null) namedTtlMs else unnamedTtlMs
            now - e.lastSeen > ttl
        }
    }

    fun encode(): String {
        val root = JSONObject()
        entries.forEach { (key, e) ->
            root.put(
                key,
                JSONObject()
                    .put("m", e.mac)
                    .put("f", e.firstSeen)
                    .put("l", e.lastSeen),
            )
        }
        return root.toString()
    }

    companion object {
        fun decode(json: String): IdLog {
            val log = IdLog()
            runCatching {
                val root = JSONObject(json)
                for (key in root.keys()) {
                    val o = root.getJSONObject(key)
                    log.entries[key] = IdEntry(
                        key = key,
                        mac = o.optString("m"),
                        firstSeen = o.optLong("f"),
                        lastSeen = o.optLong("l"),
                    )
                }
            }
            return log
        }

        /**
         * Best guess which previously-named device a brand-new identity [newKey]
         * continues: the named entry whose [IdEntry.lastSeen] is most recent and
         * no older than [windowMs]. Identities that are still being advertised
         * right now ([liveKeys]) can never be a predecessor — they haven't rotated.
         * Returns that entry's name, or null.
         */
        fun findLikelyPredecessor(
            log: IdLog,
            names: Map<String, String>,
            newKey: String,
            liveKeys: Set<String>,
            now: Long,
            windowMs: Long = 120_000,
        ): String? {
            return log.entries.values
                .asSequence()
                .filter { it.key != newKey && it.key !in liveKeys && names[it.key] != null }
                .filter { now - it.lastSeen in 0..windowMs }
                .maxByOrNull { it.lastSeen }
                ?.let { names[it.key] }
        }
    }
}
