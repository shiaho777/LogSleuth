package io.github.shiaho777.logsleuth.app.core.update

/**
 * A GitHub release, distilled to what the update card needs.
 *
 * @param apkName asset filename (e.g. `LogSleuth-0.3.0.apk`); null when the
 * release ships no APK asset.
 * @param apkUrl  `browser_download_url` of the APK asset.
 * @param apkSize asset byte size as reported by GitHub.
 */
data class ReleaseInfo(
    val tag: String,
    val title: String,
    val notes: String,
    val publishedAt: String,
    val pageUrl: String,
    val apkName: String?,
    val apkUrl: String?,
    val apkSize: Long,
)

/** Three-part semver compare for `vX.Y.Z` tags vs `X.Y.Z` version names. */
object SemVer {

    /**
     * Parses `v0.2.1`, `0.2.1`, `0.2.1-debug`, `1.0.0-beta.2` into
     * `[major, minor, patch]`. Returns null on anything else — callers treat
     * unparseable as "not comparable".
     */
    fun parse(version: String): IntArray? {
        val core = version.trim()
            .removePrefix("v")
            .removePrefix("V")
            .substringBefore('-')
        val parts = core.split('.')
        if (parts.isEmpty() || parts.size > 3) return null
        val nums = parts.map { it.toIntOrNull() ?: return null }
        return IntArray(3) { i -> nums.getOrElse(i) { 0 } }
    }

    /** -1 when a<b, 0 equal, +1 when a>b; null when either side can't parse. */
    fun compare(a: String, b: String): Int? {
        val x = parse(a) ?: return null
        val y = parse(b) ?: return null
        for (i in 0..2) if (x[i] != y[i]) return x[i].compareTo(y[i])
        return 0
    }

    /** True when [remoteTag] is strictly newer than the local [current] version. */
    fun isNewer(remoteTag: String, current: String): Boolean =
        compare(remoteTag, current) == 1
}
