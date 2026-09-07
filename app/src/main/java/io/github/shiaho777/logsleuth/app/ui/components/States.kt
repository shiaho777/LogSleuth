package io.github.shiaho777.logsleuth.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.shiaho777.logsleuth.app.R

/** Tonal-circle icon + message, centered. The shared "nothing here yet" look. */
@Composable
private fun CenteredState(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.size(96.dp),
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.padding(26.dp),
                tint = MaterialTheme.colorScheme.outline,
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            Spacer(Modifier.height(24.dp))
            action()
        }
    }
}

@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    CenteredState(icon = Icons.Outlined.Article, text = text, modifier = modifier)
}

@Composable
fun NoAccessState(onOpenSetup: () -> Unit, modifier: Modifier = Modifier) {
    CenteredState(
        icon = Icons.Outlined.Lock,
        text = stringResource(R.string.no_access_title) + "\n\n" +
            stringResource(R.string.no_access_desc),
        modifier = modifier,
    ) {
        Button(onClick = onOpenSetup) {
            Text(stringResource(R.string.no_access_action))
        }
    }
}
