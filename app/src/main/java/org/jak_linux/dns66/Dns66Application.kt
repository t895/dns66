package org.jak_linux.dns66

import android.app.Application
import android.content.Context

class Dns66Application : Application() {
    companion object {
        private lateinit var application: Application
        val applicationContext: Context get() = application.applicationContext
    }

    override fun onCreate() {
        super.onCreate()
        application = this
    }
}
