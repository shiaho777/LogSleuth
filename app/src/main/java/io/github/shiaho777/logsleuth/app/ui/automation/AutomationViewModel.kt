package io.github.shiaho777.logsleuth.app.ui.automation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.shiaho777.logsleuth.app.core.apps.AppChoice
import io.github.shiaho777.logsleuth.app.core.apps.InstalledApps
import io.github.shiaho777.logsleuth.app.core.automation.AutomationRule
import io.github.shiaho777.logsleuth.app.data.db.AutomationRuleDao
import io.github.shiaho777.logsleuth.app.data.db.toEntity
import io.github.shiaho777.logsleuth.app.data.db.toRule
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class AutomationViewModel @Inject constructor(
    private val ruleDao: AutomationRuleDao,
    installedApps: InstalledApps,
) : ViewModel() {

    private val _apps = MutableStateFlow<List<AppChoice>>(emptyList())
    val apps: StateFlow<List<AppChoice>> = _apps.asStateFlow()

    val rules: StateFlow<List<AutomationRule>> = ruleDao.observeAll()
        .map { list -> list.map { it.toRule() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch { _apps.value = installedApps.load() }
    }

    fun save(rule: AutomationRule) {
        viewModelScope.launch {
            val entity = rule.toEntity()
            if (rule.id == 0L) ruleDao.insert(entity) else ruleDao.update(entity)
        }
    }

    fun delete(rule: AutomationRule) {
        viewModelScope.launch { ruleDao.delete(rule.toEntity()) }
    }

    fun setEnabled(rule: AutomationRule, enabled: Boolean) {
        viewModelScope.launch { ruleDao.update(rule.copy(enabled = enabled).toEntity()) }
    }
}