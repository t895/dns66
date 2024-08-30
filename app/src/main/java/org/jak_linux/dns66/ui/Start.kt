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
import org.jak_linux.dns66.ui.theme.AppTypography

// TODO: Viewmodel hookup
@Composable
fun StartScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .fillMaxSize()
    ) {
        Column(
            modifier = modifier
                .verticalScroll(rememberScrollState())
        ) {
            ListSettingsContainer(
                modifier = Modifier.padding(12.dp),
                title = "",
            ) {
                SwitchListItem(
                    title = stringResource(id = R.string.switch_onboot),
                    details = stringResource(id = R.string.switch_onboot_description),
                    onCheckedChange = {},
                    onClick = {},
                )
                SwitchListItem(
                    title = stringResource(id = R.string.watchdog),
                    details = stringResource(id = R.string.watchdog_description),
                    onCheckedChange = {},
                    onClick = {},
                )
                SwitchListItem(
                    title = stringResource(id = R.string.ipv6_support),
                    details = stringResource(id = R.string.ipv6_support_description),
                    onCheckedChange = {},
                    onClick = {},
                )
            }
        }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            ExtendedFloatingActionButton(
                modifier = Modifier.size(width = 148.dp, height = 80.dp).padding(bottom = 16.dp),
                onClick = { /*TODO*/ },
            ) {
                // TODO: Replace with icon
                Text(
                    text = stringResource(id = R.string.action_stop),
                    style = AppTypography.titleLarge,
                )
            }
        }
    }
}

@Preview
@Composable
private fun StartScreenPreview(modifier: Modifier = Modifier) {
    StartScreen()
}
