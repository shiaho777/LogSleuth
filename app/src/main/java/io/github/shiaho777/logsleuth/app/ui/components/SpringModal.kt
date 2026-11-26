package io.github.shiaho777.logsleuth.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BasicAlertDialogOverride
import androidx.compose.material3.BasicAlertDialogOverrideScope
import androidx.compose.material3.ExperimentalMaterial3ComponentOverrideApi
import androidx.compose.material3.LocalBasicAlertDialogOverride
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import io.github.shiaho777.logsleuth.app.ui.theme.SleuthMotion
import kotlinx.coroutines.launch

internal typealias CloseWithMotion = (() -> Unit) -> Unit

private class ModalExitState {
    var action by mutableStateOf<(() -> Unit)?>(null)
        private set
    private var completed = false

    fun request(action: () -> Unit): Boolean {
        if (this.action != null || completed) return false
        this.action = action
        return true
    }

    fun finish(onDismiss: () -> Unit) {
        if (completed) return
        completed = true
        (action ?: onDismiss)()
    }

    fun clearPending() {
        if (!completed) action = null
    }
}

@OptIn(ExperimentalMaterial3ComponentOverrideApi::class)
@Composable
internal fun SpringAlertDialog(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable (CloseWithMotion) -> Unit,
    confirmButton: @Composable (CloseWithMotion) -> Unit,
    dismissButton: (@Composable (CloseWithMotion) -> Unit)? = null,
) {
    val exit = remember { ModalExitState() }
    val windowOverride = remember(exit) { SpringAlertWindow(exit) }
    val close: CloseWithMotion = { exit.request(it) }
    CompositionLocalProvider(LocalBasicAlertDialogOverride provides windowOverride) {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = title,
            text = { text(close) },
            confirmButton = { confirmButton(close) },
            dismissButton = dismissButton?.let { button -> { button(close) } },
        )
    }
}

@OptIn(ExperimentalMaterial3ComponentOverrideApi::class)
private class SpringAlertWindow(private val exit: ModalExitState) : BasicAlertDialogOverride {
    @Composable
    override fun BasicAlertDialogOverrideScope.BasicAlertDialog() {
        val dialogTitle = stringResource(androidx.compose.material3.R.string.m3c_dialog)
        SpringDialogWindow(exit, onDismissRequest, properties) {
            Box(modifier.sizeIn(minWidth = 280.dp, maxWidth = 560.dp).semantics { paneTitle = dialogTitle }) {
                content()
            }
        }
    }
}

@Composable
internal fun SpringDialog(
    onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(),
    showAtEnd: Boolean = false,
    content: @Composable (CloseWithMotion) -> Unit,
) {
    val exit = remember { ModalExitState() }
    SpringDialogWindow(exit, onDismissRequest, properties, showAtEnd) {
        content { exit.request(it) }
    }
}

@Composable
private fun SpringDialogWindow(
    exit: ModalExitState,
    onDismiss: () -> Unit,
    properties: DialogProperties,
    showAtEnd: Boolean = false,
    content: @Composable () -> Unit,
) {
    val progress = remember { Animatable(0f) }
    val visible = exit.action == null
    LaunchedEffect(visible) {
        progress.animateTo(if (visible) 1f else 0f, SleuthMotion.spatial())
        if (!visible) exit.finish(onDismiss)
    }

    Dialog(onDismissRequest = { exit.request(onDismiss) }, properties = properties) {
        SpringDialogDim(progress.asState())
        if (showAtEnd) {
            val direction = if (LocalLayoutDirection.current == LayoutDirection.Ltr) 1 else -1
            BoxWithConstraints(Modifier.fillMaxSize().clipToBounds(), contentAlignment = Alignment.CenterEnd) {
                Box(Modifier.fillMaxSize().clickable(interactionSource = null, indication = null) { exit.request(onDismiss) })
                Box(
                    Modifier.width((maxWidth * 0.58f).coerceIn(360.dp, 560.dp)).fillMaxHeight()
                        .graphicsLayer { translationX = direction * size.width * (1f - progress.value) },
                ) {
                    content()
                }
            }
        } else {
            Box(
                Modifier.graphicsLayer {
                    alpha = progress.value
                    scaleX = 0.94f + 0.06f * progress.value
                    scaleY = scaleX
                },
            ) {
                content()
            }
        }
    }
}

@Composable
private fun SpringDialogDim(dim: State<Float>) {
    val window = (LocalView.current.parent as DialogWindowProvider).window
    val originalDim = remember(window) { window.attributes.dimAmount }
    SideEffect { window.setWindowAnimations(0) }
    LaunchedEffect(window, dim) {
        snapshotFlow { dim.value.coerceIn(0f, 1f) }.collect { progress ->
            window.setDimAmount(originalDim * progress)
        }
    }
}

@Composable
internal fun SpringBottomSheet(
    onDismissRequest: () -> Unit,
    skipPartiallyExpanded: Boolean = false,
    content: @Composable ColumnScope.(CloseWithMotion) -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = skipPartiallyExpanded)
    val scope = rememberCoroutineScope()
    val exit = remember { ModalExitState() }
    ModalBottomSheet(onDismissRequest = { exit.finish(onDismissRequest) }, sheetState = sheet) {
        content { action ->
            if (exit.request(action)) {
                scope.launch {
                    try {
                        sheet.hide()
                        if (!sheet.isVisible) exit.finish(onDismissRequest)
                    } finally {
                        exit.clearPending()
                    }
                }
            }
        }
    }
}
