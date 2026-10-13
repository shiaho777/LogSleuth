package io.github.shiaho777.logsleuth.app.ui.crashes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.shiaho777.logsleuth.app.core.crashes.CrashContextStore
import io.github.shiaho777.logsleuth.app.core.crashes.CrashDeleter
import io.github.shiaho777.logsleuth.app.core.export.SessionExporter
import io.github.shiaho777.logsleuth.app.data.db.CrashEventDao
import io.github.shiaho777.logsleuth.app.data.db.CrashEventEntity
import io.github.shiaho777.logsleuth.app.data.prefs.SettingsRepository
import io.github.shiaho777.logsleuth.app.service.NotificationHelper
import io.github.shiaho777.logsleuth.app.ui.navigation.Routes
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class AppCrashesViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    crashEventDao: CrashEventDao,
    private val deleter: CrashDeleter,
    private val exporter: SessionExporter,
    private val contextStore: CrashContextStore,
    private val notificationHelper: NotificationHelper,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val packageName: String? = Routes.packageFromKey(savedStateHandle.get<String>("packageName"))

    val highlightEventId: Long = savedStateHandle.get<Long>("eventId") ?: -1L

    private val all = crashEventDao.observeAll()
        .map { rows -> rows.filter { it.packageName == packageName } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _pendingId = MutableStateFlow<Long?>(null)
    val pendingDelete: StateFlow<Long?> = _pendingId.asStateFlow()

    private val _pendingAll = MutableStateFlow(false)
    val pendingDeleteAll: StateFlow<Boolean> = _pendingAll.asStateFlow()

    val crashes: StateFlow<List<CrashEventEntity>> = kotlinx.coroutines.flow.combine(
        all,
        _pendingId,
        _pendingAll,
    ) { rows, hidden, hideAll ->
        if (hideAll) emptyList() else rows.filter { it.id != hidden }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val separateChannels: StateFlow<Boolean> = settingsRepository.settings
        .map { it.separateCrashChannels }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    private var deleteJob: Job? = null

    fun requestDelete(crash: CrashEventEntity) {
        commitPending()
        _pendingId.value = crash.id
        deleteJob = viewModelScope.launch {
            delay(4_000)
            val id = _pendingId.value
            _pendingId.value = null
            if (id != null) deleter.deleteIds(listOf(id))
        }
    }

    fun requestDeleteAll() {
        commitPending()
        if (all.value.isEmpty()) return
        _pendingAll.value = true
        deleteJob = viewModelScope.launch {
            delay(4_000)
            val ids = all.value.map { it.id }
            _pendingAll.value = false
            deleter.deleteIds(ids)
        }
    }

    fun undoDelete() {
        deleteJob?.cancel()
        _pendingId.value = null
        _pendingAll.value = false
    }

    fun share(crash: CrashEventEntity) {
        viewModelScope.launch { exporter.shareCrash(crash) }
    }

    fun ignore(packageName: String) {
        viewModelScope.launch { settingsRepository.ignorePackage(packageName) }
    }

    suspend fun readContext(crash: CrashEventEntity): String? = withContext(Dispatchers.IO) {
        contextStore.read(crash.contextPath)
    }

    fun openChannel(type: String) {
        notificationHelper.openCrashChannel(type, packageName, separateChannels.value)
    }

    private fun commitPending() {
        val id = _pendingId.value
        val allOfThem = _pendingAll.value
        deleteJob?.cancel()
        _pendingId.value = null
        _pendingAll.value = false
        viewModelScope.launch {
            when {
                allOfThem -> deleter.deleteIds(all.value.map { it.id })
                id != null -> deleter.deleteIds(listOf(id))
            }
        }
    }
}
