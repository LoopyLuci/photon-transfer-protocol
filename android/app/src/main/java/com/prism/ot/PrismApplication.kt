package com.prism.ot

import android.app.Application
import android.util.Log
import com.prism.ot.core.PrismLogger
import com.prism.ot.ml.MlBridge
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Process-wide application entry point.
 *
 * Responsibilities:
 *  - Initialize Hilt graph.
 *  - Load the native Rust codec core once and fail fast if the .so is missing.
 *  - Warm ML model handles lazily via [MlBridge] but only *validate* their presence here.
 *  - Install an uncaught exception handler that emits a diagnostic log so we can
 *    correlate crashes against the native core version.
 */
@HiltAndroidApp
class PrismApplication : Application() {

    @Inject lateinit var logger: PrismLogger
    @Inject lateinit var mlBridge: MlBridge

    override fun onCreate() {
        super.onCreate()
        installCrashHandler()
        validateNativeCore()
        validateModelAssets()
    }

    private fun installCrashHandler() {
        val prior = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                logger.e(
                    TAG,
                    "Uncaught exception on ${thread.name}: ${throwable.javaClass.simpleName}",
                    throwable
                )
            } finally {
                prior?.uncaughtException(thread, throwable)
            }
        }
    }

    private fun validateNativeCore() {
        runCatching {
            val version = mlBridge.nativeVersion()
            logger.i(TAG, "Native PRISM core loaded, version=$version")
        }.onFailure {
            // Not fatal: the app can still launch and show a clear error UI.
            // The codec layer will surface CodecError.NativeLibraryMissing.
            logger.e(TAG, "Native PRISM core failed to load", it)
        }
    }

    private fun validateModelAssets() {
        runCatching {
            val missing = mlBridge.listMissingModelAssets()
            if (missing.isNotEmpty()) {
                logger.w(TAG, "Missing ML model assets: $missing")
            } else {
                logger.i(TAG, "All ML model assets present")
            }
        }.onFailure { Log.w(TAG, "Model asset validation failed", it) }
    }

    companion object {
        private const val TAG = "PrismApplication"
    }
}
