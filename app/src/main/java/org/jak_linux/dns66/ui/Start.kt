package org.jak_linux.dns66.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jak_linux.dns66.R
import org.jak_linux.dns66.vpn.VpnStatus

// TODO: Viewmodel hookup
@Composable
fun StartScreen(
    modifier: Modifier = Modifier,
    resumeOnStartup: Boolean,
    onResumeOnStartupClick: () -> Unit,
    watchConnection: Boolean,
    onWatchConnectionClick: () -> Unit,
    ipv6Support: Boolean,
    onIpv6SupportClick: () -> Unit,
    status: VpnStatus,
    onChangeVpnStatusClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
        ) {
            ListSettingsContainer(
                modifier = Modifier.padding(12.dp),
                title = "",
            ) {
                SwitchListItem(
                    title = stringResource(id = R.string.switch_onboot),
                    details = stringResource(id = R.string.switch_onboot_description),
                    checked = resumeOnStartup,
                    onCheckedChange = { onResumeOnStartupClick() },
                    onClick = onResumeOnStartupClick,
                )
                SwitchListItem(
                    title = stringResource(id = R.string.watchdog),
                    details = stringResource(id = R.string.watchdog_description),
                    checked = watchConnection,
                    onCheckedChange = { onWatchConnectionClick() },
                    onClick = onWatchConnectionClick,
                )
                SwitchListItem(
                    title = stringResource(id = R.string.ipv6_support),
                    details = stringResource(id = R.string.ipv6_support_description),
                    checked = ipv6Support,
                    onCheckedChange = { onIpv6SupportClick() },
                    onClick = onIpv6SupportClick,
                )
            }
        }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            ExtendedFloatingActionButton(
                modifier = Modifier.size(width = 148.dp, height = 80.dp).padding(bottom = 16.dp),
                onClick = onChangeVpnStatusClick,
            ) {
                Text(
                    text = stringResource(id = status.toTextId()),
                    style = MaterialTheme.typography.titleLarge,
                )
            }
        }
    }
}

@Preview
@Composable
private fun StartScreenPreview(modifier: Modifier = Modifier) {
    StartScreen(
        resumeOnStartup = false,
        onResumeOnStartupClick = {},
        watchConnection = false,
        onWatchConnectionClick = {},
        ipv6Support = false,
        onIpv6SupportClick = {},
        status = VpnStatus.STOPPED,
        onChangeVpnStatusClick = {},
    )
}
