package io.github.shiaho777.logsleuth.app.core.logcat

import java.util.Calendar

/**
 * Turns a logcat uid column into an integer uid, and the other way around
 * for the well-known system accounts.
 *
 * `-v uid` is not always a decimal. Android prints app uids as `u0a123`
 * (or `u0_a123`) and system accounts by name (`system`, `radio`, …).
 * A parser that only accepts digits treats those lines as continuations
 * and glues them onto the previous message.
 */
object UidNames {

    private val APP_UID = Regex("""^u(\d+)_?a(\d+)$""")
    private val ISOLATED_UID = Regex("""^u(\d+)_?i(\d+)$""")

    /** `u<user>a<app>` / `u<user>_a<app>` → user * 100000 + 10000 + app. */
    private const val USER_OFFSET = 100_000
    private const val APP_START = 10_000
    private const val ISOLATED_START = 99_000

    fun resolve(token: String): Int? {
        val clean = token.trim()
        if (clean.isEmpty()) return null
        clean.toIntOrNull()?.let { return it }
        WELL_KNOWN[clean]?.let { return it }
        APP_UID.matchEntire(clean)?.let { m ->
            val user = m.groupValues[1].toIntOrNull() ?: return null
            val app = m.groupValues[2].toIntOrNull() ?: return null
            return user * USER_OFFSET + APP_START + app
        }
        ISOLATED_UID.matchEntire(clean)?.let { m ->
            val user = m.groupValues[1].toIntOrNull() ?: return null
            val app = m.groupValues[2].toIntOrNull() ?: return null
            return user * USER_OFFSET + ISOLATED_START + app
        }
        return null
    }

    /** Name such as `system` when [uid] is a well-known account, else null. */
    fun wellKnownName(uid: Int): String? = NAME_BY_UID[uid]

    /**
     * `MM-dd HH:mm:ss.SSS` in the default timezone — the stamp `logcat -T`
     * accepts alongside `-v threadtime`.
     */
    fun formatThreadTime(millis: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        return "%02d-%02d %02d:%02d:%02d.%03d".format(
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH),
            cal.get(Calendar.HOUR_OF_DAY),
            cal.get(Calendar.MINUTE),
            cal.get(Calendar.SECOND),
            cal.get(Calendar.MILLISECOND),
        )
    }

    private val WELL_KNOWN: Map<String, Int> = mapOf(
        "root" to 0,
        "system" to 1000,
        "radio" to 1001,
        "bluetooth" to 1002,
        "graphics" to 1003,
        "input" to 1004,
        "audio" to 1005,
        "camera" to 1006,
        "log" to 1007,
        "compass" to 1008,
        "mount" to 1009,
        "wifi" to 1010,
        "adb" to 1011,
        "install" to 1012,
        "media" to 1013,
        "dhcp" to 1014,
        "sdcard_rw" to 1015,
        "vpn" to 1016,
        "keystore" to 1017,
        "usb" to 1018,
        "drm" to 1019,
        "mdnsr" to 1020,
        "gps" to 1021,
        "media_rw" to 1023,
        "mtp" to 1024,
        "drmrpc" to 1026,
        "nfc" to 1027,
        "shell" to 2000,
        "cache" to 2001,
        "nobody" to 9999,
    )

    private val NAME_BY_UID: Map<Int, String> = WELL_KNOWN.entries.associate { (name, uid) -> uid to name }
}
