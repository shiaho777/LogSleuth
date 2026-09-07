package io.github.shiaho777.logsleuth.sdk

/** Metadata about a crash captured by the SDK. */
data class CrashReport(
    /** Epoch millis of the crash. */
    val timeMillis: Long,
    val threadName: String,
    val exceptionClass: String,
    val message: String?,
    val stackTrace: String,
)
