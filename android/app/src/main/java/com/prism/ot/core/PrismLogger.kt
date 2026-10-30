package com.prism.ot.core

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Application-wide logging façade. Behind this interface we can later
 * swap Logcat for file logging, remote telemetry, or an in-memory ring
 * buffer surfaced in a debug screen.
 */
interface PrismLogger {
    fun v(tag: String, message: String)
    fun d(tag: String, message: String)
    fun i(tag: String, message: String)
    fun w(tag: String, message: String, throwable: Throwable? = null)
    fun e(tag: String, message: String, throwable: Throwable? = null)
}

@Singleton
class LogcatLogger @Inject constructor() : PrismLogger {

    private val enabled = Log.isLoggable("Prism", Log.DEBUG)
    private val verboseEnabled = Log.isLoggable("Prism", Log.VERBOSE)

    override fun v(tag: String, message: String) {
        if (verboseEnabled) Log.v(tag, message)
    }

    override fun d(tag: String, message: String) {
        if (enabled) Log.d(tag, message)
    }

    override fun i(tag: String, message: String) = Log.i(tag, message).let { }

    override fun w(tag: String, message: String, throwable: Throwable?) {
        if (throwable != null) Log.w(tag, message, throwable) else Log.w(tag, message)
    }

    override fun e(tag: String, message: String, throwable: Throwable?) {
        if (throwable != null) Log.e(tag, message, throwable) else Log.e(tag, message)
    }
}
