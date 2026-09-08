package io.github.shiaho777.logsleuth.app.core.apps

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A launchable app on the device, for pickers and per-app filters. */
data class AppChoice(val packageName: String, val label: String)

/** Loads the list of user-launchable apps (cached after first read). */
@Singleton
class InstalledApps @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    @Volatile
    private var cached: List<AppChoice>? = null

    suspend fun load(): List<AppChoice> {
        cached?.let { return it }
        return withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val apps = runCatching {
                pm.queryIntentActivities(intent, PackageManager.MATCH_ALL).mapNotNull { ri ->
                    val info: ApplicationInfo = ri.activityInfo.applicationInfo
                    AppChoice(
                        packageName = info.packageName,
                        label = runCatching { pm.getApplicationLabel(info).toString() }
                            .getOrDefault(info.packageName),
                    )
                }.distinctBy { it.packageName }.sortedBy { it.label.lowercase() }
            }.getOrDefault(emptyList())
            cached = apps
            apps
        }
    }

    fun resolveUid(packageName: String?): Int? {
        if (packageName == null) return null
        return runCatching {
            context.packageManager.getApplicationInfo(packageName, 0).uid
        }.getOrNull()
    }
}
