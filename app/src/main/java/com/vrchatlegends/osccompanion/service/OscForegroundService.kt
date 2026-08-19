package com.vrchatlegends.osccompanion.service

import android.app.Notification
import android.app.PendingIntent
import android.app.ForegroundServiceStartNotAllowedException
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
import com.vrchatlegends.osccompanion.capture.CameraCaptureWatcher
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

    private var foregroundStarted = false

    override fun onCreate() {
        super.onCreate()
        foregroundStarted = tryStartForeground()
        if (!foregroundStarted) {
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (!foregroundStarted) return START_NOT_STICKY
        if (intent?.action == ACTION_STOP) {
            OscRepository.get(applicationContext).stop()
            CameraCaptureWatcher.get(applicationContext).stop()
            stopSelf()
            return START_NOT_STICKY
        }
        lifecycleScope.launch {
            val settings = SettingsStore(applicationContext).settings.first()
            val repo = OscRepository.get(applicationContext)
            repo.applySettings(settings)

            when (intent?.action) {
                ACTION_STOP_OSC -> repo.stop()
                else -> if (intent?.getBooleanExtra(EXTRA_START_OSC, false) == true &&
                    !repo.connection.value.running
                ) {
                    repo.start()
                }
            }

            val watcher = CameraCaptureWatcher.get(applicationContext)
            if (settings.captureAutoSend) watcher.start(lifecycleScope) else watcher.stop()

            if (!repo.connection.value.running && !settings.captureAutoSend) stopSelf()
        }
        // A sticky recreation happens while the app is backgrounded, where Android refuses
        // startForeground and used to crash the process. The activity reconnects it on resume.
        return START_NOT_STICKY
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
            .setContentText("Background tools active")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(open)
            .addAction(0, "Stop", stop)
            .build()
    }

    private fun tryStartForeground(): Boolean = try {
        startForeground(NOTIFICATION_ID, buildNotification())
        true
    } catch (error: RuntimeException) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            error is ForegroundServiceStartNotAllowedException
        ) {
            false
        } else {
            throw error
        }
    }

    companion object {
        private const val NOTIFICATION_ID = 4201
        const val ACTION_STOP = "com.vrchatlegends.osccompanion.STOP"
        private const val ACTION_STOP_OSC = "com.vrchatlegends.osccompanion.STOP_OSC"
        private const val ACTION_REFRESH = "com.vrchatlegends.osccompanion.REFRESH_SERVICE"
        private const val EXTRA_START_OSC = "start_osc"

        fun start(context: Context, startOsc: Boolean) {
            val intent = Intent(context, OscForegroundService::class.java)
                .setAction(ACTION_REFRESH)
                .putExtra(EXTRA_START_OSC, startOsc)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopOsc(context: Context) {
            val intent = Intent(context, OscForegroundService::class.java).setAction(ACTION_STOP_OSC)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun refresh(context: Context) {
            start(context, startOsc = false)
        }
    }
}
