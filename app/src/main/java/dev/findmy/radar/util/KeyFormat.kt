package dev.findmy.radar.util

import java.util.Base64

/** Parses a user-entered advertisement key. Accepts hex (with `:`/spaces) or Base64. */
fun parseKeyBytes(input: String): ByteArray? {
    val cleaned = input.trim()
    if (cleaned.isEmpty()) return null

    val hex = cleaned.replace(":", "").replace(" ", "")
    if (hex.length % 2 == 0 && hex.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) {
        return hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    // Strict base64: correct alphabet and padded length, so random junk is rejected.
    val isBase64 = cleaned.length % 4 == 0 &&
        cleaned.all { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it in "+/=" }
    if (!isBase64) return null
    return runCatching { Base64.getDecoder().decode(cleaned) }.getOrNull()
        ?.takeIf { it.isNotEmpty() }
}
