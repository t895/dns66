/* Copyright (C) 2024 Charles Lombardo <clombardo169@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.clombardo.dnsnet.ui

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.clombardo.dnsnet.BuildConfig
import dev.clombardo.dnsnet.R
import dev.clombardo.dnsnet.ui.theme.DnsNetTheme
import dev.clombardo.dnsnet.ui.theme.ListPadding

@Composable
fun AboutText(text: String = "") {
    Text(
        modifier = Modifier.fillMaxWidth(),
        text = text,
        textAlign = TextAlign.Center,
    )
}

@Composable
fun About(
    modifier: Modifier = Modifier,
    columnPadding: PaddingValues = PaddingValues(),
    onOpenCredits: () -> Unit,
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = modifier
            .verticalScroll(scrollState)
            .padding(columnPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(id = R.drawable.icon_full),
            contentDescription = stringResource(R.string.app_name),
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
        )
        Card {
            Column(
                modifier = Modifier.padding(ListPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                AboutText(text = stringResource(id = R.string.app_shortdesc))
                AboutText(text = stringResource(id = R.string.app_version_info, BuildConfig.VERSION_NAME))
                AboutText(text = stringResource(id = R.string.info_app_copyright))
                AboutText(text = stringResource(id = R.string.dns66_credit))
                AboutText(text = stringResource(id = R.string.info_app_license))

                val uriHandler = LocalUriHandler.current
                val websiteUri = Uri.parse(stringResource(id = R.string.website))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        colors = IconButtonDefaults.filledIconButtonColors(),
                        onClick = { uriHandler.openUri(websiteUri.toString()) },
                    ) {
                        Icon(
                            imageVector = Icons.Default.Code,
                            contentDescription = stringResource(R.string.view_source_code),
                        )
                    }
                    IconButton(
                        colors = IconButtonDefaults.filledIconButtonColors(),
                        onClick = onOpenCredits,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = stringResource(R.string.credits),
                        )
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun AboutPreview() {
    DnsNetTheme {
        About(
            modifier = Modifier.background(MaterialTheme.colorScheme.surface),
            onOpenCredits = {},
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    modifier: Modifier = Modifier,
    onNavigateUp: () -> Unit,
    onOpenCredits: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    InsetScaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Text(text = stringResource(id = R.string.action_about))
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_up),
                        )
                    }
                },
                windowInsets = topAppBarInsets,
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        About(
            modifier = Modifier.padding(horizontal = 16.dp),
            columnPadding = innerPadding,
            onOpenCredits = onOpenCredits,
        )
    }
}

@Preview
@Composable
private fun AboutScreenPreview() {
    DnsNetTheme {
        AboutScreen(
            onNavigateUp = {},
            onOpenCredits = {},
        )
    }
}
