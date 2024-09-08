package org.jak_linux.dns66.ui

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jak_linux.dns66.BuildConfig
import org.jak_linux.dns66.R
import org.jak_linux.dns66.ui.theme.Dns66Theme

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
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = modifier
            .verticalScroll(scrollState)
            .padding(columnPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Image(painter = painterResource(id = R.mipmap.app_icon_large), contentDescription = null)
        AboutText(text = stringResource(id = R.string.app_shortdesc))
        AboutText(text = stringResource(id = R.string.app_version_info, BuildConfig.VERSION_NAME))
        AboutText(text = stringResource(id = R.string.info_app_copyright))
        AboutText(text = stringResource(id = R.string.info_app_license))

        val uriHandler = LocalUriHandler.current
        val websiteUri = Uri.parse(stringResource(id = R.string.website))
        IconButton(onClick = { uriHandler.openUri(websiteUri.toString()) }) {
            Icon(imageVector = Icons.Rounded.Public, contentDescription = null)
        }
    }
}

@Preview
@Composable
private fun AboutPreview() {
    Dns66Theme {
        About(Modifier.background(MaterialTheme.colorScheme.surface))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    modifier: Modifier = Modifier,
    onNavigateUp: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
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
                            contentDescription = null,
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        About(
            modifier = Modifier.padding(horizontal = 16.dp),
            columnPadding = innerPadding,
        )
    }
}

@Preview
@Composable
private fun AboutScreenPreview(modifier: Modifier = Modifier) {
    Dns66Theme {
        AboutScreen(onNavigateUp = {})
    }
}
