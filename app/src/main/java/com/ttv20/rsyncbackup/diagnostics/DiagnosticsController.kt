package com.ttv20.rsyncbackup.diagnostics

import android.app.Application
import com.ttv20.rsyncbackup.BuildConfig
import com.ttv20.rsyncbackup.model.BackupLog
import com.ttv20.rsyncbackup.model.BackupProfile
import com.ttv20.rsyncbackup.model.RunStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import sh.measure.android.Measure
import sh.measure.android.config.MeasureConfig

class DiagnosticsController(private val application: Application) {
    private val consentStore = DiagnosticsConsentStore(application)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var measureInitialized = false

    @Volatile
    private var reporter: DiagnosticsReporter = NoopDiagnosticsReporter

    fun initialize() {
        initializeMeasure()
        applyConsent(consentStore.consent())
    }

    fun syncConsent(consent: Boolean?) {
        consentStore.setConsent(consent)
        applyConsent(consent)
    }

    fun updateConsent(enabled: Boolean) {
        val previous = consentStore.consent()
        if (enabled) {
            consentStore.setConsent(true)
            applyConsent(true)
            if (previous != true) {
                trackEvent("diagnostics_enabled")
            }
        } else {
            if (previous == true) {
                trackEvent("diagnostics_disabled")
            }
            stopMeasure()
            consentStore.setConsent(false)
            reporter = NoopDiagnosticsReporter
        }
    }

    fun trackEvent(name: String, attributes: Map<String, Any?> = emptyMap()) {
        reporter.trackEvent(name, attributes)
    }

    fun trackHandledException(error: Throwable, attributes: Map<String, Any?> = emptyMap()) {
        reporter.trackHandledException(error, attributes)
    }

    fun trackBackupLog(log: BackupLog, profile: BackupProfile? = null) {
        val eventName = when (log.status) {
            RunStatus.SUCCESS,
            RunStatus.WARNING,
            -> "backup_finished"
            RunStatus.FAILED -> "backup_failed"
            RunStatus.CANCELLED -> "backup_cancelled"
            RunStatus.RUNNING,
            RunStatus.QUEUED,
            RunStatus.NEVER_RUN,
            -> return
        }
        trackEvent(
            eventName,
            mapOf(
                DiagnosticsAttributes.TRIGGER_TYPE to log.trigger.name.lowercase(),
                DiagnosticsAttributes.RUN_STATUS to log.status.name.lowercase(),
                DiagnosticsAttributes.END_REASON to log.endReason?.name?.lowercase(),
                DiagnosticsAttributes.RSYNC_EXIT_CODE to log.exitCode,
                DiagnosticsAttributes.TARGET_MODE to (log.targetMode ?: profile?.targetMode)?.name?.lowercase(),
                DiagnosticsAttributes.ROUTE_USED to log.routeUsed?.name?.lowercase(),
                DiagnosticsAttributes.DRY_RUN_ENABLED to (log.dryRunEnabled ?: profile?.dryRunBeforeBackup),
                DiagnosticsAttributes.DELETE_ENABLED to (log.deleteEnabled ?: profile?.deleteEnabled),
                DiagnosticsAttributes.FAILURE_STAGE to log.failureStage,
                DiagnosticsAttributes.FAILURE_CATEGORY to log.failureCategory,
                DiagnosticsAttributes.RSYNC_WARNING_24 to (log.exitCode == 24),
            ) + DiagnosticsAttributes.backupIdentity(
                profileId = log.profileId,
                targetId = profile
                    ?.takeIf { it.id == log.profileId }
                    ?.targetId,
            ) + BackupLogDiagnosticsMetrics.attributes(log),
        )
    }

    fun shutdown() {
        scope.cancel()
    }

    private fun initializeMeasure() {
        if (measureInitialized || !measureConfigured()) return
        Measure.init(
            application,
            MeasureConfig(
                autoStart = false,
                trackActivityIntentData = false,
                enableFullCollectionMode = BuildConfig.DEBUG && !BuildConfig.IS_FDROID_BUILD,
            ),
        )
        measureInitialized = true
    }

    private fun applyConsent(consent: Boolean?) {
        if (diagnosticsConsentAllowsNetwork(consent)) {
            startMeasure()
            reporter = buildEnabledReporter()
        } else {
            stopMeasure()
            reporter = NoopDiagnosticsReporter
        }
    }

    private fun buildEnabledReporter(): DiagnosticsReporter {
        val reporters = buildList {
            if (measureInitialized) {
                add(MeasureDiagnosticsReporter())
            }
            if (DiagnosticsEndpoints.eventEndpoint() != null) {
                add(
                    OpenObserveDiagnosticsReporter(
                        context = application,
                        consentStore = consentStore,
                        scope = scope,
                    ),
                )
            }
        }
        return when (reporters.size) {
            0 -> NoopDiagnosticsReporter
            1 -> reporters.single()
            else -> CompositeDiagnosticsReporter(reporters)
        }
    }

    private fun startMeasure() {
        if (measureInitialized) {
            Measure.start()
        }
    }

    private fun stopMeasure() {
        if (measureInitialized) {
            Measure.stop()
        }
    }

    private fun measureConfigured(): Boolean =
        BuildConfig.MEASURE_API_URL.isNotBlank() && BuildConfig.MEASURE_API_KEY.isNotBlank()
}
