package com.aymanelbanhawy.enterprisepdf.app

import android.app.Application
import android.os.SystemClock
import androidx.work.Configuration
import com.aymanelbanhawy.enterprisepdf.app.release.AppLogger
import com.aymanelbanhawy.enterprisepdf.app.release.AppRuntimeConfig
import com.aymanelbanhawy.enterprisepdf.app.release.AppRuntimeConfigLoader
import kotlinx.coroutines.runBlocking

class EnterprisePdfApplication : Application(), Configuration.Provider {
    private val runtimeConfig: AppRuntimeConfig by lazy(LazyThreadSafetyMode.NONE) {
        AppRuntimeConfigLoader.load(this)
    }

    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        val startedAt = SystemClock.elapsedRealtime()
        super.onCreate()
        AppLogger.configure(runtimeConfig)
        val editorCoreContainer = com.aymanelbanhawy.editor.core.EditorCoreContainer(this)
        appContainer = AppContainer(editorCoreContainer, runtimeConfig)
        runBlocking {
            val migrationReport = appContainer.migrationCoordinator.runUpgradePass()
            val repair = appContainer.runtimeDiagnosticsRepository.runStartupRepair()
            appContainer.runtimeDiagnosticsRepository.recordAppStart(SystemClock.elapsedRealtime() - startedAt, repair)
            appContainer.runtimeDiagnosticsRepository.recordBreadcrumb(
                category = com.aymanelbanhawy.editor.core.runtime.RuntimeEventCategory.Startup,
                level = if (migrationReport.core.failureCount == 0) com.aymanelbanhawy.editor.core.runtime.RuntimeLogLevel.Info else com.aymanelbanhawy.editor.core.runtime.RuntimeLogLevel.Warn,
                eventName = "migration_report",
                message = migrationReport.core.notice ?: migrationReport.aiStateMessage.ifBlank { "Migration pass completed." },
                metadata = mapOf(
                    "aiStateNormalizedCount" to migrationReport.aiStateNormalizedCount.toString(),
                    "migrationSuccessCount" to migrationReport.core.successCount.toString(),
                    "migrationFailureCount" to migrationReport.core.failureCount.toString(),
                ),
            )
        }
        AppLogger.i(message = "Application initialized for ${runtimeConfig.environment}")
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(if (runtimeConfig.secureLoggingEnabled) android.util.Log.WARN else android.util.Log.INFO)
            .build()
}
