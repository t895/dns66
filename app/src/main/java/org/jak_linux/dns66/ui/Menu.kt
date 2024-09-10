package org.jak_linux.dns66.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuItem(
    modifier: Modifier = Modifier,
    text: String,
    painter: Painter? = null,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        modifier = modifier
            .sizeIn(minWidth = 112.dp, minHeight = 48.dp, maxWidth = 280.dp),
        text = {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (painter != null) {
                    Icon(
                        painter = painter,
                        contentDescription = null,
                    )
                    Spacer(modifier = Modifier.padding(horizontal = 8.dp))
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        },
        onClick = onClick,
        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
    )
}
