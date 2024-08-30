package org.jak_linux.dns66

import android.content.Intent
import android.os.Build
import android.os.Parcelable

@Suppress("DEPRECATION")
inline fun <reified T> Intent.getParcel(name: String): T? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(name, T::class.java)
    } else {
        getParcelableExtra<Parcelable>(name) as T
    }
