package io.github.shiaho777.logsleuth.app.core.logcat

/**
 * Which grant wins when more than one is available.
 *
 * `auto` keeps Shizuku ahead of root so a phone that already uses Shizuku
 * does not grow a root shell. The user can put root first from Settings.
 * A chosen path that is not actually granted falls through to the next one
 * that is, so a stale preference cannot blank the stream.
 */
object AccessPolicy {
    const val AUTO = "auto"
    const val SHIZUKU = "shizuku"
    const val ROOT = "root"
    const val ADB = "adb"
    const val REMOTE = "remote"

    fun choose(
        preference: String,
        shizukuReady: Boolean,
        rootReady: Boolean,
        readLogs: Boolean,
        remoteReady: Boolean = false,
    ): AccessKind {
        val local = listOf(AccessKind.SHIZUKU, AccessKind.ROOT, AccessKind.READ_LOGS)
        val order = when (preference) {
            SHIZUKU -> listOf(AccessKind.SHIZUKU, AccessKind.ROOT, AccessKind.READ_LOGS)
            ROOT -> listOf(AccessKind.ROOT, AccessKind.SHIZUKU, AccessKind.READ_LOGS)
            ADB -> listOf(AccessKind.READ_LOGS, AccessKind.SHIZUKU, AccessKind.ROOT)
            // Remote first while it is connected; local paths stay as the
            // fallback so a dropped remote link cannot blank the stream.
            REMOTE -> listOf(AccessKind.REMOTE_ADB) + local
            else -> local
        }
        return order.firstOrNull { kind ->
            when (kind) {
                AccessKind.SHIZUKU -> shizukuReady
                AccessKind.ROOT -> rootReady
                AccessKind.READ_LOGS -> readLogs
                AccessKind.REMOTE_ADB -> remoteReady
                AccessKind.NONE -> false
            }
        } ?: AccessKind.NONE
    }
}
