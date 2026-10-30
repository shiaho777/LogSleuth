package io.github.shiaho777.logsleuth.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import io.github.shiaho777.logsleuth.app.R

/** The QQ group number, shown next to the invite link. */
internal const val QQ_GROUP_NUMBER = "1127256026"

/** Invite link for the QQ group — opens QQ's join page. */
internal const val QQ_GROUP_INVITE_URL = "https://qm.qq.com/q/Gzj7CpirAs"

/**
 * QQ group card. It sits directly above the GitHub card at the bottom of
 * Settings, so the community entry is the last thing before the repository
 * link.
 */
@Composable
fun CommunityCard(onJoinGroup: () -> Unit) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Groups,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.settings_qq_group),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        QQ_GROUP_NUMBER,
                        style = MaterialTheme.typography.bodyLarge,
                        textDecoration = TextDecoration.None,
                    )
                }
            }
            Text(
                stringResource(R.string.settings_qq_group_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = onJoinGroup, modifier = Modifier.fillMaxWidth()) {
                Icon(
                    imageVector = Icons.Filled.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.settings_qq_group_join))
            }
        }
    }
}
