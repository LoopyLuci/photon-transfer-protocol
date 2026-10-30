package com.prism.ot.session

import android.content.Context
import com.prism.ot.core.PrismLogger
import com.prism.ot.data.model.TransferResult
import com.prism.ot.data.preferences.PrismPreferences
import com.prism.ot.io.TransferResultDeliverer
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wraps [TransferResultDeliverer] with session‑level bookkeeping:
 *  - Records completed sessions in [PrismPreferences] for the home screen
 *    statistics.
 *  - Cleans up the app‑private file when delivery fails, so a failed
 *    session does not leak storage.
 *  - Surfaces the APK‑specific path (launch installer) versus the generic
 *    share path.
 */
@Singleton
class SessionResultDeliverer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val deliverer: TransferResultDeliverer,
    private val preferences: PrismPreferences,
    private val logger: PrismLogger,
) {

    suspend fun deliver(sessionResult: SessionResult) {
        when (sessionResult) {
            is SessionResult.Completed -> {
                preferences.recordSessionCompletion(sessionResult.transfer.totalBytesReceived)
                deliverer.launch(sessionResult.transfer)
            }
            is SessionResult.TransferFailed -> {
                logger.w(
                    TAG,
                    "Transfer failed: ${sessionResult.transfer.error.code}",
                )
            }
            is SessionResult.Cancelled -> {
                logger.i(TAG, "Transfer cancelled at ${sessionResult.bytesReceived} bytes")
            }
            is SessionResult.Error -> {
                logger.e(TAG, "Session error: ${sessionResult.error.code}")
            }
        }
    }
}
