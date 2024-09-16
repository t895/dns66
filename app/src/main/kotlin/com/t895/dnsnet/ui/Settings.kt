/* Copyright (C) 2024 Charles Lombardo <clombardo169@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.t895.dnsnet.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.t895.dnsnet.ui.theme.DnsNetTheme

@Composable
fun SettingInfo(
    modifier: Modifier = Modifier,
    title: String,
    details: String = "",
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        if (details.isNotEmpty()) {
            Spacer(modifier = Modifier.padding(vertical = 1.dp))
            Text(
                text = details,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun ContentSetting(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    title: String = "",
    details: String = "",
    onBodyClick: () -> Unit,
    interactionSource: MutableInteractionSource? = remember { MutableInteractionSource() },
    startContent: @Composable (BoxScope.() -> Unit)? = null,
    endContent: @Composable (BoxScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .clip(CardDefaults.shape)
            .clickable(
                enabled = enabled,
                onClick = onBodyClick,
                interactionSource = interactionSource,
                indication = ripple(),
            )
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (startContent != null) {
            Box(
                contentAlignment = Alignment.Center,
                content = startContent,
            )
            Spacer(modifier = Modifier.padding(horizontal = 4.dp))
        }

        SettingInfo(
            modifier = Modifier.weight(1f),
            title = title,
            details = details,
        )

        if (endContent != null) {
            Spacer(modifier = Modifier.padding(horizontal = 4.dp))
            Box(
                contentAlignment = Alignment.Center,
                content = endContent,
            )
        }
    }
}

@Composable
fun CheckboxListItem(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    checked: Boolean = false,
    title: String = "",
    details: String = "",
    sharedInteractionSource: MutableInteractionSource? = null,
    onCheckedChange: (Boolean) -> Unit,
    onClick: () -> Unit,
) {
    ContentSetting(
        modifier = modifier,
        enabled = enabled,
        title = title,
        details = details,
        onBodyClick = onClick,
        interactionSource = sharedInteractionSource,
        endContent = {
            Checkbox(
                enabled = enabled,
                checked = checked,
                onCheckedChange = onCheckedChange,
                interactionSource = sharedInteractionSource,
            )
        },
    )
}

@Preview
@Composable
private fun CheckboxListItemPreview() {
    DnsNetTheme {
        var checked by remember { mutableStateOf(false) }
        CheckboxListItem(
            modifier = Modifier.background(MaterialTheme.colorScheme.surface),
            checked = checked,
            title = "Chaos Computer Club",
            details = "213.73.91.35",
            onCheckedChange = { checked = !checked },
            onClick = {},
        )
    }
}

@Composable
fun SwitchListItem(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    checked: Boolean = false,
    title: String = "",
    details: String = "",
    sharedInteractionSource: MutableInteractionSource? = null,
    onCheckedChange: (Boolean) -> Unit,
    onClick: () -> Unit,
) {
    ContentSetting(
        modifier = modifier,
        enabled = enabled,
        title = title,
        details = details,
        onBodyClick = onClick,
        interactionSource = sharedInteractionSource,
        endContent = {
            Switch(
                enabled = enabled,
                checked = checked,
                onCheckedChange = onCheckedChange,
                interactionSource = sharedInteractionSource,
            )
        },
    )
}

@Preview
@Composable
private fun SwitchListItemPreview() {
    DnsNetTheme {
        var checked by remember { mutableStateOf(false) }
        SwitchListItem(
            modifier = Modifier.background(MaterialTheme.colorScheme.surface),
            checked = checked,
            title = "Chaos Computer Club",
            details = "213.73.91.35",
            onCheckedChange = { checked = !checked },
            onClick = {},
        )
    }
}

@Composable
fun IconSwitchListItem(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    checked: Boolean = false,
    title: String = "",
    details: String = "",
    sharedInteractionSource: MutableInteractionSource? = null,
    onCheckedChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    iconContent: @Composable BoxScope.() -> Unit,
) {
    ContentSetting(
        modifier = modifier,
        enabled = enabled,
        title = title,
        details = details,
        onBodyClick = onClick,
        interactionSource = sharedInteractionSource,
        startContent = {
            Box(
                modifier = Modifier
                    .size(56.dp),
                contentAlignment = Alignment.Center,
                content = iconContent,
            )
        },
        endContent = {
            Switch(
                enabled = enabled,
                checked = checked,
                onCheckedChange = onCheckedChange,
                interactionSource = sharedInteractionSource,
            )
        },
    )
}

@Preview
@Composable
private fun IconSwitchListItemPreview() {
    DnsNetTheme {
        var checked by remember { mutableStateOf(false) }
        IconSwitchListItem(
            modifier = Modifier.background(MaterialTheme.colorScheme.surface),
            title = "Chaos Computer Club",
            details = "213.73.91.35",
            onCheckedChange = { checked = !checked },
            onClick = {},
            iconContent = { Icon(Icons.Default.Check, "") }
        )
    }
}

@Composable
fun IconListItem(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    title: String = "",
    details: String = "",
    onClick: () -> Unit,
    interactionSource: MutableInteractionSource? = remember { MutableInteractionSource() },
    iconContent: @Composable BoxScope.() -> Unit,
) {
    ContentSetting(
        modifier = modifier,
        enabled = enabled,
        title = title,
        details = details,
        onBodyClick = onClick,
        interactionSource = interactionSource,
        endContent = iconContent,
    )
}

@Preview
@Composable
private fun IconListItemPreview() {
    DnsNetTheme {
        IconListItem(
            modifier = Modifier.background(MaterialTheme.colorScheme.surface),
            onClick = {},
            title = "Chaos Computer Club",
            details = "213.73.91.35",
            iconContent = {
                IconButton(onClick = {}) {
                    Icon(Icons.Default.MoreVert, null)
                }
            }
        )
    }
}

@Composable
fun ExpandableOptionsItem(
    modifier: Modifier = Modifier,
    expanded: Boolean = false,
    enabled: Boolean = true,
    title: String = "",
    details: String = "",
    sharedInteractionSource: MutableInteractionSource? = null,
    onExpandClick: () -> Unit,
    options: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier) {
        IconListItem(
            title = title,
            details = details,
            enabled = enabled,
            onClick = onExpandClick,
            interactionSource = sharedInteractionSource,
        ) {
            IconButton(
                enabled = enabled,
                onClick = onExpandClick,
                interactionSource = sharedInteractionSource,
            ) {
                val iconRotation by animateFloatAsState(
                    targetValue = if (expanded) 0f else 90f,
                    label = "iconRotation",
                )
                Icon(
                    modifier = Modifier.rotate(iconRotation),
                    painter = rememberVectorPainter(Icons.Default.KeyboardArrowDown),
                    contentDescription = null,
                )
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(expandFrom = Alignment.Top),
            exit = shrinkVertically(shrinkTowards = Alignment.Top),
        ) {
            Column(
                modifier = Modifier
                    .padding(bottom = 16.dp)
                    .padding(horizontal = 8.dp),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = options,
            )
        }
    }
}

@Preview
@Composable
private fun ExpandableOptionsItemPreview() {
    DnsNetTheme {
        Box(Modifier.background(MaterialTheme.colorScheme.surface)) {
            var expanded by remember { mutableStateOf(false) }
            ExpandableOptionsItem(
                expanded = expanded,
                title = "Title",
                details = "Details",
                onExpandClick = { expanded = !expanded },
            ) {
                SettingInfo(title = "Option1")
                SettingInfo(title = "Option2")
                SettingInfo(title = "Option3")
            }
        }
    }
}

@Composable
fun RadioListItem(
    modifier: Modifier = Modifier,
    selected: Boolean,
    enabled: Boolean = true,
    title: String = "",
    details: String = "",
    sharedInteractionSource: MutableInteractionSource? = null,
    onClick: () -> Unit,
    onButtonClick: () -> Unit,
) {
    ContentSetting(
        modifier = modifier,
        enabled = enabled,
        title = title,
        details = details,
        onBodyClick = onClick,
        interactionSource = sharedInteractionSource,
        endContent = {
            RadioButton(
                selected = selected,
                onClick = onButtonClick,
                interactionSource = sharedInteractionSource,
            )
        },
    )
}

@Preview
@Composable
private fun RadioListItemPreview() {
    DnsNetTheme {
        Box(Modifier.background(MaterialTheme.colorScheme.surface)) {
            var selected by remember { mutableStateOf(false) }
            RadioListItem(
                title = "Title",
                details = "Details",
                selected = selected,
                onClick = { selected = !selected },
                onButtonClick = { selected = !selected },
            )
        }
    }
}

@Composable
fun ListSettingsContainer(
    modifier: Modifier = Modifier,
    title: String = "",
    content: @Composable ColumnScope.() -> Unit,
) {
    Column {
        if (title.isNotEmpty()) {
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp),
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.padding(vertical = 4.dp))
        }

        Card(
            modifier = modifier
                .wrapContentHeight()
                .fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.Start,
                content = content,
            )
        }
    }
}

@Preview
@Composable
private fun ListSettingsContainerPreview() {
    DnsNetTheme {
        ListSettingsContainer(title = "Bypass DNSNet for marked apps") {
            var checked by remember { mutableStateOf(false) }
            SwitchListItem(
                checked = checked,
                title = "Chaos Computer Club",
                details = "213.73.91.35",
                onCheckedChange = { checked = !checked },
                onClick = {},
            )
            var checked2 by remember { mutableStateOf(false) }
            CheckboxListItem(
                checked = checked2,
                title = "Chaos Computer Club",
                details = "213.73.91.35",
                onCheckedChange = { checked2 = !checked2 },
                onClick = {},
            )
            IconListItem(
                title = "Chaos Computer Club",
                details = "213.73.91.35",
                onClick = {},
                iconContent = {
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.MoreVert, null)
                    }
                },
            )

            var expanded by remember { mutableStateOf(false) }
            ExpandableOptionsItem(
                expanded = expanded,
                title = "Expandable",
                details = "Details",
                onExpandClick = { expanded = !expanded },
            ) {
                RadioListItem(
                    selected = false,
                    title = "Option1",
                    onClick = {},
                    onButtonClick = {}
                )
                RadioListItem(
                    selected = false,
                    title = "Option2",
                    onClick = {},
                    onButtonClick = {}
                )
                RadioListItem(
                    selected = false,
                    title = "Option3",
                    onClick = {},
                    onButtonClick = {}
                )
            }
        }
    }
}
