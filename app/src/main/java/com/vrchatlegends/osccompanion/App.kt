package com.vrchatlegends.osccompanion

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.vrchatlegends.osccompanion.diag.CrashReporter

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            OSC_CHANNEL_ID,
            getString(R.string.osc_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.osc_channel_desc)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val OSC_CHANNEL_ID = "osc_link"
    }
}
