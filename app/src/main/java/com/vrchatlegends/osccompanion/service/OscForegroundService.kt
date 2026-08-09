package com.vrchatlegends.osccompanion.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.vrchatlegends.osccompanion.App
import com.vrchatlegends.osccompanion.MainActivity
import com.vrchatlegends.osccompanion.R
import com.vrchatlegends.osccompanion.data.SettingsStore
import com.vrchatlegends.osccompanion.osc.OscRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Keeps the UDP socket and the OSCQuery advertisement alive while the panel is not
 * focused. Horizon OS will otherwise freeze a backgrounded 2D app, which silently drops
 * every inbound avatar parameter and stops the rotating chatbox.
 */
class OscForegroundService : LifecycleService() {

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())
        lifecycleScope.launch {
            val settings = SettingsStore(applicationContext).settings.first()
            val repo = OscRepository.get(applicationContext)
            repo.applySettings(settings)
            if (!repo.connection.value.running) repo.start()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) {
            OscRepository.get(applicationContext).stop()
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, OscForegroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0,
        )

        return NotificationCompat.Builder(this, App.OSC_CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("OSC link active")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(open)
            .addAction(0, "Stop", stop)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 4201
        const val ACTION_STOP = "com.vrchatlegends.osccompanion.STOP"

        fun start(context: Context) {
            val intent = Intent(context, OscForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, OscForegroundService::class.java))
        }
    }
}
