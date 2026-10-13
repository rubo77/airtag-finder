package dev.findmy.radar.util

/** Grace period before an absent named device counts as disappeared (BLE ads flicker). */
private const val DISAPPEAR_GRACE_MS = 120_000L

/**
 * Edge-detection for "named device appeared / disappeared". Feeds on the set of
 * display names currently visible; emits an event only on transitions:
 * appear -> present again after grace, disappear -> absent for [DISAPPEAR_GRACE_MS].
 */
class PresenceWatcher {

    private val lastSeen = mutableMapOf<String, Long>()
    private var initialized = false

    /**
     * Update with the names visible right now. Returns (name, appeared) events.
     * The first non-empty evaluation only seeds state — no notification storm
     * for devices that were already around when the watcher started.
     */
    fun update(present: Set<String>, now: Long): List<Pair<String, Boolean>> {
        val events = mutableListOf<Pair<String, Boolean>>()
        for (name in present) {
            if (name !in lastSeen && initialized) events += name to true
            lastSeen[name] = now
        }
        val it = lastSeen.entries.iterator()
        while (it.hasNext()) {
            val (name, seen) = it.next()
            if (name !in present && now - seen > DISAPPEAR_GRACE_MS) {
                it.remove()
                events += name to false
            }
        }
        if (present.isNotEmpty()) initialized = true
        return events
    }
}
