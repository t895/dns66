package org.jak_linux.dns66.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.window.core.layout.WindowWidthSizeClass
import org.jak_linux.dns66.R
import org.jak_linux.dns66.ui.theme.Dns66Theme
import org.jak_linux.dns66.ui.theme.FabPadding
import org.jak_linux.dns66.vpn.VpnStatus

@Composable
fun StartScreen(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    resumeOnStartup: Boolean,
    onResumeOnStartupClick: () -> Unit,
    watchConnection: Boolean,
    onWatchConnectionClick: () -> Unit,
    ipv6Support: Boolean,
    onIpv6SupportClick: () -> Unit,
    status: VpnStatus,
    onChangeVpnStatusClick: () -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        val layoutDirection = LocalLayoutDirection.current
        Column(
            modifier = Modifier
                .padding(start = contentPadding.calculateStartPadding(layoutDirection))
                .padding(end = contentPadding.calculateEndPadding(layoutDirection))
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.padding(top = contentPadding.calculateTopPadding()))
            ListSettingsContainer {
                SwitchListItem(
                    title = stringResource(id = R.string.switch_onboot),
                    details = stringResource(id = R.string.switch_onboot_description),
                    checked = resumeOnStartup,
                    sharedInteractionSource = remember { MutableInteractionSource() },
                    onCheckedChange = { onResumeOnStartupClick() },
                    onClick = onResumeOnStartupClick,
                )
                SwitchListItem(
                    title = stringResource(id = R.string.watchdog),
                    details = stringResource(id = R.string.watchdog_description),
                    checked = watchConnection,
                    sharedInteractionSource = remember { MutableInteractionSource() },
                    onCheckedChange = { onWatchConnectionClick() },
                    onClick = onWatchConnectionClick,
                )
                SwitchListItem(
                    title = stringResource(id = R.string.ipv6_support),
                    details = stringResource(id = R.string.ipv6_support_description),
                    checked = ipv6Support,
                    sharedInteractionSource = remember { MutableInteractionSource() },
                    onCheckedChange = { onIpv6SupportClick() },
                    onClick = onIpv6SupportClick,
                )
            }
            Spacer(Modifier.padding(bottom = contentPadding.calculateBottomPadding()))
        }

        val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = if (windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.COMPACT) {
                Alignment.BottomCenter
            } else {
                Alignment.BottomEnd
            },
        ) {
            VpnFab(
                modifier = Modifier.padding(FabPadding),
                status = status,
                onClick = onChangeVpnStatusClick,
            )
        }
    }
}

@Preview
@Composable
private fun StartScreenPreview() {
    Dns66Theme {
        StartScreen(
            modifier = Modifier.background(MaterialTheme.colorScheme.surface),
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
}
