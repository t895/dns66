/* Copyright (C) 2024 Charles Lombardo <clombardo169@gmail.com>
 *
 * Derived from DNS66:
 * Copyright (C) 2016-2019 Julian Andres Klode <jak@jak-linux.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.t895.dnsnet.ui

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import java.lang.ref.WeakReference

data class App(
    val info: ApplicationInfo,
    val label: String,
    var enabled: Boolean,
) {
    private var weakIcon: WeakReference<Drawable>? = null

    fun getIcon(): Drawable? = weakIcon?.get()

    fun loadIcon(pm: PackageManager): Drawable? {
        info.packageName
        var icon = getIcon()
        if (icon == null) {
            icon = info.loadIcon(pm)
            weakIcon = WeakReference(icon)
        }
        return icon
    }
}
