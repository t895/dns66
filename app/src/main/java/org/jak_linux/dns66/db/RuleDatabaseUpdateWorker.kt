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
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.jak_linux.dns66.FileHelper
import org.jak_linux.dns66.Host
import org.jak_linux.dns66.MainActivity
import org.jak_linux.dns66.NotificationChannels
import org.jak_linux.dns66.R

class RuleDatabaseUpdateWorker(
    val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    companion object {
        private const val TAG = "RuleDatabaseUpdateWorker"
        private const val UPDATE_NOTIFICATION_ID = 42

        const val PERIODIC_TAG = "RuleDatabaseUpdatePeriodicWorker"

        var lastErrors by atomic<MutableList<String>?>(null)
    }

    private val errors = ArrayList<String>()
    private val pending = ArrayList<String>()
    private val done = ArrayList<String>()

    private lateinit var notificationManager: NotificationManager
    private lateinit var notificationBuilder: NotificationCompat.Builder

    private val config = FileHelper.loadCurrentSettings()

    init {
        Log.d(TAG, "Begin")
        setupNotificationBuilder()
        Log.d(TAG, "Setup")
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.d(TAG, "doWork: Begin")
        val start = System.currentTimeMillis()
        val jobs = mutableListOf<Deferred<Unit>>()
        config.hosts.items.forEach {
            val update = RuleDatabaseItemUpdate(context, this@RuleDatabaseUpdateWorker, it)
            if (update.shouldDownload()) {
                val job = async { update.run() }
                job.start()
                jobs.add(job)
            }
        }

        releaseGarbagePermissions()

        try {
            withTimeout(3600000) {
                jobs.awaitAll()
            }
        } catch (_: TimeoutCancellationException) {
        }
        val end = System.currentTimeMillis()
        Log.d(TAG, "doWork: end after ${end - start} milliseconds")

        postExecute()

        Result.success()
    }

    private fun setupNotificationBuilder() {
        notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationBuilder =
            NotificationCompat.Builder(context, NotificationChannels.UPDATE_STATUS)
                .setContentTitle(context.getString(R.string.updating_hostfiles))
                .setSmallIcon(R.drawable.ic_refresh)
                .setProgress(config.hosts.items.size, 0, false)
    }

    /**
     * Releases all persisted URI permissions that are no longer referenced
     */
    private fun releaseGarbagePermissions() {
        val contentResolver = context.contentResolver
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
        for (item in config.hosts.items) {
            if (Uri.parse(item.location) == uri) {
                return false
            }
        }
        return true
    }

    /**
     * Sets progress message.
     */
    @Synchronized
    private fun updateProgressNotification() {
        val builder = StringBuilder()
        for (p in pending) {
            if (builder.isNotEmpty()) {
                builder.append("\n")
            }
            builder.append(p)
        }

        notificationBuilder.setProgress(pending.size + done.size, done.size, false)
            .setStyle(NotificationCompat.BigTextStyle().bigText(builder.toString()))
            .setContentText(context.getString(R.string.updating_n_host_files, pending.size))
        notificationManager.notify(UPDATE_NOTIFICATION_ID, notificationBuilder.build())
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

        if (errors.isEmpty()) {
            notificationManager.cancel(UPDATE_NOTIFICATION_ID)
        } else {
            val intent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }

            lastErrors = errors
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            )

            notificationBuilder
                .setProgress(0, 0, false)
                .setContentText(context.getString(R.string.could_not_update_all_hosts))
                .setSmallIcon(R.drawable.ic_warning)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
            notificationManager.notify(UPDATE_NOTIFICATION_ID, notificationBuilder.build())
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
        errors.add("${item.title}\n$message")
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
