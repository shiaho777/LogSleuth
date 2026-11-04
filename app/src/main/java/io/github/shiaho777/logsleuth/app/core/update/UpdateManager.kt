package io.github.shiaho777.logsleuth.app.core.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.shiaho777.logsleuth.app.BuildConfig
import io.github.shiaho777.logsleuth.app.R
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
 *
 * One transfer slot: [transferLock] keeps a cancelled transfer from writing
 * the part file while its replacement does, and [generation] makes sure a
 * transfer that was superseded or cancelled cannot publish state any more.
 * Pause and cancel are noticed within [ApkDownloader]'s read timeout.
 */
@Singleton
class UpdateManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        const val REPO_URL = "https://github.com/shiaho777/LogSleuth"
        private const val API = "https://api.github.com/repos/shiaho777/LogSleuth/releases"
        private const val CONNECT_MS = 10_000
        private const val API_READ_MS = 15_000

        /** Short enough that pause and cancel are felt immediately. */
        private const val DOWNLOAD_READ_MS = 2_000
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val updatesDir = File(context.filesDir, "updates")
    private val _state = MutableStateFlow(UpdateUiState())
    val state = _state.asStateFlow()

    private val checkGuard = AtomicBoolean(false)

    /** True while the current transfer should keep reading. */
    @Volatile private var paused = false

    /**
     * Bumped by every transfer and by cancel. A transfer only publishes while
     * its generation is current, so one that was replaced or cancelled cannot
     * resurrect the card or report a failure for someone else's download.
     */
    @Volatile private var generation = 0

    private var downloadJob: Job? = null

    /** Only one transfer may touch the part file at a time. */
    private val transferLock = Mutex()

    init {
        // Restore an interrupted/paused transfer left over from a previous run.
        scope.launch { restoreFromDisk() }
    }

    // ---------------- version check ----------------

    fun checkNow() {
        if (!checkGuard.compareAndSet(false, true)) return
        _state.update { it.copy(checking = true, checkError = null) }
        scope.launch {
            try {
                val releases = fetchReleases()
                val latest = releases.firstOrNull()
                val current = BuildConfig.VERSION_NAME.substringBefore('-')
                _state.update {
                    it.copy(
                        releases = releases,
                        latest = latest,
                        updateAvailable = latest != null && SemVer.isNewer(latest.tag, current),
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(checkError = e.message ?: e.javaClass.simpleName) }
            } finally {
                _state.update { it.copy(checking = false) }
                checkGuard.set(false)
            }
        }
    }

    /** All non-draft releases, newest first. Throws on HTTP/parse failure. */
    private fun fetchReleases(): List<ReleaseInfo> {
        val conn = (URL(API).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_MS
            readTimeout = API_READ_MS
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

    fun start(release: ReleaseInfo) = launchTransfer(release)

    fun resume() {
        val release = _state.value.dlRelease ?: return
        if (_state.value.dlStatus == DlStatus.DONE) return
        launchTransfer(release)
    }

    fun pause() {
        if (_state.value.dlStatus != DlStatus.RUNNING) return
        paused = true
        _state.update { it.copy(dlStatus = DlStatus.PAUSED, dlBps = 0) }
    }

    fun cancelDownload() {
        paused = true
        generation++
        downloadJob?.cancel()
        downloadJob = null
        // One slot only, so everything there belongs to this download.
        updatesDir.listFiles()?.forEach { it.delete() }
        _state.update {
            it.copy(
                dlRelease = null, dlStatus = DlStatus.NONE,
                dlDoneBytes = 0, dlTotalBytes = 0, dlBps = 0, dlFile = null, dlError = null,
            )
        }
    }

    /**
     * Invalidates any transfer already running (it stops within the read
     * timeout) and queues this one behind it. The lock is what guarantees two
     * jobs never write the same part file.
     */
    private fun launchTransfer(release: ReleaseInfo) {
        val url = release.apkUrl ?: return
        val gen = ++generation
        paused = false
        downloadJob = scope.launch {
            transferLock.withLock {
                if (gen != generation) return@withLock
                transfer(release, url, gen)
            }
        }
    }

    private suspend fun transfer(release: ReleaseInfo, url: String, gen: Int) {
        updatesDir.mkdirs()
        pruneUpdates(release.apkName)
        runCatching { writeMeta(release) }
        val part = partFile(release)
        val finalApk = apkFile(release) ?: return
        publish(gen) {
            it.copy(
                dlRelease = release, dlStatus = DlStatus.RUNNING,
                dlDoneBytes = part.length().takeIf { part.isFile } ?: 0L,
                dlTotalBytes = release.apkSize, dlBps = 0, dlFile = null, dlError = null,
            )
        }
        val outcome = ApkDownloader(
            url = url,
            part = part,
            expectedSize = release.apkSize,
            userAgent = "LogSleuth/${BuildConfig.VERSION_NAME}",
            connectTimeoutMs = CONNECT_MS,
            readTimeoutMs = DOWNLOAD_READ_MS,
            onProgress = { done, total, bps ->
                publish(gen) { it.copy(dlDoneBytes = done, dlTotalBytes = total, dlBps = bps) }
            },
            keepGoing = { gen == generation && !paused },
        ).run()
        when (outcome) {
            is FetchOutcome.Complete -> finish(release, part, finalApk, outcome.bytes, gen)
            is FetchOutcome.Stopped -> publish(gen) {
                it.copy(dlStatus = DlStatus.PAUSED, dlDoneBytes = outcome.bytes, dlBps = 0)
            }
            is FetchOutcome.Failed -> publish(gen) {
                it.copy(
                    dlStatus = DlStatus.FAILED, dlDoneBytes = outcome.bytes,
                    dlBps = 0, dlError = errorMessage(outcome.failure),
                )
            }
        }
    }

    /** Moves the finished part file into place; only then is the download done. */
    private fun finish(release: ReleaseInfo, part: File, finalApk: File, bytes: Long, gen: Int) {
        if (gen != generation) return
        if (!part.renameTo(finalApk)) {
            // Renaming over an existing file is not allowed everywhere.
            finalApk.delete()
            if (!part.renameTo(finalApk)) {
                publish(gen) {
                    it.copy(
                        dlStatus = DlStatus.FAILED,
                        dlError = context.getString(R.string.update_error_finalize),
                    )
                }
                return
            }
        }
        metaFile(release).delete()
        publish(gen) {
            it.copy(
                dlStatus = DlStatus.DONE, dlDoneBytes = bytes, dlTotalBytes = bytes,
                dlBps = 0, dlFile = finalApk, dlError = null,
            )
        }
    }

    /** Turns a transfer failure into the card's message, in the UI language. */
    private fun errorMessage(failure: FetchFailure): String = when (failure) {
        is FetchFailure.Http -> context.getString(R.string.update_error_http, failure.code)
        FetchFailure.Incomplete -> context.getString(R.string.update_error_incomplete)
        FetchFailure.Stalled -> context.getString(R.string.update_error_stalled)
        is FetchFailure.Io -> context.getString(R.string.update_error_io, failure.message ?: "?")
    }

    private fun publish(gen: Int, block: (UpdateUiState) -> UpdateUiState) {
        if (gen != generation) return
        // Re-checked inside the update: cancelDownload can land between the
        // check above and the write below.
        _state.update { if (gen != generation) it else block(it) }
    }

    private fun writeMeta(r: ReleaseInfo) {
        metaFile(r).writeText(
            JSONObject()
                .put("tag", r.tag).put("title", r.title)
                .put("notes", r.notes).put("url", r.apkUrl)
                .put("apkName", r.apkName).put("size", r.apkSize)
                .put("publishedAt", r.publishedAt)
                .put("pageUrl", r.pageUrl)
                .toString(),
        )
    }

    /** One download slot — wipe leftovers from any other release. */
    private fun pruneUpdates(keep: String?) {
        updatesDir.listFiles()?.forEach { f ->
            // Keep "<apk>.apk", "<apk>.apk.part" and "<apk>.apk.meta".
            if (keep == null || !f.name.startsWith(keep)) f.delete()
        }
    }

    /** Rebuild download state from on-disk leftovers after a process restart. */
    private suspend fun restoreFromDisk() = withContext(Dispatchers.IO) {
        discardStale()
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
                val part = partFile(r)
                val apk = apkFile(r)
                val done = part.length().takeIf { part.isFile } ?: 0L
                pruneUpdates(r.apkName)
                when {
                    apk?.isFile == true -> _state.update {
                        it.copy(
                            dlRelease = r, dlStatus = DlStatus.DONE,
                            dlDoneBytes = apk.length(), dlTotalBytes = apk.length(), dlFile = apk,
                        )
                    }
                    // Every byte arrived but the process died before the rename.
                    apk != null && r.apkSize > 0 && done >= r.apkSize && part.renameTo(apk) -> {
                        m.delete()
                        _state.update {
                            it.copy(
                                dlRelease = r, dlStatus = DlStatus.DONE,
                                dlDoneBytes = apk.length(), dlTotalBytes = apk.length(), dlFile = apk,
                            )
                        }
                    }
                    else -> _state.update {
                        it.copy(
                            dlRelease = r, dlStatus = DlStatus.PAUSED,
                            dlDoneBytes = done, dlTotalBytes = r.apkSize,
                        )
                    }
                }
                return@withContext
            } catch (_: Exception) { /* corrupt sidecar → skip */ }
        }
        // A finished APK whose sidecar was already deleted also counts as done.
        updatesDir.listFiles { f -> f.name.endsWith(".apk", true) }?.firstOrNull()?.let { apk ->
            _state.update {
                it.copy(
                    dlRelease = ReleaseInfo(
                        tag = tagFromApkName(apk.name),
                        title = "", notes = "",
                        publishedAt = "", pageUrl = REPO_URL,
                        apkName = apk.name, apkUrl = null, apkSize = apk.length(),
                    ),
                    dlStatus = DlStatus.DONE, dlFile = apk,
                    dlDoneBytes = apk.length(), dlTotalBytes = apk.length(),
                )
            }
        }
    }

    /**
     * Deletes downloads of the running version or older — installing them would
     * not update anything, and after an in-app update the APK that was just
     * installed would otherwise sit in filesDir forever. Unrecognised names are
     * kept: a renamed asset is still a valid download.
     */
    private fun discardStale() {
        val current = BuildConfig.VERSION_NAME.substringBefore('-')
        updatesDir.listFiles().orEmpty().forEach { f ->
            val tag = fileTag(f.name) ?: return@forEach
            val cmp = SemVer.compare(tag, current) ?: return@forEach
            if (cmp <= 0) f.delete()
        }
    }

    /** `LogSleuth-0.5.0.apk[.part|.meta]` → `0.5.0`; null for other files. */
    private fun fileTag(name: String): String? {
        val stem = if (name.endsWith(".part") || name.endsWith(".meta")) name.dropLast(5) else name
        return if (stem.endsWith(".apk", ignoreCase = true)) tagFromApkName(stem) else null
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

/**
 * Reads the version back out of a downloaded APK's file name.
 *
 * The release asset is `LogSleuth-<version>.APK`, but the name is not
 * load-bearing: the prefix is stripped case-insensitively, and anything that
 * does not look like a prefix is kept, so a renamed asset still shows a
 * sensible tag.
 */
private fun tagFromApkName(name: String): String {
    val stem = name.substringBeforeLast('.', name)
    return stem.replaceFirst(Regex("(?i)^logsleuth[-_]"), "")
}
