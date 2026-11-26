package io.github.shiaho777.logsleuth.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import io.github.shiaho777.logsleuth.app.ui.theme.SleuthGraphic
import androidx.compose.ui.unit.dp
import io.github.shiaho777.logsleuth.app.R

@Composable
private fun CenteredState(
    @DrawableRes graphic: Int,
    text: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val scroll = if (constraints.hasBoundedHeight) Modifier.verticalScroll(rememberScrollState()) else Modifier
        Column(
            modifier = Modifier.widthIn(max = 448.dp).fillMaxWidth().then(scroll)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SleuthGraphic(graphic, Modifier.size(88.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            action?.invoke()
        }
    }
}

@Composable
fun EmptyState(
    text: String,
    modifier: Modifier = Modifier,
    @DrawableRes graphic: Int = R.drawable.sleuth_search,
    action: (@Composable () -> Unit)? = null,
) {
    CenteredState(
        graphic = graphic,
        text = text,
        modifier = modifier,
        action = action,
    )
}

@Composable
fun NoAccessState(onOpenSetup: () -> Unit, modifier: Modifier = Modifier) {
    CenteredState(
        graphic = R.drawable.sleuth_access,
        text = stringResource(R.string.no_access_title),
        modifier = modifier,
    ) {
        Button(onClick = onOpenSetup) {
            Text(stringResource(R.string.no_access_action))
        }
    }
}
