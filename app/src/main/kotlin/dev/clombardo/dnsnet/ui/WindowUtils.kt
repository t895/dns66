package dev.clombardo.dnsnet.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.runtime.Composable

val navigationSuiteScaffoldTopAppBarInsets: WindowInsets
    @Composable
    get() = WindowInsets.displayCutout.only(WindowInsetsSides.End)
        .add(WindowInsets.systemBars.only(WindowInsetsSides.Top))
        .add(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal))

val topAppBarInsets: WindowInsets
    @Composable
    get() = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal)
        .add(WindowInsets.systemBars.only(WindowInsetsSides.Top))
        .add(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))

val navigationSuiteScaffoldContentInsets: WindowInsets
    @Composable
    get() = WindowInsets.systemBars
        .add(WindowInsets.displayCutout.only(WindowInsetsSides.End))

val scaffoldContentInsets: WindowInsets
    @Composable
    get() = WindowInsets.systemBars
        .add(WindowInsets.displayCutout.only(WindowInsetsSides.Start))
        .add(WindowInsets.displayCutout.only(WindowInsetsSides.End))
