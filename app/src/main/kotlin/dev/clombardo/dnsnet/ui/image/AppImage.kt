package dev.clombardo.dnsnet.ui.image

import android.content.pm.PackageManager
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.key.Keyer
import coil3.request.Options
import dev.clombardo.dnsnet.DnsNetApplication.Companion.applicationContext

class AppImageKeyer : Keyer<dev.clombardo.dnsnet.ui.App> {
    override fun key(data: dev.clombardo.dnsnet.ui.App, options: Options): String? = data.info.packageName
}

class AppImageFetcher(val pm: PackageManager, val app: dev.clombardo.dnsnet.ui.App) : Fetcher {
    override suspend fun fetch(): FetchResult? {
        val icon = app.loadIcon(pm) ?: return null
        return ImageFetchResult(
            image = icon.asImage(),
            isSampled = true,
            dataSource = DataSource.DISK,
        )
    }

    class Factory : Fetcher.Factory<dev.clombardo.dnsnet.ui.App> {
        override fun create(data: dev.clombardo.dnsnet.ui.App, options: Options, imageLoader: ImageLoader): Fetcher =
            AppImageFetcher(applicationContext.packageManager, data)
    }
}
