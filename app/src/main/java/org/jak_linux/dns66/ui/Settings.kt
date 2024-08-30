package org.jak_linux.dns66.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jak_linux.dns66.ui.theme.AppTypography
import org.jak_linux.dns66.ui.theme.Dns66Theme

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
            style = AppTypography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        if (details.isNotEmpty()) {
            Spacer(modifier = Modifier.padding(vertical = 1.dp))
            Text(
                text = details,
                style = AppTypography.bodySmall,
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
    onCheckedChange: (Boolean) -> Unit,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .clip(CardDefaults.shape)
            .clickable(
                enabled = enabled,
                onClick = onClick,
            )
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingInfo(
            modifier = Modifier.weight(1f),
            title = title,
            details = details,
        )
        Spacer(modifier = Modifier.padding(horizontal = 4.dp))
        Checkbox(
            enabled = enabled,
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Preview
@Composable
private fun CheckboxListItemPreview() {
    Dns66Theme {
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
    onCheckedChange: (Boolean) -> Unit,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .clip(CardDefaults.shape)
            .clickable(
                enabled = enabled,
                onClick = onClick,
            )
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingInfo(
            modifier = Modifier.weight(1f),
            title = title,
            details = details,
        )
        Spacer(modifier = Modifier.padding(horizontal = 4.dp))
        Switch(
            enabled = enabled,
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Preview
@Composable
private fun SwitchListItemPreview() {
    Dns66Theme {
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
    painter: Painter,
    contentDescription: String = "",
    enabled: Boolean = true,
    checked: Boolean = false,
    title: String = "",
    details: String = "",
    onCheckedChange: (Boolean) -> Unit,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            modifier = Modifier
                .size(48.dp)
                .padding(4.dp),
            painter = painter,
            contentDescription = contentDescription,
        )
        SwitchListItem(
            enabled = enabled,
            checked = checked,
            title = title,
            details = details,
            onCheckedChange = onCheckedChange,
            onClick = onClick,
        )
    }
}

@Preview
@Composable
private fun IconSwitchListItemPreview() {
    Dns66Theme {
        var checked by remember { mutableStateOf(false) }
        IconSwitchListItem(
            modifier = Modifier.background(MaterialTheme.colorScheme.surface),
            painter = rememberVectorPainter(image = Icons.Default.Image),
            title = "Chaos Computer Club",
            details = "213.73.91.35",
            onCheckedChange = { checked = !checked },
            onClick = {},
        )
    }
}

@Composable
fun IconListItem(
    modifier: Modifier = Modifier,
    icon: Painter,
    contentDescription: String = "",
    enabled: Boolean = true,
    title: String = "",
    details: String = "",
    onClick: () -> Unit,
    onIconClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .clip(CardDefaults.shape)
            .clickable(
                enabled = enabled,
                onClick = onClick,
            )
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingInfo(
            modifier = Modifier.weight(1f),
            title = title,
            details = details,
        )
        Spacer(modifier = Modifier.padding(horizontal = 4.dp))
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .clickable(enabled = enabled, onClick = onClick)
                .minimumInteractiveComponentSize()
        ) {
            Icon(painter = icon, contentDescription = contentDescription)
        }
    }
}

@Preview
@Composable
private fun IconListItemPreview() {
    Dns66Theme {
        IconListItem(
            modifier = Modifier.background(MaterialTheme.colorScheme.surface),
            icon = rememberVectorPainter(Icons.Default.MoreVert),
            onClick = {},
            onIconClick = {},
            title = "Chaos Computer Club",
            details = "213.73.91.35",
        )
    }
}

@Composable
fun ListSettingsContainer(
    modifier: Modifier = Modifier,
    title: String = "",
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier
            .wrapContentHeight()
            .fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.Center,
        ) {
            if (title.isNotEmpty()) {
                Text(
                    text = title,
                    style = AppTypography.labelSmall
                )
                Spacer(modifier = Modifier.padding(vertical = 2.dp))
            }

            Column(
                modifier = Modifier,
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
    Dns66Theme {
        ListSettingsContainer(title = "Bypass DNS66 for marked apps") {
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
                icon = rememberVectorPainter(Icons.Default.MoreVert),
                title = "Chaos Computer Club",
                details = "213.73.91.35",
                onClick = {},
                onIconClick = {},
            )
        }
    }
}
