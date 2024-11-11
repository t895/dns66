package com.t895.dnsnet.ui.image

import android.content.pm.PackageManager
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.key.Keyer
import coil3.request.Options
import com.t895.dnsnet.DnsNetApplication.Companion.applicationContext
import com.t895.dnsnet.ui.App

class AppImageKeyer : Keyer<App> {
    override fun key(data: App, options: Options): String? = data.info.packageName
}

class AppImageFetcher(val pm: PackageManager, val app: App) : Fetcher {
    override suspend fun fetch(): FetchResult? {
        app.loadIcon(pm)
        val icon = app.getIcon() ?: return null
        return ImageFetchResult(
            image = icon.asImage(),
            isSampled = true,
            dataSource = DataSource.DISK,
        )
    }

    class Factory : Fetcher.Factory<App> {
        override fun create(data: App, options: Options, imageLoader: ImageLoader): Fetcher =
            AppImageFetcher(applicationContext.packageManager, data)
    }
}
