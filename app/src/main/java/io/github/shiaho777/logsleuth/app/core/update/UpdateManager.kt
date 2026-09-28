package io.github.shiaho777.logsleuth.app.core.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.shiaho777.logsleuth.app.BuildConfig
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** What the update card renders — check result plus any download in flight. */
data class UpdateUiState(
    val checking: Boolean = false,
    val checkError: String? = null,
    val releases: List<ReleaseInfo> = emptyList(),
    /** Latest non-draft release; the update target when [updateAvailable]. */
    val latest: ReleaseInfo? = null,
    val updateAvailable: Boolean = false,
    /** Release this download belongs to; null = nothing downloaded/in flight. */
    val dlRelease: ReleaseInfo? = null,
    val dlStatus: DlStatus = DlStatus.NONE,
    val dlDoneBytes: Long = 0,
    val dlTotalBytes: Long = 0,
    /** Smoothed download rate, bytes/second — only meaningful while RUNNING. */
    val dlBps: Long = 0,
    /** Finished APK on disk once [DlStatus.DONE]. */
    val dlFile: File? = null,
    val dlError: String? = null,
)

enum class DlStatus { NONE, RUNNING, PAUSED, DONE, FAILED }

/**
 * GitHub release checks + resumable APK downloads.
 *
 * Downloads keep a `<name>.part` file plus a `<name>.meta` JSON sidecar in
 * `filesDir/updates/`. Pausing just stops the read loop; the sidecar survives
 * process death, so the card restores a PAUSED (or DONE) row on next open and
 * resumes with an HTTP `Range` request.
 */
@Singleton
class UpdateManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        const val REPO_URL = "https://github.com/shiaho777/LogSleuth"
        private const val API = "https://api.github.com/repos/shiaho777/LogSleuth/releases"
        private const val CONNECT_MS = 10_000
        private const val READ_MS = 15_000
        private const val BUFFER = 64 * 1024
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val updatesDir = File(context.filesDir, "updates")
    private val _state = MutableStateFlow(UpdateUiState())
    val state = _state.asStateFlow()

    @Volatile private var paused = false
    private var downloadJob: Job? = null

    init {
        // Restore an interrupted/paused transfer left over from a previous run.
        scope.launch { restoreFromDisk() }
    }

    // ---------------- version check ----------------

    fun checkNow() {
        if (_state.value.checking) return
        scope.launch {
            _state.value = _state.value.copy(checking = true, checkError = null)
            try {
                val releases = fetchReleases()
                val latest = releases.firstOrNull()
                val current = BuildConfig.VERSION_NAME.substringBefore('-')
                _state.value = _state.value.copy(
                    checking = false,
                    releases = releases,
                    latest = latest,
                    updateAvailable = latest != null &&
                        SemVer.isNewer(latest.tag, current),
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    checking = false,
                    checkError = e.message ?: e.javaClass.simpleName,
                )
            }
        }
    }

    /** All non-draft releases, newest first. Throws on HTTP/parse failure. */
    private fun fetchReleases(): List<ReleaseInfo> {
        val conn = (URL(API).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_MS
            readTimeout = READ_MS
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "LogSleuth/${BuildConfig.VERSION_NAME}")
        }
        try {
            val code = conn.responseCode
            if (code != 200) throw RuntimeException("GitHub API HTTP $code")
            val arr = org.json.JSONArray(conn.inputStream.bufferedReader().readText())
            val out = ArrayList<ReleaseInfo>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                if (o.optBoolean("draft") || o.optBoolean("prerelease")) continue
                var apkName: String? = null
                var apkUrl: String? = null
                var apkSize = 0L
                val assets = o.optJSONArray("assets")
                if (assets != null) {
                    for (j in 0 until assets.length()) {
                        val a = assets.getJSONObject(j)
                        if (a.getString("name").endsWith(".apk", true)) {
                            apkName = a.getString("name")
                            apkUrl = a.getString("browser_download_url")
                            apkSize = a.getLong("size")
                            break
                        }
                    }
                }
                out += ReleaseInfo(
                    tag = o.getString("tag_name"),
                    title = o.optString("name"),
                    notes = o.optString("body"),
                    publishedAt = o.optString("published_at").take(10),
                    pageUrl = o.optString("html_url"),
                    apkName = apkName, apkUrl = apkUrl, apkSize = apkSize,
                )
            }
            return out
        } finally {
            conn.disconnect()
        }
    }

    // ---------------- resumable download ----------------

    private fun partFile(r: ReleaseInfo) = File(updatesDir, "${r.apkName}.part")
    private fun metaFile(r: ReleaseInfo) = File(updatesDir, "${r.apkName}.meta")
    private fun apkFile(r: ReleaseInfo): File? =
        r.apkName?.let { File(updatesDir, it) }

    fun start(release: ReleaseInfo) = transfer(release)
    fun resume() = _state.value.dlRelease?.let { transfer(it) }
    fun pause() { paused = true }

    fun cancelDownload() {
        paused = true
        downloadJob?.cancel()
        _state.value.dlRelease?.let { r ->
            partFile(r).delete(); metaFile(r).delete(); apkFile(r)?.delete()
        }
        _state.value = _state.value.copy(
            dlRelease = null, dlStatus = DlStatus.NONE,
            dlDoneBytes = 0, dlTotalBytes = 0, dlBps = 0, dlFile = null, dlError = null,
        )
    }

    /** One download slot — wipe leftovers from any other release. */
    private fun pruneUpdates(keep: String?) {
        updatesDir.listFiles()?.forEach { f ->
            // Keep "<apk>.apk", "<apk>.apk.part" and "<apk>.apk.meta".
            if (keep == null || !f.name.startsWith(keep)) f.delete()
        }
    }

    private fun transfer(r: ReleaseInfo) {
        if (r.apkUrl == null || downloadJob?.isActive == true) return
        paused = false
        downloadJob = scope.launch {
            updatesDir.mkdirs()
            pruneUpdates(r.apkName)
            val part = partFile(r)
            var out: FileOutputStream? = null
            try {
                metaFile(r).writeText(
                    JSONObject()
                        .put("tag", r.tag).put("title", r.title)
                        .put("notes", r.notes).put("url", r.apkUrl)
                        .put("apkName", r.apkName).put("size", r.apkSize)
                        .put("publishedAt", r.publishedAt)
                        .put("pageUrl", r.pageUrl)
                        .toString(),
                )
                val existing = part.length().takeIf { part.exists() } ?: 0L
                val conn = (URL(r.apkUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = CONNECT_MS
                    readTimeout = READ_MS
                    setRequestProperty("User-Agent", "LogSleuth/${BuildConfig.VERSION_NAME}")
                    if (existing > 0) setRequestProperty("Range", "bytes=$existing-")
                }
                try {
                    val code = conn.responseCode
                    // 206 = resumed append; 200 = server ignored Range → restart.
                    val resume = existing > 0 && code == HttpURLConnection.HTTP_PARTIAL
                    if (code !in 200..299) throw RuntimeException("HTTP $code")
                    var done = if (resume) existing else 0L
                    val total = done + conn.getHeaderFieldLong("Content-Length", r.apkSize)
                    _state.value = _state.value.copy(
                        dlRelease = r, dlStatus = DlStatus.RUNNING,
                        dlDoneBytes = done, dlTotalBytes = total, dlBps = 0,
                        dlFile = null, dlError = null,
                    )
                    out = FileOutputStream(part, resume)
                    val buf = ByteArray(BUFFER)
                    var lastT = System.nanoTime()
                    var lastBytes = done
                    while (true) {
                        if (paused) {
                            _state.value = _state.value.copy(
                                dlStatus = DlStatus.PAUSED, dlBps = 0, dlDoneBytes = done,
                            )
                            return@launch
                        }
                        val n = conn.inputStream.read(buf)
                        if (n < 0) break
                        out!!.write(buf, 0, n)
                        done += n
                        val now = System.nanoTime()
                        if (now - lastT > 300_000_000L) {
                            val bps = ((done - lastBytes) * 1e9 / (now - lastT)).toLong()
                            _state.value = _state.value.copy(
                                dlDoneBytes = done, dlTotalBytes = total, dlBps = bps,
                            )
                            lastT = now; lastBytes = done
                        }
                    }
                    out!!.flush()
                    val finalApk = apkFile(r)!!
                    part.renameTo(finalApk)
                    metaFile(r).delete()
                    _state.value = _state.value.copy(
                        dlStatus = DlStatus.DONE, dlDoneBytes = done,
                        dlTotalBytes = done, dlBps = 0, dlFile = finalApk,
                    )
                } finally {
                    conn.disconnect()
                }
            } catch (e: Exception) {
                if (!paused && _state.value.dlStatus != DlStatus.PAUSED) {
                    _state.value = _state.value.copy(
                        dlStatus = DlStatus.FAILED, dlError = e.message ?: "download failed",
                    )
                }
            } finally {
                out?.close()
            }
        }
    }

    /** Rebuild download state from on-disk leftovers after a process restart. */
    private suspend fun restoreFromDisk() = withContext(Dispatchers.IO) {
        val metas = updatesDir.listFiles { f -> f.name.endsWith(".meta") } ?: return@withContext
        for (m in metas) {
            try {
                val o = JSONObject(m.readText())
                val r = ReleaseInfo(
                    tag = o.getString("tag"), title = o.optString("title"),
                    notes = o.optString("notes"),
                    publishedAt = o.optString("publishedAt"),
                    pageUrl = o.optString("pageUrl"),
                    apkName = o.getString("apkName"),
                    apkUrl = o.getString("url"), apkSize = o.optLong("size"),
                )
                val done = partFile(r).length().takeIf { partFile(r).exists() } ?: 0L
                pruneUpdates(r.apkName)
                if (apkFile(r)?.exists() == true) {
                    _state.value = _state.value.copy(
                        dlRelease = r, dlStatus = DlStatus.DONE,
                        dlDoneBytes = r.apkSize, dlTotalBytes = r.apkSize, dlFile = apkFile(r),
                    )
                } else {
                    _state.value = _state.value.copy(
                        dlRelease = r, dlStatus = DlStatus.PAUSED,
                        dlDoneBytes = done, dlTotalBytes = r.apkSize,
                    )
                }
                return@withContext
            } catch (_: Exception) { /* corrupt sidecar → skip */ }
        }
        // A finished APK whose meta was already deleted also counts as done.
        updatesDir.listFiles { f -> f.name.endsWith(".apk") }?.firstOrNull()?.let { apk ->
            _state.value = _state.value.copy(
                dlRelease = ReleaseInfo(
                    tag = apk.nameWithoutExtension.removePrefix("LogSleuth-"),
                    title = "", notes = "",
                    publishedAt = "", pageUrl = REPO_URL,
                    apkName = apk.name, apkUrl = null, apkSize = apk.length(),
                ),
                dlStatus = DlStatus.DONE, dlFile = apk,
                dlDoneBytes = apk.length(), dlTotalBytes = apk.length(),
            )
        }
    }

    // ---------------- intents ----------------

    fun repoIntent() = Intent(Intent.ACTION_VIEW, Uri.parse(REPO_URL))

    fun openReleaseIntent(r: ReleaseInfo) =
        Intent(Intent.ACTION_VIEW, Uri.parse(r.pageUrl))

    /** Install intent for the finished APK. Caller checks the permission first. */
    fun installIntent(file: File): Intent {
        val uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file,
        )
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** Can we sideload the APK yet, or does the user first need to allow it? */
    fun canInstallUnknownApps(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()

    fun unknownSourcesIntent(): Intent = Intent(
        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
        Uri.parse("package:${context.packageName}"),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
