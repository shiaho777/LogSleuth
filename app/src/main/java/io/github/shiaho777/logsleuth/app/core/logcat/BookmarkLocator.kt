package io.github.shiaho777.logsleuth.app.core.logcat

internal fun nearestTimestampIndex(size: Int, target: Long, timestampAt: (Int) -> Long): Int? {
    if (target <= 0L) return null
    var closest: Int? = null
    var closestTime = 0L
    var distance = Long.MAX_VALUE
    for (index in 0 until size) {
        val timestamp = timestampAt(index)
        if (timestamp <= 0L) continue
        if (timestamp == target) return index
        val delta = if (timestamp >= target) timestamp - target else target - timestamp
        if (delta < distance || (delta == distance && closestTime < target && timestamp > target)) {
            closest = index
            closestTime = timestamp
            distance = delta
        }
    }
    return closest
}
