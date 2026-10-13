package io.github.shiaho777.logsleuth.app.ui.crashes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.shiaho777.logsleuth.app.R
import io.github.shiaho777.logsleuth.app.ui.components.EmptyState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppCrashesScreen(
    onBack: () -> Unit,
    viewModel: AppCrashesViewModel = hiltViewModel(),
) {
    val crashes by viewModel.crashes.collectAsState()
    val pendingId by viewModel.pendingDelete.collectAsState()
    val pendingAll by viewModel.pendingDeleteAll.collectAsState()
    val listState = rememberLazyListState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(crashes, viewModel.highlightEventId) {
        val index = crashes.indexOfFirst { it.id == viewModel.highlightEventId }
        if (index > 0) listState.scrollToItem(index)
    }
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    val title = crashes.firstOrNull()?.appName?.takeIf { it.isNotBlank() }
        ?: viewModel.packageName
        ?: stringResource(R.string.unknown_app)

    val deletedOne = stringResource(R.string.crash_deleted)
    val deletedAll = stringResource(R.string.crash_deleted_all)
    val undoLabel = stringResource(R.string.undo)

    LaunchedEffect(pendingId, pendingAll) {
        if (pendingId == null && !pendingAll) return@LaunchedEffect
        val message = if (pendingAll) deletedAll else deletedOne
        val result = snackbar.showSnackbar(message, actionLabel = undoLabel)
        if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.close),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::requestDeleteAll) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.delete),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (crashes.isEmpty()) {
            EmptyState(
                text = stringResource(R.string.crashes_empty),
                modifier = Modifier.padding(padding),
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(crashes, key = { it.id }) { crash ->
                    CrashCard(
                        crash = crash,
                        onDelete = { viewModel.requestDelete(crash) },
                        onShare = viewModel::share,
                        onIgnore = crash.packageName?.let { pkg ->
                            {
                                viewModel.ignore(pkg)
                                scope.launch {
                                    snackbar.showSnackbar(
                                        resources.getString(R.string.crash_ignored, pkg),
                                    )
                                }
                            }
                        },
                        onOpenChannel = { viewModel.openChannel(crash.type) },
                        onReadContext = viewModel::readContext,
                        initiallyExpanded = crash.id == viewModel.highlightEventId,
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}
