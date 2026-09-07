package io.github.shiaho777.logsleuth.sdk

/**
 * Configuration for [Sleuth.init].
 *
 * @param tagFilter only logcat lines whose tag contains this string are
 *   recorded (plus all ERROR lines). null = record everything from the app.
 * @param maxFiles ring buffer: number of log files to keep.
 * @param maxFileBytes ring buffer: max size per file before rotation.
 * @param captureLogcat set false to only capture crashes/ANRs.
 * @param watchAnr enable the main-thread ANR watchdog.
 * @param onCrashInvokedOnNextStart when true, [Sleuth.onCrash] fires on the
 *   init *after* a crash (default). When false it fires immediately, before
 *   the process dies — keep that callback tiny and async-safe.
 */
class SleuthConfig private constructor(
    val tagFilter: String?,
    val maxFiles: Int,
    val maxFileBytes: Long,
    val captureLogcat: Boolean,
    val watchAnr: Boolean,
    val onCrashInvokedOnNextStart: Boolean,
    val redactions: List<Regex>,
) {
    class Builder {
        private var tagFilter: String? = null
        private var maxFiles: Int = 3
        private var maxFileBytes: Long = 2L * 1024 * 1024
        private var captureLogcat: Boolean = true
        private var watchAnr: Boolean = true
        private var onCrashInvokedOnNextStart: Boolean = true
        private val redactions = mutableListOf<Regex>()

        fun tagFilter(value: String?) = apply { tagFilter = value }
        fun maxFiles(value: Int) = apply { maxFiles = value.coerceIn(1, 20) }
        fun maxFileBytes(value: Long) = apply { maxFileBytes = value.coerceAtLeast(64 * 1024) }
        fun captureLogcat(value: Boolean) = apply { captureLogcat = value }
        fun watchAnr(value: Boolean) = apply { watchAnr = value }
        fun onCrashInvokedOnNextStart(value: Boolean) = apply { onCrashInvokedOnNextStart = value }

        /** Redact secrets from recorded logs, e.g. `addRedaction(Regex("token=\\S+"))`. */
        fun addRedaction(pattern: Regex) = apply { redactions += pattern }

        fun build() = SleuthConfig(
            tagFilter = tagFilter,
            maxFiles = maxFiles,
            maxFileBytes = maxFileBytes,
            captureLogcat = captureLogcat,
            watchAnr = watchAnr,
            onCrashInvokedOnNextStart = onCrashInvokedOnNextStart,
            redactions = redactions.toList(),
        )
    }
}
