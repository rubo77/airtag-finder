package dev.findmy.radar.util

/** Beep interval: 90 ms on top of the tag, up to 1.5 s at the edge of the radar range. */
fun proximityIntervalMs(distanceM: Double): Long =
    (90 + distanceM * 140).toLong().coerceIn(90, 1500)

/** Coarse distance bucket used to re-trigger the beep loop only on meaningful changes. */
fun distanceBucket(distanceM: Double): Int =
    if (distanceM.isNaN()) -1 else (distanceM * 4).toInt()
