/* Copyright (C) 2025 Charles Lombardo <clombardo169@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.clombardo.dnsnet

import android.icu.number.Notation
import android.icu.number.NumberFormatter
import android.icu.number.Precision
import android.icu.text.CompactDecimalFormat
import android.icu.util.ULocale
import android.os.Build

object NumberFormatterCompat {
    fun formatCompact(value: Long): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            NumberFormatter.with()
                .notation(Notation.compactShort())
                .precision(Precision.maxSignificantDigits(3))
                .locale(ULocale.getDefault())
                .format(value)
                .toString()
        } else {
            CompactDecimalFormat.getInstance().format(value)
        }
}
