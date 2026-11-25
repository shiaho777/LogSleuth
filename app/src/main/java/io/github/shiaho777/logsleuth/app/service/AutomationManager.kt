package io.github.shiaho777.logsleuth.app.service

import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.shiaho777.logsleuth.app.core.automation.AutomationPayload
import io.github.shiaho777.logsleuth.app.core.automation.AutomationRule
import io.github.shiaho777.logsleuth.app.core.automation.CompiledAutomationRule
import io.github.shiaho777.logsleuth.app.core.logcat.LogcatEngine
import io.github.shiaho777.logsleuth.app.core.logcat.LogcatEntry
import io.github.shiaho777.logsleuth.app.data.db.AutomationRuleDao
import io.github.shiaho777.logsleuth.app.data.db.toRule
import io.github.shiaho777.logsleuth.app.data.prefs.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Watches the live stream for the user's automation rules and sends each
 * match as an **explicit** broadcast to the rule's target package (Tasker,
 * MacroDroid, …). Nothing is ever broadcast implicitly — log content only
 * leaves the app toward a package the user picked.
 *
 * Runs while [AutomationService] holds it started; the service owns the
 * engine client that keeps logcat alive in the background. Per-rule
 * cooldowns bound the broadcast rate on a hot log stream.
 */
@Singleton
class AutomationManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val engine: LogcatEngine,
    private val ruleDao: AutomationRuleDao,
    private val settingsRepository: SettingsRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var collectJob: Job? = null
    private var rulesJob: Job? = null

    @Volatile
    private var compiled: List<CompiledAutomationRule> = emptyList()

    /** uid → package, refreshed lazily; only used to fill the package extra. */
    private val packageByUid = HashMap<Int, String?>()

    private val lastSentAt = HashMap<Long, Long>()

    @Synchronized
    fun start() {
        if (collectJob != null) return
        rulesJob = scope.launch {
            ruleDao.observeAll().collect { entities ->
                compiled = entities.map { it.toRule() }
                    .filter { it.enabled && it.targetPackage.isNotBlank() }
                    .map { CompiledAutomationRule(it) }
            }
        }
        collectJob = scope.launch {
            engine.entries.collect { entry -> dispatch(entry) }
        }
    }

    @Synchronized
    fun stop() {
        collectJob?.cancel()
        collectJob = null
        rulesJob?.cancel()
        rulesJob = null
        compiled = emptyList()
        lastSentAt.clear()
    }

    private fun dispatch(entry: LogcatEntry) {
        val rules = compiled
        if (rules.isEmpty()) return
        val now = System.currentTimeMillis()
        val pkg = if (entry.remoteEndpoint != null) entry.remotePackage else entry.uid?.let(::packageFor)
        for (compiledRule in rules) {
            val rule = compiledRule.rule
            val last = lastSentAt[rule.id]
            if (last != null && now - last < rule.cooldownSeconds.toLong() * 1_000L) continue
            if (!compiledRule.matches(entry, pkg)) continue
            lastSentAt[rule.id] = now
            send(rule, entry, pkg)
        }
    }

    private fun send(rule: AutomationRule, entry: LogcatEntry, pkg: String?) {
        val intent = Intent(rule.action).setPackage(rule.targetPackage)
        entry.remoteEndpoint?.let { intent.putExtra("remote_endpoint", it) }
        for ((key, value) in AutomationPayload.extras(rule, entry, pkg)) {
            when (value) {
                is String -> intent.putExtra(key, value)
                is Int -> intent.putExtra(key, value)
                is Long -> intent.putExtra(key, value)
            }
        }
        runCatching { context.sendBroadcast(intent) }
            .onFailure { Log.w(TAG, "automation broadcast failed for ${rule.targetPackage}", it) }
    }

    private fun packageFor(uid: Int): String? {
        packageByUid[uid]?.let { return it }
        if (packageByUid.containsKey(uid)) return null
        val packages = runCatching { context.packageManager.getPackagesForUid(uid) }.getOrNull()
        val pkg = packages?.firstOrNull()
        packageByUid[uid] = pkg
        return pkg
    }

    /** Whether the master switch is on — the service checks this on start. */
    suspend fun enabledInSettings(): Boolean = settingsRepository.settings.first().automationEnabled

    private companion object {
        const val TAG = "LogSleuthAutomation"
    }
}