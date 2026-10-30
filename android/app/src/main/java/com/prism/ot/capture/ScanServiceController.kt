package com.prism.ot.capture

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Starts and stops [ScanForegroundService] on behalf of the UI.
 *
 * While-in-use compliance is enforced by convention: this controller is
 * called from `onResume` of the scanning screen. Starting a camera
 * foreground service from the background is prohibited on Android 14+
 * and will throw `ForegroundServiceStartNotAllowedException`.
 */
@Singleton
class ScanServiceController @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    val isRunning: StateFlow<Boolean> = ScanForegroundService.isRunning

    fun start() {
        val intent = Intent(context, ScanForegroundService::class.java)
        ContextCompat.startForegroundService(context, intent)
    }

    fun stop() {
        context.startService(
            Intent(context, ScanForegroundService::class.java)
                .setAction(ScanForegroundService.ACTION_STOP),
        )
    }
}
