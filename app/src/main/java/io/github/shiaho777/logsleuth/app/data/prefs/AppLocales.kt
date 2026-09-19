package io.github.shiaho777.logsleuth.app.data.prefs

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList

/**
 * In-app language override (English / 简体中文 / follow system).
 *
 * The override is stored in a plain SharedPreferences file because it must be
 * read *synchronously* inside `attachBaseContext` — DataStore's async read
 * cannot participate there. `SettingsRepository` keeps a DataStore copy for
 * reactive UI state; this store is the source of truth for applying it.
 *
 * Application, Activity and Service contexts all call [wrap] from
 * `attachBaseContext`, so resources resolve in the chosen locale everywhere —
 * Compose `stringResource`, notification channel names, service toasts.
 *
 * On Android 13+ the choice is additionally pushed to the framework's per-app
 * language setting ([LocaleManager]); the system then applies and persists it
 * itself and restarts running activities. On older versions the caller
 * recreates the activity after [setOverride].
 */
object AppLocales {

    const val SYSTEM = "system"
    const val EN = "en"
    const val ZH_CN = "zh-CN" // BCP 47 tag matching values-zh-rCN

    val options = listOf(SYSTEM, EN, ZH_CN)

    private const val PREFS = "app_locale"
    private const val KEY_TAG = "tag"

    fun overrideTag(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_TAG, SYSTEM)
            ?.takeIf { it in options }
            ?: SYSTEM

    /**
     * Persists the override (synchronous `commit` so a following `recreate()`
     * observes it) and, on API 33+, syncs the framework per-app locale —
     * which also triggers the activity restart itself.
     */
    fun setOverride(context: Context, tag: String) {
        val value = tag.takeIf { it in options } ?: SYSTEM
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TAG, value)
            .commit()
        if (Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(LocaleManager::class.java)?.applicationLocales =
                if (value == SYSTEM) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(value)
        }
    }

    /** Wraps [base] with the stored override; pass-through for "system". */
    fun wrap(base: Context): Context {
        val tag = overrideTag(base)
        val locales = if (tag == SYSTEM) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
        if (locales.isEmpty) return base
        val config = Configuration(base.resources.configuration)
        config.setLocales(locales)
        return base.createConfigurationContext(config)
    }

    /** True when the framework won't restart the activity for us. */
    fun needsManualRecreate(): Boolean = Build.VERSION.SDK_INT < 33

    /** Applies a new language from a UI click: store, then recreate on <33. */
    fun applyFromUi(context: Context, tag: String) {
        setOverride(context, tag)
        if (needsManualRecreate()) context.findActivity()?.recreate()
    }

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
