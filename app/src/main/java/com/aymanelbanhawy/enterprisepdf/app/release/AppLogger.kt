package com.aymanelbanhawy.enterprisepdf.app.release

import android.util.Log
import com.aymanelbanhawy.enterprisepdf.app.BuildConfig

object AppLogger {
    private const val DEFAULT_TAG = "EnterprisePdf"
    @Volatile
    private var secureLoggingEnabled: Boolean = true

    fun configure(runtimeConfig: AppRuntimeConfig) {
        secureLoggingEnabled = runtimeConfig.secureLoggingEnabled
    }

    fun i(tag: String = DEFAULT_TAG, message: String) {
        if (!BuildConfig.ALLOW_VERBOSE_DIAGNOSTICS && secureLoggingEnabled) {
            return
        }
        Log.i(tag, sanitize(message))
    }

    fun w(tag: String = DEFAULT_TAG, message: String, throwable: Throwable? = null) {
        Log.w(tag, sanitize(message), throwable)
    }

    fun e(tag: String = DEFAULT_TAG, message: String, throwable: Throwable? = null) {
        Log.e(tag, sanitize(message), throwable)
    }

    private fun sanitize(message: String): String {
        if (!secureLoggingEnabled) {
            return message
        }
        return message
            .replace(Regex("(?i)bearer\\s+[A-Za-z0-9._-]+"), "Bearer [REDACTED]")
            .replace(Regex("(?i)(api[_-]?key|token|secret)=([^&\\s]+)"), "$1=[REDACTED]")
    }
}
