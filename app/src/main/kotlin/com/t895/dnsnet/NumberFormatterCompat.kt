package com.t895.dnsnet

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
                .precision(Precision.maxSignificantDigits(4))
                .locale(ULocale.getDefault())
                .format(value)
                .toString()
        } else {
            CompactDecimalFormat.getInstance().format(value)
        }
}
