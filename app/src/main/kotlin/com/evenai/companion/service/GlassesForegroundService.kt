package com.evenai.companion.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.evenai.companion.MainActivity
import com.evenai.companion.R

/**
 * Foreground service that keeps the BLE connections alive when the app is
 * backgrounded. Required for reliable BLE on Android 12+.
 */
class GlassesForegroundService : Service() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("G1 Companion")
            .setContentText("Glasses connected and listening")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "G1 Connection",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps your glasses connected in the background"
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID       = "g1_connection"
        private const val NOTIFICATION_ID  = 1001

        fun start(context: Context) {
            context.startForegroundService(Intent(context, GlassesForegroundService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, GlassesForegroundService::class.java))
        }
    }
}
