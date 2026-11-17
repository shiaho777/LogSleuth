package io.github.shiaho777.logsleuth.app.core.logcat

/** Max length of an abbreviated reverse-domain tag in the meta row. */
const val ABBREV_TAG_MAX = 20

/**
 * The leading segments of a reverse-domain tag ("com.ss.android") carry no
 * scanning value in the list — the app icon already names the owner and the
 * detail sheet keeps the full tag. Keep the last two segments so the
 * meaningful tail stays readable. Only reverse-domain tags qualify; plain
 * tags ("ActivityManager", "chromium") are returned unchanged.
 */
fun abbreviateTag(tag: String): String {
    val segments = tag.split('.')
    if (segments.size < 3 || segments.any { it.isEmpty() }) return tag
    if (segments.first() !in DOMAIN_ROOTS) return tag
    val kept = segments.takeLast(2).joinToString(".")
    return if (kept.length <= ABBREV_TAG_MAX) kept else kept.take(ABBREV_TAG_MAX) + "…"
}

private val DOMAIN_ROOTS = setOf(
    "com", "org", "net", "io", "cn", "dev", "app", "me", "co", "edu", "gov", "ai",
)
