/* Copyright (C) 2017 Julian Andres Klode <jak@jak-linux.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.jak_linux.dns66.db

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.util.Log
import org.jak_linux.dns66.Configuration
import org.jak_linux.dns66.Dns66Application.Companion.applicationContext
import org.jak_linux.dns66.FileHelper
import java.util.concurrent.TimeUnit

/**
 * Automatic daily host file refreshes.
 */
class RuleDatabaseUpdateJobService : JobService() {
    companion object {
        private const val JOB_ID = 1
        private const val TAG = "DbUpdateJobService"

        /**
         * Schedules or cancels the job, depending on the configuration
         *
         * @return true if the job could be scheduled.
         */
        @JvmStatic
        fun scheduleOrCancel(configuration: Configuration): Boolean {
            val scheduler =
                applicationContext.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler

            if (!configuration.hosts.automaticRefresh) {
                Log.d(TAG, "scheduleOrCancel: Cancelling Job")

                scheduler.cancel(JOB_ID)
                return true
            }
            Log.d(TAG, "scheduleOrCancel: Scheduling Job")

            val serviceName =
                ComponentName(applicationContext, RuleDatabaseUpdateJobService::class.java)

            val jobInfo = JobInfo.Builder(JOB_ID, serviceName)
                .setRequiresCharging(true)
                .setRequiresDeviceIdle(true)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
                .setPersisted(true)
                .setPeriodic(TimeUnit.DAYS.toMillis(1))
                .build()

            val result = scheduler.schedule(jobInfo)
            if (result == JobScheduler.RESULT_SUCCESS) {
                Log.d(TAG, "Job scheduled")
            } else {
                Log.d(TAG, "Job not scheduled")
            }

            return result == JobScheduler.RESULT_SUCCESS
        }
    }

    private lateinit var task: RuleDatabaseUpdateTask

    override fun onStartJob(params: JobParameters?): Boolean {
        Log.d(TAG, "onStartJob: Start job")

        task = object : RuleDatabaseUpdateTask(
            FileHelper.loadCurrentSettings(),
            true
        ) {
            override fun onPostExecute(result: Void?) {
                super.onPostExecute(result)
                jobFinished(params, task.pendingCount() > 0)
            }

            override fun onCancelled(result: Void?) {
                super.onCancelled(result)
                jobFinished(params, task.pendingCount() > 0)
            }
        }

        task.execute()
        return false
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        Log.d(TAG, "onStartJob: Stop job")
        task.cancel(true)
        return task.pendingCount() > 0
    }
}
