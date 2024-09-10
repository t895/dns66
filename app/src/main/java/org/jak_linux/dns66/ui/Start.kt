package org.jak_linux.dns66.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import org.jak_linux.dns66.R
import org.jak_linux.dns66.ui.theme.FabPadding
import org.jak_linux.dns66.ui.theme.ListPadding
import org.jak_linux.dns66.ui.theme.VpnFabSize
import org.jak_linux.dns66.vpn.VpnStatus

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
            .fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(bottom = VpnFabSize + FabPadding)
                .padding(ListPadding)
        ) {
            ListSettingsContainer {
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
            VpnFab(
                modifier = Modifier.padding(bottom = FabPadding),
                status = status,
                onClick = onChangeVpnStatusClick,
            )
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
