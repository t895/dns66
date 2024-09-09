package org.jak_linux.dns66.ui

import android.content.pm.ApplicationInfo
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import org.jak_linux.dns66.AllowListMode
import org.jak_linux.dns66.AllowListMode.Companion.toAllowListMode
import org.jak_linux.dns66.R
import org.jak_linux.dns66.ui.theme.Dns66Theme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen(
    modifier: Modifier = Modifier,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    showSystemApps: Boolean,
    onShowSystemAppsClick: () -> Unit,
    bypassSelection: AllowListMode,
    onBypassSelection: (AllowListMode) -> Unit,
    apps: List<App> = emptyList(),
    onAppClick: (App) -> Unit,
) {
    val pm = LocalContext.current.packageManager
    PullToRefreshBox(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp),
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
    ) {
        LazyColumn {
            item {
                ListSettingsContainer(
                    title = stringResource(R.string.allowlist_description),
                ) {
                    SwitchListItem(
                        title = stringResource(R.string.switch_show_system_apps),
                        checked = showSystemApps,
                        onCheckedChange = { onShowSystemAppsClick() },
                        onClick = onShowSystemAppsClick,
                    )

                    var expanded by rememberSaveable { mutableStateOf(false) }
                    val bypassOptions = stringArrayResource(R.array.allowlist_defaults)
                    Box {
                        IconListItem(
                            title = bypassOptions[bypassSelection.ordinal],
                            onClick = { expanded = true },
                            iconContent = {
                                IconButton(onClick = { expanded = true }) {
                                    Icon(Icons.Default.KeyboardArrowDown, null)
                                }
                            }
                        )

                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                        ) {
                            bypassOptions.forEachIndexed { i, option ->
                                DropdownMenuItem(
                                    text = {
                                        Text(text = option)
                                    },
                                    onClick = {
                                        onBypassSelection(i.toAllowListMode())
                                        expanded = false
                                    },
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.padding(vertical = 4.dp))
            }

            items(apps) {
                IconSwitchListItem(
                    title = it.label,
                    details = it.info.packageName,
                    checked = it.enabled,
                    onCheckedChange = { _ -> onAppClick(it) },
                    onClick = { onAppClick(it) },
                    iconContent = {
                        it.loadIcon(pm)
                        Image(
                            modifier = Modifier.fillMaxSize(),
                            painter = rememberDrawablePainter(it.getIcon()),
                            contentDescription = null,
                        )
                    }
                )
            }
        }
    }
}

@Preview
@Composable
private fun AppsScreenPreview() {
    Dns66Theme {
        AppsScreen(
            isRefreshing = false,
            onRefresh = {},
            apps = listOf(App(ApplicationInfo(), "Label", true)),
            onAppClick = {},
            showSystemApps = false,
            onShowSystemAppsClick = {},
            bypassSelection = AllowListMode.ON_VPN,
            onBypassSelection = {},
        )
    }
}
