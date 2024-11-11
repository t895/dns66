/* Copyright (C) 2024 Charles Lombardo <clombardo169@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.t895.dnsnet

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import com.t895.dnsnet.ui.image.AppImageFetcher
import com.t895.dnsnet.ui.image.AppImageKeyer

class DnsNetApplication : Application() {
    companion object {
        private lateinit var application: Application
        val applicationContext: Context get() = application.applicationContext
    }

    override fun onCreate() {
        super.onCreate()
        application = this

        SingletonImageLoader.setSafe {
            ImageLoader.Builder(applicationContext)
                .components {
                    add(AppImageKeyer())
                    add(AppImageFetcher.Factory())
                }
                .memoryCache {
                    MemoryCache.Builder()
                        .maxSizePercent(applicationContext, 0.25)
                        .build()
                }
                .diskCache {
                    DiskCache.Builder()
                        .directory(applicationContext.cacheDir.resolve("image_cache"))
                        .maxSizePercent(0.02)
                        .build()
                }
                .build()
        }
    }
}
