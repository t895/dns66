/* Copyright (C) 2024 Charles Lombardo <clombardo169@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.t895.dnsnet

import android.util.Log

fun Any.className(): String = this::class.java.simpleName

fun Any.logd(message: String) = Log.d(this.className(), message)
fun Any.logd(message: String, error: Throwable?) =
    Log.d(this.className(), message, error)
fun Any.logv(message: String) = Log.v(this.className(), message)
fun Any.logv(message: String, error: Throwable?) =
    Log.v(this.className(), message, error)
fun Any.logi(message: String) = Log.i(this.className(), message)
fun Any.logi(message: String, error: Throwable?) =
    Log.i(this.className(), message, error)
fun Any.logw(message: String) = Log.w(this.className(), message)
fun Any.logw(message: String, error: Throwable?) =
    Log.w(this.className(), message, error)
fun Any.loge(message: String) = Log.e(this.className(), message)
fun Any.loge(message: String, error: Throwable?) =
    Log.e(this.className(), message, error)
fun Any.logwtf(message: String) = Log.wtf(this.className(), message)
fun Any.logwtf(message: String, error: Throwable?) =
    Log.wtf(this.className(), message, error)
