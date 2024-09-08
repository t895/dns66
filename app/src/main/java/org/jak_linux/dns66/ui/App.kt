package org.jak_linux.dns66.ui

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
