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

class DnsNetApplication : Application() {
    companion object {
        private lateinit var application: Application
        val applicationContext: Context get() = application.applicationContext
    }

    override fun onCreate() {
        super.onCreate()
        application = this
    }
}
