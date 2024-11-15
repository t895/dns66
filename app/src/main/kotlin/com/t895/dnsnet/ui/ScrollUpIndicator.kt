/* Copyright (C) 2024 Charles Lombardo <clombardo169@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.t895.dnsnet.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.t895.dnsnet.R
import com.t895.dnsnet.ui.theme.HideScrollUpIndicator
import com.t895.dnsnet.ui.theme.ScrollUpIndicatorPadding
import com.t895.dnsnet.ui.theme.ShowScrollUpIndicator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun BoxScope.ScrollUpIndicator(
    enabled: Boolean = true,
    visible: Boolean,
    onClick: suspend CoroutineScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    val scrollUpButtonColor = MaterialTheme.colorScheme.surfaceContainerHigh
    AnimatedVisibility(
        modifier = Modifier.align(Alignment.BottomCenter),
        visible = visible,
        enter = ShowScrollUpIndicator,
        exit = HideScrollUpIndicator,
    ) {
        Box(
            modifier = Modifier
                .padding(ScrollUpIndicatorPadding)
                .systemBarsPadding()
                .size(48.dp)
                .shadow(
                    elevation = 2.dp,
                    shape = CircleShape,
                )
                .clip(CircleShape)
                .background(color = scrollUpButtonColor)
                .clickable(
                    enabled = enabled,
                    role = Role.Button,
                ) {
                    scope.launch(block = onClick)
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.ArrowUpward,
                contentDescription = stringResource(R.string.scroll_up),
            )
        }
    }
}
