package io.github.shiaho777.logsleuth.app.ui.setup

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.shiaho777.logsleuth.app.R
import io.github.shiaho777.logsleuth.app.core.importer.ExampleReportStore
import io.github.shiaho777.logsleuth.app.core.importer.LogImporter
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class WelcomeState(
    val opening: Boolean = false,
    val openedSessionId: Long? = null,
    val error: Int? = null,
)

@HiltViewModel
class WelcomeViewModel @Inject constructor(
    private val importer: LogImporter,
    private val example: ExampleReportStore,
) : ViewModel() {
    private val _state = MutableStateFlow(WelcomeState())
    val state = _state.asStateFlow()

    fun openExample() = open(R.string.example_report_error) { example.open() }

    fun import(uri: Uri) = open(R.string.import_failed) { importer.import(uri).getOrThrow() }

    fun clearResult() {
        _state.value = WelcomeState()
    }

    private fun open(error: Int, load: suspend () -> Long) {
        if (_state.value.opening || _state.value.openedSessionId != null) return
        _state.value = WelcomeState(opening = true)
        viewModelScope.launch {
            _state.value = try {
                WelcomeState(openedSessionId = load())
            } catch (failure: CancellationException) {
                throw failure
            } catch (_: Exception) {
                WelcomeState(error = error)
            }
        }
    }
}
