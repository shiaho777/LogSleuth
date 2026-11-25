package io.github.shiaho777.logsleuth.app.core.logcat

internal class SessionLogMetadata(initialEndpoint: String? = null) {
    var remoteEndpoint: String? = initialEndpoint
        private set
    val remotePackages = HashMap<Int, String>()
    var missedEntries: Long = 0L
        private set
    var sourceChanged: Boolean = false
        private set

    private var readingHeader = true

    fun consume(line: String) {
        if (!line.startsWith('#')) readingHeader = false
        when {
            line.startsWith(GAP) -> {
                val count = line.removePrefix(GAP).toLongOrNull() ?: return
                if (count in 1..(Long.MAX_VALUE - missedEntries)) missedEntries += count
            }
            line == SOURCE_CHANGED -> sourceChanged = true
            readingHeader && line.startsWith(ENDPOINT) -> {
                remoteEndpoint = line.removePrefix(ENDPOINT).take(300)
            }
            readingHeader && line == "# access: REMOTE_ADB" && remoteEndpoint == null -> {
                remoteEndpoint = ""
            }
            remoteEndpoint != null && line.startsWith(PACKAGE) -> {
                val record = line.removePrefix(PACKAGE)
                val uid = record.substringBefore(' ').toIntOrNull() ?: return
                val pkg = record.substringAfter(' ', "")
                if (remotePackages.size < MAX_PACKAGES && PACKAGE_NAME.matches(pkg)) {
                    remotePackages.putIfAbsent(uid, pkg)
                }
            }
        }
    }

    companion object {
        private const val ENDPOINT = "# LogSleuth remote: "
        private const val PACKAGE = "# LogSleuth package: "
        private const val GAP = "# LogSleuth gap: "
        const val SOURCE_CHANGED = "# LogSleuth source changed"
        private const val MAX_PACKAGES = 32_768
        private val PACKAGE_NAME = Regex("[A-Za-z0-9_.]{1,255}")

        fun gap(count: Long): String = "$GAP$count"

        fun packageLine(uid: Int, pkg: String): String = "$PACKAGE$uid $pkg"

        fun sourceHeader(endpoint: String, packages: Map<Int, String>): String = buildString {
            appendLine(ENDPOINT + endpoint)
            for ((uid, pkg) in packages) appendLine(packageLine(uid, pkg))
        }
    }
}
