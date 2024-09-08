/* Copyright (C) 2017 Julian Andres Klode <jak@jak-linux.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.jak_linux.dns66.db

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.AsyncTask
import android.util.Log
import androidx.core.app.NotificationCompat
import org.jak_linux.dns66.Configuration
import org.jak_linux.dns66.Dns66Application.Companion.applicationContext
import org.jak_linux.dns66.Host
import org.jak_linux.dns66.MainActivity
import org.jak_linux.dns66.NotificationChannels
import org.jak_linux.dns66.R
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Asynchronous task to update the database.
 * <p>
 * This spawns a thread pool fetching updating host files from
 * remote servers.
 */
open class RuleDatabaseUpdateTask(
    private val configuration: Configuration,
    notifications: Boolean,
) : AsyncTask<Void, Void, Void>() {
    companion object {
        private const val TAG = "RuleDatabaseUpdateTask"
        private const val UPDATE_NOTIFICATION_ID = 42

        @JvmField
        val lastErrors = AtomicReference<MutableList<String>>(null)
    }

    private val errors = ArrayList<String>()
    private val pending = ArrayList<String>()
    private val done = ArrayList<String>()

    private var notificationManager: NotificationManager? = null
    private var notificationBuilder: NotificationCompat.Builder? = null

    init {
        Log.d(TAG, "RuleDatabaseUpdateTask: Begin")

        if (notifications) {
            setupNotificationBuilder()
        }

        Log.d(TAG, "RuleDatabaseUpdateTask: Setup")
    }

    private fun setupNotificationBuilder() {
        notificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationBuilder =
            NotificationCompat.Builder(applicationContext, NotificationChannels.UPDATE_STATUS)
                .setContentTitle(applicationContext.getString(R.string.updating_hostfiles))
                .setSmallIcon(R.drawable.ic_refresh)
                .setProgress(configuration.hosts.items.size, 0, false)
    }

    @Deprecated("Deprecated in Java")
    override fun doInBackground(vararg params: Void?): Void? {
        Log.d(TAG, "doInBackground: begin")
        val start = System.currentTimeMillis()
        val executor = Executors.newCachedThreadPool()

        for (item in configuration.hosts.items) {
            val runnable = getCommand(item)
            if (runnable.shouldDownload()) {
                executor.execute(runnable)
            }
        }

        releaseGarbagePermissions()

        executor.shutdown()
        while (true) {
            try {
                if (executor.awaitTermination(1, TimeUnit.HOURS)) {
                    break
                }

                Log.d(TAG, "doInBackground: Waiting for completion")
            } catch (_: InterruptedException) {
            }
        }
        val end = System.currentTimeMillis()
        Log.d(TAG, "doInBackground: end after ${end - start} milliseconds")

        postExecute()

        return null
    }

    /**
     * Releases all persisted URI permissions that are no longer referenced
     */
    fun releaseGarbagePermissions() {
        val contentResolver = applicationContext.contentResolver
        for (permission in contentResolver.persistedUriPermissions) {
            if (isGarbage(permission.uri)) {
                Log.i(TAG, "releaseGarbagePermissions: Releasing permission for ${permission.uri}")
                contentResolver.releasePersistableUriPermission(
                    permission.uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } else {
                Log.v(TAG, "releaseGarbagePermissions: Keeping permission for ${permission.uri}")
            }
        }
    }

    /**
     * Returns whether URI is no longer referenced in the configuration
     *
     * @param uri URI to check
     */
    private fun isGarbage(uri: Uri): Boolean {
        for (item in configuration.hosts.items) {
            if (Uri.parse(item.location) == uri) {
                return false
            }
        }
        return true
    }

    /**
     * RuleDatabaseItemUpdateRunnable factory for unit tests
     */
    fun getCommand(item: Host): RuleDatabaseItemUpdateRunnable =
        RuleDatabaseItemUpdateRunnable(this, applicationContext, item)

    /**
     * Sets progress message.
     */
    @Synchronized
    private fun updateProgressNotification() {
        val builder = StringBuilder()
        for (p in pending) {
            if (builder.length > 0) {
                builder.append("\n")
            }
            builder.append(p)
        }

        notificationBuilder ?: return
        notificationBuilder!!.setProgress(pending.size + done.size, done.size, false)
            .setStyle(NotificationCompat.BigTextStyle().bigText(builder.toString()))
            .setContentText(applicationContext.getString(R.string.updating_n_host_files, pending.size))
        notificationManager!!.notify(UPDATE_NOTIFICATION_ID, notificationBuilder!!.build())
    }

    /**
     * Clears the notifications or updates it for viewing errors.
     */
    @Synchronized
    private fun postExecute() {
        Log.d(TAG, "postExecute: Sending notification")
        try {
            RuleDatabase.instance.initialize()
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }

        notificationBuilder ?: return
        notificationManager ?: return
        if (errors.isEmpty()) {
            notificationManager!!.cancel(UPDATE_NOTIFICATION_ID)
        } else {
            val intent = Intent(applicationContext, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }

            lastErrors.set(errors)
            val pendingIntent = PendingIntent.getActivity(
                applicationContext,
                0,
                intent,
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            )

            notificationBuilder!!
                .setProgress(0, 0, false)
                .setContentText(applicationContext.getString(R.string.could_not_update_all_hosts))
                .setSmallIcon(R.drawable.ic_warning)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
            notificationManager!!.notify(UPDATE_NOTIFICATION_ID, notificationBuilder!!.build())
        }
    }

    /**
     * Adds an error message related to the item to the log.
     *
     * @param item    The item
     * @param message Message
     */
    @Synchronized
    fun addError(item: Host, message: String) {
        Log.d(TAG, "error: ${item.title}:$message")
        errors.add("<b>${item.title}</b><br>$message")
    }

    @Synchronized
    fun addDone(item: Host) {
        Log.d(TAG, "done: ${item.title}")
        pending.remove(item.title)
        done.add(item.title)
        updateProgressNotification()
    }

    /**
     * Adds an item to the notification
     *
     * @param item The item currently being processed.
     */
    @Synchronized
    fun addBegin(item: Host) {
        pending.add(item.title)
        updateProgressNotification()
    }

    @Synchronized
    fun pendingCount(): Int = pending.size
}
