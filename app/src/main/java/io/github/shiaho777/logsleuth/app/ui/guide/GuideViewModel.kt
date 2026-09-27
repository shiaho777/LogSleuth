package io.github.shiaho777.logsleuth.app.ui.guide

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.shiaho777.logsleuth.app.data.prefs.SettingsRepository
import javax.inject.Inject

@HiltViewModel
class GuideViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    /**
     * Skip and finish both count — the tour should never re-show itself.
     * Repo-level write: finishing pops this screen, which clears the
     * ViewModel and would cancel a viewModelScope write before it lands.
     */
    fun markCompleted() {
        settingsRepository.setGuideCompletedAsync(true)
    }
}
