package com.prism.ot.motor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.prism.ot.R
import com.prism.ot.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service for the BLE motor connection.
 *
 * Android 14+ requires the `connectedDevice` type and the
 * `FOREGROUND_SERVICE_CONNECTED_DEVICE` permission for any foreground
 * service that interacts with an external Bluetooth device. The runtime
 * prerequisite is one of `BLUETOOTH_CONNECT`, `BLUETOOTH_SCAN`, or a
 * granted `BLUETOOTH_*` permission.
 *
 * The service does **not** own the GATT connection — [MotorControllerImpl]
 * does. The service exists to keep the process alive and visible while a
 * kinetic transfer is in progress, and to provide a stop action that
 * disconnects the motor and stops the policy loop.
 *
 * While-in-use: starting a `connectedDevice` service from the background is
 * permitted on Android 14+ **only** if the app has an active Bluetooth
 * connection or a companion-device association. PRISM starts the service
 * from the kinetic scan screen while the activity is visible, which
 * satisfies the requirement without needing the companion-device manager.
 */
@AndroidEntryPoint
class BleMotorService : Service() {

    @Inject lateinit var motorController: MotorController
    @Inject lateinit var policyLoop: MotorPolicyLoop

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopMotor()
            return START_NOT_STICKY
        }

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), type)
        _isRunning.value = true
        return START_STICKY
    }

    override fun onDestroy() {
        policyLoop.stop()
        scope.cancel()
        _isRunning.value = false
        super.onDestroy()
    }

    private fun stopMotor() {
        policyLoop.stop()
        scope.launch { motorController.disconnect() }
        _isRunning.value = false
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
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
            Intent(this, BleMotorService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.motor_notification_title))
            .setContentText(getString(R.string.motor_notification_text))
            .setSmallIcon(R.drawable.ic_stat_prism)
            .setContentIntent(contentIntent)
            .addAction(0, getString(R.string.motor_notification_stop), stopIntent)
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
            getString(R.string.motor_notification_channel),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.motor_notification_channel_desc)
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val ACTION_STOP = "com.prism.ot.action.STOP_MOTOR"
        private const val CHANNEL_ID = "prism_motor"
        private const val NOTIFICATION_ID = 1002

        private val _isRunning = kotlinx.coroutines.flow.MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning

        fun start(context: Context) {
            androidx.core.content.ContextCompat.startForegroundService(
                context,
                Intent(context, BleMotorService::class.java),
            )
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, BleMotorService::class.java).setAction(ACTION_STOP),
            )
        }
    }
}
