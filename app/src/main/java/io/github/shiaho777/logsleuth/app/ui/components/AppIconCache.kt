package io.github.shiaho777.logsleuth.app.ui.components

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import io.github.shiaho777.logsleuth.app.core.logcat.UidNames
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Shared uid → app-icon cache for dense log lists. Rows subscribe to the
 * [icons] snapshot map during composition, so a bitmap landing for one uid
 * recomposes only the rows carrying it — PackageManager IPC happens once
 * per uid, off the main thread, and never for off-screen rows.
 */
class AppIconCache(context: Context, private val scope: CoroutineScope) {
    private val pm = context.packageManager
    private val icons = mutableStateMapOf<Int, ImageBitmap?>()
    private val labels = mutableStateMapOf<Int, String?>()
    private val pending = ConcurrentHashMap.newKeySet<Int>()
    private val pendingLabels = ConcurrentHashMap.newKeySet<Int>()
    @Volatile private var launchable: Set<String>? = null

    /** Bitmap for [uid], or null while resolving / when the uid maps nowhere. */
    fun iconFor(uid: Int?): ImageBitmap? {
        if (uid == null) return null
        if (uid !in icons && pending.add(uid)) {
            scope.launch {
                val bmp = withContext(Dispatchers.IO) { resolve(uid) }
                icons[uid] = bmp
            }
        }
        return icons[uid]
    }

    /**
     * Short name for [uid]. Well-known accounts (`system`, `shell`) return
     * immediately. App uids resolve to a package label off the main thread
     * and return null until that lookup lands.
     */
    fun labelFor(uid: Int?): String? {
        if (uid == null) return null
        UidNames.wellKnownName(uid)?.let { return it }
        if (uid !in labels && pendingLabels.add(uid)) {
            scope.launch {
                val label = withContext(Dispatchers.IO) { resolveLabel(uid) }
                labels[uid] = label
            }
        }
        return labels[uid]
    }

    private fun resolve(uid: Int): ImageBitmap? {
        val pkg = pickPackage(uid) ?: return null
        return runCatching {
            pm.getApplicationIcon(pkg).toBitmap(48, 48).asImageBitmap()
        }.getOrNull()
    }

    private fun resolveLabel(uid: Int): String? {
        val pkg = pickPackage(uid) ?: return null
        val label = runCatching {
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, PackageManager.GET_META_DATA)).toString()
        }.getOrNull()
        return label?.takeIf { it.isNotBlank() } ?: pkg
    }

    private fun pickPackage(uid: Int): String? {
        val pkgs = runCatching { pm.getPackagesForUid(uid)?.toList() }
            .getOrNull().orEmpty()
        if (pkgs.isEmpty()) return null
        val launchable = launchable ?: loadLaunchable().also { launchable = it }
        return pkgs.firstOrNull { it in launchable } ?: pkgs.first()
    }

    private fun loadLaunchable(): Set<String> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return runCatching {
            pm.queryIntentActivities(intent, 0)
                .mapTo(HashSet()) { it.activityInfo.packageName }
        }.getOrDefault(emptySet())
    }
}

val LocalAppIconCache = compositionLocalOf<AppIconCache?> { null }
