/* Copyright (C) 2024 Charles Lombardo <clombardo169@gmail.com>
 *
 * Derived from DNS66:
 * Copyright (C) 2016 - 2019 Julian Andres Klode <jak@jak-linux.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.t895.dnsnet

import android.content.Context
import android.net.Uri
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.system.StructPollfd
import android.widget.Toast
import com.t895.dnsnet.DnsNetApplication.Companion.applicationContext
import java.io.Closeable
import java.io.File
import java.io.FileDescriptor
import java.io.FileNotFoundException
import java.io.FileReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.UnsupportedEncodingException
import java.net.URLEncoder

/**
 * Utility object for working with files.
 */
object FileHelper {
    /**
     * Try open the file with [Context.openFileInput]
     */
    @Throws(IOException::class)
    fun openRead(filename: String?): InputStream? =
        try {
            applicationContext.openFileInput(filename)
        } catch (e: FileNotFoundException) {
            null
        }

    /**
     * Write to the given file in the private files dir, first renaming an old one to .bak
     *
     * @param filename A filename as for [Context.openFileOutput]
     * @return See [Context.openFileOutput]
     * @throws IOException See @{link {@link Context#openFileOutput(String, int)}}
     */
    @Throws(IOException::class)
    fun openWrite(filename: String): OutputStream? {
        val out = applicationContext.getFileStreamPath(filename)

        // Create backup
        out.renameTo(applicationContext.getFileStreamPath("$filename.bak"))
        return applicationContext.openFileOutput(filename, Context.MODE_PRIVATE)
    }

    @Throws(IOException::class)
    private fun readConfigFile(name: String): Configuration {
        val stream = openRead(name)
        return if (stream != null) {
            Configuration.read(InputStreamReader(stream))
        } else {
            logd("Config file not found, creating new file.")
            Configuration()
        }
    }

    fun loadCurrentSettings(): Configuration =
        try {
            readConfigFile("settings.json")
        } catch (e: Exception) {
            Toast.makeText(
                applicationContext,
                applicationContext.getString(R.string.cannot_read_config, e.localizedMessage),
                Toast.LENGTH_LONG
            ).show()
            loadPreviousSettings(applicationContext)
        }

    fun loadPreviousSettings(context: Context): Configuration =
        try {
            readConfigFile("settings.json.bak")
        } catch (e: Exception) {
            Toast.makeText(
                context,
                context.getString(R.string.cannot_restore_previous_config, e.localizedMessage),
                Toast.LENGTH_LONG
            ).show()
            loadDefaultSettings()
        }

    fun loadDefaultSettings(): Configuration = Configuration.read(null)

    fun writeSettings(config: Configuration) {
        logd("writeSettings: Writing the settings file")
        try {
            OutputStreamWriter(openWrite("settings.json")).use {
                config.write(it)
            }
        } catch (e: IOException) {
            Toast.makeText(
                applicationContext,
                applicationContext.getString(R.string.cannot_write_config, e.localizedMessage),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    /**
     * Returns a file where the item should be downloaded to.
     *
     * @param item    A configuration item.
     * @return File or null, if that item is not downloadable.
     */
    fun getItemFile(item: Host): File? =
        if (item.isDownloadable()) {
            try {
                File(
                    applicationContext.getExternalFilesDir(null),
                    URLEncoder.encode(item.location, "UTF-8"),
                )
            } catch (e: UnsupportedEncodingException) {
                e.printStackTrace()
                null
            }
        } else {
            null
        }

    @Throws(FileNotFoundException::class)
    fun openItemFile(host: Host): InputStreamReader? {
        return if (host.location.startsWith("content://")) {
            try {
                InputStreamReader(applicationContext.contentResolver.openInputStream(Uri.parse(host.location)))
            } catch (e: SecurityException) {
                logd("openItemFile: Cannot open", e)
                throw FileNotFoundException(e.message)
            }
        } else {
            val itemFile = getItemFile(host) ?: return null
            if (host.isDownloadable()) {
                InputStreamReader(
                    SingleWriterMultipleReaderFile(itemFile).openRead()
                )
            } else {
                FileReader(itemFile)
            }
        }
    }

    /**
     * Wrapper around [Os.poll] that automatically restarts on EINTR
     * While post-Lollipop devices handle that themselves, we need to do this for Lollipop.
     *
     * @param fds     Descriptors and events to wait on
     * @param timeout Timeout. Should be -1 for infinite, as we do not lower the timeout when
     *                retrying due to an interrupt
     * @return The number of fds that have events
     * @throws ErrnoException See [Os.poll]
     */
    @Throws(ErrnoException::class, InterruptedException::class)
    fun poll(fds: Array<StructPollfd?>, timeout: Int): Int {
        while (true) {
            if (Thread.interrupted()) {
                throw InterruptedException()
            }

            return try {
                Os.poll(fds, timeout)
            } catch (e: ErrnoException) {
                if (e.errno == OsConstants.EINTR) {
                    continue
                }
                throw e
            }
        }
    }

    fun closeOrWarn(fd: FileDescriptor?, message: String): FileDescriptor? {
        try {
            if (fd != null) {
                Os.close(fd)
            }
        } catch (e: ErrnoException) {
            loge("closeOrWarn: $message", e)
        }

        // Always return null
        return null
    }

    fun <T : Closeable?> closeOrWarn(fd: T, message: String): FileDescriptor? {
        try {
            fd?.close()
        } catch (e: java.lang.Exception) {
            loge("closeOrWarn: $message", e)
        }

        // Always return null
        return null
    }
}
