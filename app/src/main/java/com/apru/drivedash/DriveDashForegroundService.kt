package com.apru.drivedash

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * Foreground Service that keeps DriveDash active in the background:
 * - Prevents Android OS from pausing/throttling high-accuracy GPS location updates
 * - Prevents Android from silencing the WebRTC microphone and audio streaming
 * - Keeps network sockets open for Firebase and peer-to-peer WebRTC mesh voice
 * - Retains a partial wake lock so the CPU remains active when the screen is locked
 */
class DriveDashForegroundService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        const val CHANNEL_ID = "drivedash_active_ride_channel"
        const val NOTIFICATION_ID = 9001
        const val ACTION_START = "com.apru.drivedash.action.START_SERVICE"
        const val ACTION_STOP = "com.apru.drivedash.action.STOP_SERVICE"
        const val EXTRA_ROOM_CODE = "extra_room_code"

        fun start(context: Context, roomCode: String) {
            val intent = Intent(context, DriveDashForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_ROOM_CODE, roomCode)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                android.util.Log.e("DriveDashService", "Failed to start foreground service", e)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, DriveDashForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                android.util.Log.e("DriveDashService", "Failed to stop foreground service", e)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForegroundAndSelf()
            return START_NOT_STICKY
        }

        val roomCode = intent?.getStringExtra(EXTRA_ROOM_CODE) ?: ""
        val notification = buildNotification(roomCode)

        val hasLocation = ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasMic = ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

        var serviceType = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (hasLocation) {
                serviceType = serviceType or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && hasMic) {
                serviceType = serviceType or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            }
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && serviceType != 0) {
                startForeground(NOTIFICATION_ID, notification, serviceType)
            } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, notification)
            } else {
                if (hasLocation) {
                    startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
                } else if (hasMic) {
                    startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
                } else {
                    stopForegroundAndSelf()
                    return START_NOT_STICKY
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("DriveDashService", "startForeground error", e)
        }

        return START_STICKY
    }

    private fun buildNotification(roomCode: String): Notification {
        val tapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (roomCode.isNotBlank()) "DriveDash Convoy (Room $roomCode)" else "DriveDash Convoy Active"
        val subtitle = "Live GPS & Voice Chat running in background"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Active Convoy Ride",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps DriveDash GPS and voice chat running when app is in background or screen is off"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "DriveDash:RideWakeLock")?.apply {
                acquire(4 * 60 * 60 * 1000L) // 4 hours safety timeout
            }
        } catch (e: Exception) {
            android.util.Log.w("DriveDashService", "Could not acquire WakeLock", e)
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
            wakeLock = null
        } catch (_: Exception) {}
    }

    private fun stopForegroundAndSelf() {
        releaseWakeLock()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    override fun onDestroy() {
        releaseWakeLock()
        super.onDestroy()
    }
}
