package com.prism.ot.capture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import com.prism.ot.R
import com.prism.ot.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * Keeps the scanning process alive during long transfers and satisfies the
 * Android 14+ foreground-service-type requirement.
 *
 * The service does **not** own the camera. CameraX remains lifecycle-bound
 * to the scanning Activity so that the preview surface and the analysis
 * use case share a single camera session. The service's role is:
 *  - declare `foregroundServiceType="camera"` and hold the corresponding
 *    `FOREGROUND_SERVICE_CAMERA` permission,
 *  - show an ongoing notification with a stop action,
 *  - hold a partial wake lock so the CPU does not sleep mid-transfer.
 *
 * While-in-use compliance: the `CAMERA` runtime permission is subject to
 * while-in-use restrictions, so this service must only be started while
 * the app is in the foreground. It cannot be started from a
 * `BOOT_COMPLETED` receiver or from the background. The
 * [ScanServiceController] documents and enforces this by being called
 * from `onResume`.
 */
@AndroidEntryPoint
class ScanForegroundService : LifecycleService() {

    @Inject
    lateinit var powerManager: PowerManager

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        if (intent?.action == ACTION_STOP) {
            stopScan()
            return Service.START_NOT_STICKY
        }

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        } else {
            0
        }
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            type,
        )
        acquireWakeLock()
        _isRunning.value = true
        return Service.START_STICKY
    }

    override fun onDestroy() {
        releaseWakeLock()
        _isRunning.value = false
        super.onDestroy()
    }

    private fun stopScan() {
        releaseWakeLock()
        _isRunning.value = false
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "Prism::ScanSession",
        ).apply {
            setReferenceCounted(false)
            acquire(MAX_WAKE_LOCK_MILLIS)
        }
    }

    private fun releaseWakeLock() {
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        wakeLock = null
    }

    private fun buildNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, ScanForegroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.scan_notification_title))
            .setContentText(getString(R.string.scan_notification_text))
            .setSmallIcon(R.drawable.ic_stat_prism)
            .setContentIntent(contentIntent)
            .addAction(0, getString(R.string.scan_notification_stop), stopIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.scan_notification_channel),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.scan_notification_channel_desc)
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val ACTION_STOP = "com.prism.ot.action.STOP_SCAN"
        private const val CHANNEL_ID = "prism_scan"
        private const val NOTIFICATION_ID = 1001
        private const val MAX_WAKE_LOCK_MILLIS = 2L * 60L * 60L * 1000L

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()
    }
}
