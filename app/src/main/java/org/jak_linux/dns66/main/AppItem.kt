package org.jak_linux.dns66.main

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import java.lang.ref.WeakReference

class AppItem(
    val appInfo: ApplicationInfo,
    val packageName: String,
    val label: String
) {
    private var weakIcon: WeakReference<Drawable>? = null

    fun getIcon(): Drawable? = weakIcon?.get()

    fun loadIcon(pm: PackageManager): Drawable? {
        var icon = getIcon()
        if (icon == null) {
            icon = appInfo.loadIcon(pm)
            weakIcon = WeakReference(icon)
        }
        return icon
    }
}
