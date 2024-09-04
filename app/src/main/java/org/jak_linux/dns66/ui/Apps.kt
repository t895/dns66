package org.jak_linux.dns66.ui

import android.content.pm.ApplicationInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import org.jak_linux.dns66.R
import org.jak_linux.dns66.main.AppItem
import org.jak_linux.dns66.ui.theme.Dns66Theme

@Composable
fun AppsScreen(
    modifier: Modifier = Modifier,
    showSystemApps: Boolean = false,
    onShowSystemAppsClick: () -> Unit,
    bypassSelection: Int = 0,
    onBypassSelection: (Int) -> Unit,
    apps: List<AppItem> = emptyList(),
    onAppClick: (AppItem) -> Unit,
) {
    LazyColumn(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp)
    ) {
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
                        title = bypassOptions[bypassSelection],
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
                                    onBypassSelection(i)
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
                details = it.packageName,
                onCheckedChange = { _ -> onAppClick(it) },
                onClick = { onAppClick(it) },
                iconContent = {
                    AsyncImage(
                        modifier = Modifier.fillMaxSize(),
                        model = it.getIcon(),
                        contentDescription = null,
                        placeholder = rememberVectorPainter(Icons.Default.Android),
                    )
                }
            )
        }
    }
}

@Preview
@Composable
private fun AppsScreenPreview(modifier: Modifier = Modifier) {
    Dns66Theme {
        AppsScreen(
            apps = listOf(AppItem(ApplicationInfo(), "Package", "Label")),
            onAppClick = {},
            onShowSystemAppsClick = {},
            onBypassSelection = {},
        )
    }
}
