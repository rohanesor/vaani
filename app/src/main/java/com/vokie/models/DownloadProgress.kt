package com.vokie.models

/** Pure transfer accounting shared by downloader and UI; no inference or transport dependency. */
internal fun downloadPercent(downloaded: Long, total: Long): Int =
    if (total <= 0) 0 else ((downloaded.coerceIn(0, total).toDouble() / total) * 100).toInt().coerceIn(0, 99)

internal fun validResumeRange(header: String?, offset: Long, expectedTotal: Long): Boolean {
    val match = Regex("bytes (\\d+)-(\\d+)/(\\d+)").matchEntire(header.orEmpty()) ?: return false
    val (start, end, total) = match.destructured
    val first = start.toLongOrNull() ?: return false
    val last = end.toLongOrNull() ?: return false
    return first == offset && last >= first && last < expectedTotal && total.toLongOrNull() == expectedTotal
}
