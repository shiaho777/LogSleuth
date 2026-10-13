package io.github.shiaho777.logsleuth.app.ui.crashes

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.shiaho777.logsleuth.app.core.crashes.CrashContextStore
import io.github.shiaho777.logsleuth.app.core.crashes.CrashDeleter
import io.github.shiaho777.logsleuth.app.core.crashes.CrashSearch
import io.github.shiaho777.logsleuth.app.core.export.SessionExporter
import io.github.shiaho777.logsleuth.app.data.db.CrashEventDao
import io.github.shiaho777.logsleuth.app.data.db.CrashEventEntity
import io.github.shiaho777.logsleuth.app.data.prefs.SettingsRepository
import io.github.shiaho777.logsleuth.app.service.NotificationHelper
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CrashDetailsUi(
    val crash: CrashEventEntity? = null,
    val contextText: String? = null,
    val wrap: Boolean = true,
    val searchQuery: String = "",
    val searchIgnoreCase: Boolean = true,
    val searchRanges: List<IntRange> = emptyList(),
    val contextSearchRanges: List<IntRange> = emptyList(),
    val searching: Boolean = false,
    val includeApp: Boolean = true,
    val includeDevice: Boolean = true,
    val separateChannels: Boolean = true,
)

@HiltViewModel
class CrashDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    crashEventDao: CrashEventDao,
    private val contextStore: CrashContextStore,
    private val exporter: SessionExporter,
    private val settingsRepository: SettingsRepository,
    private val notificationHelper: NotificationHelper,
    private val deleter: CrashDeleter,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val eventId: Long = savedStateHandle.get<Long>("eventId") ?: -1L

    private val searchQuery = MutableStateFlow("")
    private val searching = MutableStateFlow(false)
    private val contextText = MutableStateFlow<String?>(null)

    val ui: StateFlow<CrashDetailsUi> = combine(
        crashEventDao.observeById(eventId),
        settingsRepository.settings,
        searchQuery,
        searching,
        contextText,
    ) { crash, settings, query, isSearching, surrounding ->
        val ignoreCase = !settings.searchCaseSensitive
        val stack = crash?.snippet.orEmpty()
        val hits = CrashSearch.search(stack, surrounding, query, ignoreCase)
        CrashDetailsUi(
            crash = crash,
            contextText = surrounding,
            wrap = settings.wrapCrashLogLines,
            searchQuery = query,
            searchIgnoreCase = ignoreCase,
            searchRanges = hits.snippetRanges,
            contextSearchRanges = hits.contextRanges,
            searching = isSearching,
            includeApp = settings.includeAppInfoInZip,
            includeDevice = settings.includeDeviceInfoInZip,
            separateChannels = settings.separateCrashChannels,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CrashDetailsUi())

    init {
        viewModelScope.launch {
            crashEventDao.observeById(eventId).collect { crash ->
                contextText.value = contextStore.read(crash?.contextPath)
            }
        }
    }

    fun setSearching(value: Boolean) {
        searching.value = value
        if (!value) searchQuery.value = ""
    }

    fun setQuery(query: String) {
        searchQuery.value = query
    }

    fun setWrap(value: Boolean) {
        viewModelScope.launch { settingsRepository.setWrapCrashLogLines(value) }
    }

    fun share() {
        val crash = ui.value.crash ?: return
        viewModelScope.launch {
            exporter.shareCrash(crash, ui.value.includeApp, ui.value.includeDevice)
        }
    }

    suspend fun prepareZip(): File? {
        val crash = ui.value.crash ?: return null
        return exporter.crashZip(crash, ui.value.includeApp, ui.value.includeDevice).getOrNull()
    }

    suspend fun saveZip(uri: Uri) {
        val file = prepareZip() ?: return
        exporter.saveToUri(file, uri)
    }

    fun copyStack() {
        val text = ui.value.crash?.snippet ?: return
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("crash", text))
    }

    fun openAppInfo() {
        val pkg = ui.value.crash?.packageName ?: return
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", pkg, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun openChannel() {
        val crash = ui.value.crash ?: return
        notificationHelper.openCrashChannel(crash.type, crash.packageName, ui.value.separateChannels)
    }

    fun ignore() {
        val pkg = ui.value.crash?.packageName ?: return
        viewModelScope.launch { settingsRepository.ignorePackage(pkg) }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            deleter.deleteIds(listOf(eventId))
            onDone()
        }
    }

    fun zipFileName(): String {
        val crash = ui.value.crash
        val safe = (crash?.packageName ?: "unknown").replace(Regex("""[^\w.-]+"""), "_")
        return "crash_${safe}_${crash?.id ?: eventId}.zip"
    }
}
