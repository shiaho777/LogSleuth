package io.github.shiaho777.logsleuth.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.shiaho777.logsleuth.app.R
import io.github.shiaho777.logsleuth.app.data.prefs.AppLocales

/**
 * Card content for choosing the app language: follow system, English,
 * or 简体中文. Language names are shown in their own language (standard
 * i18n practice); only the "system" option is translated.
 */
@Composable
fun LanguagePicker(
    current: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.settings_language),
            style = MaterialTheme.typography.titleSmall,
        )
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            val labels = listOf(
                AppLocales.SYSTEM to stringResource(R.string.language_system),
                AppLocales.EN to "English",
                AppLocales.ZH_CN to "简体中文",
            )
            labels.forEachIndexed { index, (tag, label) ->
                SegmentedButton(
                    selected = current == tag,
                    onClick = { onSelect(tag) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = labels.size),
                ) { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}
