package com.ttv20.rsyncbackup.backup

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.text.SpannableString
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import androidx.core.app.NotificationCompat
import com.ttv20.rsyncbackup.MainActivity
import com.ttv20.rsyncbackup.R
import com.ttv20.rsyncbackup.RsyncBackupApplication
import com.ttv20.rsyncbackup.model.BackupLog
import com.ttv20.rsyncbackup.model.BackupProfile
import com.ttv20.rsyncbackup.model.BackupRunTrigger
import com.ttv20.rsyncbackup.model.RunProgressPhase
import com.ttv20.rsyncbackup.model.RunProgressState
import com.ttv20.rsyncbackup.model.RunStatus
import com.ttv20.rsyncbackup.model.transferProgressPercent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.Locale

class BackupService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val processController = BackupProcessController()
    private val queueWorkerLock = Any()
    private var queueWorkerRunning = false
    private var cancelGraceJob: Job? = null
    private var progressNotificationJob: Job? = null
    @Volatile private var latestStartId = 0

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        latestStartId = startId
        when (intent?.action) {
            ACTION_RUN, ACTION_RUN_SCHEDULED, ACTION_RUN_ANYWAY -> handleRunRequest(intent)
            ACTION_CANCEL -> handleCancelRequest()
            ACTION_FORCE_STOP -> handleForceStopRequest()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        processController.requestForceStop()
        progressNotificationJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun handleRunRequest(intent: Intent) {
        val profileId = intent.getStringExtra(EXTRA_PROFILE_ID) ?: return
        val app = application as RsyncBackupApplication
        val profile = app.repository.state.value.profiles.firstOrNull { it.id == profileId } ?: return
        val runAnyway = intent.action == ACTION_RUN_ANYWAY
        val trigger = intent.backupRunTrigger()
        val snapshot = AndroidConstraintSnapshotReader(this).read()
        val failures = BackupConstraintEvaluator.failures(
            profile = profile,
            snapshot = snapshot,
        )

        startForeground(NOTIFICATION_ID, runningNotification("Checking backup constraints"))
        if (failures.isNotEmpty() && !runAnyway) {
            app.repository.recordConstraintBlockedBackup(profile, failures, trigger, snapshot)
            notifyConstraintWarning(this, profile, failures, snapshot, trigger)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelfResult(latestStartId)
            return
        }

        app.repository.enqueueBackup(profileId, trigger = trigger)
        startQueueWorker()
    }

    private fun Intent.backupRunTrigger(): BackupRunTrigger =
        if (action == ACTION_RUN_SCHEDULED) {
            BackupRunTrigger.AUTOMATIC
        } else {
            BackupRunTrigger.MANUAL
        }

    private fun startQueueWorker() {
        synchronized(queueWorkerLock) {
            if (queueWorkerRunning) return
            queueWorkerRunning = true
        }
        scope.launch {
            val app = application as RsyncBackupApplication
            var stopStartId: Int? = null
            try {
                while (stopStartId == null) {
                    val profileId = app.repository.startNextQueued()
                    if (profileId == null) {
                        synchronized(queueWorkerLock) {
                            val queue = app.repository.state.value.queue
                            if (queue.runningProfileId == null && queue.queuedProfileIds.isEmpty()) {
                                queueWorkerRunning = false
                                stopStartId = latestStartId
                            }
                        }
                        continue
                    }

                    val profileName = app.repository.state.value.profiles
                        .firstOrNull { it.id == profileId }
                        ?.name
                        ?: profileId
                    val trigger = app.repository.state.value.queue.runningTrigger ?: BackupRunTrigger.MANUAL
                    val serviceStartedAt = Instant.now().toString()
                    startForeground(NOTIFICATION_ID, runningNotification("Running $profileName"))
                    startProgressNotificationObserver(app, profileId, profileName)
                    val log = try {
                        BackupEngine(
                            context = this@BackupService,
                            repository = app.repository,
                            secretStore = app.secretStore,
                            processController = processController,
                        ).runProfile(profileId, trigger)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        failedLog(app, profileId, profileName, trigger, serviceStartedAt, error)
                    } finally {
                        progressNotificationJob?.cancel()
                        progressNotificationJob = null
                        cancelGraceJob?.cancel()
                        app.repository.completeRunning(profileId)
                    }
                    resultNotification(log.summary)
                    if (log.isTailscaleProblem()) {
                        tailscaleProblemNotification(log.summary)
                    }
                }
            } finally {
                val finalStartId = stopStartId ?: synchronized(queueWorkerLock) {
                    queueWorkerRunning = false
                    latestStartId
                }
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelfResult(finalStartId)
            }
        }
    }

    private fun handleCancelRequest() {
        val app = application as RsyncBackupApplication
        val runningProfileId = app.repository.state.value.queue.runningProfileId ?: return
        processController.requestGracefulCancel()
        app.repository.markRunningMessage(
            profileId = runningProfileId,
            message = "Cancellation requested",
            progressPhase = RunProgressPhase.CANCELLING,
        )
        startForeground(NOTIFICATION_ID, runningNotification("Cancelling backup", allowForceStop = true))
        cancelGraceJob?.cancel()
        cancelGraceJob = scope.launch {
            delay(CANCEL_GRACE_MS)
            if (processController.isGracefulCancelPending()) {
                processController.requestForceStop()
                app.repository.markRunningMessage(
                    profileId = runningProfileId,
                    message = "Force stopping backup",
                    progressPhase = RunProgressPhase.FORCE_STOPPING,
                )
                startForeground(NOTIFICATION_ID, runningNotification("Force stopping backup", allowForceStop = true))
            }
        }
    }

    private fun handleForceStopRequest() {
        val app = application as RsyncBackupApplication
        val runningProfileId = app.repository.state.value.queue.runningProfileId ?: return
        processController.requestForceStop()
        app.repository.markRunningMessage(
            profileId = runningProfileId,
            message = "Force stopping backup",
            progressPhase = RunProgressPhase.FORCE_STOPPING,
        )
        startForeground(NOTIFICATION_ID, runningNotification("Force stopping backup", allowForceStop = true))
    }

    private fun failedLog(
        app: RsyncBackupApplication,
        profileId: String,
        profileName: String,
        trigger: BackupRunTrigger,
        serviceStartedAt: String,
        error: Exception,
    ): BackupLog {
        val failedAt = Instant.now().toString()
        val startedAt = app.repository.state.value.runProgress.startedAt ?: serviceStartedAt
        val log = backupCrashLog(
            profileId = profileId,
            profileName = profileName,
            startedAt = startedAt,
            trigger = trigger,
            error = error,
            failedAt = failedAt,
        )
        app.repository.setRunProgress(
            app.repository.state.value.runProgress.copy(
                profileId = profileId,
                profileName = profileName,
                phase = RunProgressPhase.FAILED,
                message = log.summary,
                updatedAt = failedAt,
            ),
        )
        app.repository.appendLog(log)
        app.repository.markProfile(profileId, RunStatus.FAILED, log.summary, failedAt)
        return log
    }

    private fun runningNotification(text: String, allowForceStop: Boolean = false) =
        NotificationCompat.Builder(this, CHANNEL_RUNNING)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("Running backup")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(openAppIntent())
            .addAction(0, "Cancel", serviceIntent(ACTION_CANCEL))
            .let { builder ->
                if (allowForceStop) {
                    builder.addAction(0, "Force stop", serviceIntent(ACTION_FORCE_STOP))
                } else {
                    builder
                }
            }
            .build()

    private fun startProgressNotificationObserver(
        app: RsyncBackupApplication,
        profileId: String,
        profileName: String,
    ) {
        progressNotificationJob?.cancel()
        progressNotificationJob = scope.launch {
            val manager = getSystemService(NotificationManager::class.java)
            app.repository.state
                .map { it.runProgress }
                .distinctUntilChanged()
                .collect { progress ->
                    if (progress.profileId == profileId) {
                        manager.notify(
                            NOTIFICATION_ID,
                            runningNotification(progress.copy(profileName = progress.profileName ?: profileName)),
                        )
                    }
                }
        }
    }

    private fun runningNotification(progress: RunProgressState): android.app.Notification {
        val text = progress.message ?: progress.phase.notificationLabel()
        val fileLine = progress
            .takeUnless { it.phase == RunProgressPhase.DRY_RUN }
            ?.currentFile
            ?.let { "Last: ${compactMiddle(it)}" }
        val details = if (progress.phase == RunProgressPhase.DRY_RUN) {
            emptyList()
        } else {
            listOfNotNull(
                progress.filesTransferred?.let { transferred ->
                    progress.filesDiscovered?.let { discovered ->
                        "Files: $transferred/$discovered"
                    } ?: "Files: $transferred"
                },
                progress.bytesTransferredRaw?.let { "Transferred: ${formatBytes(it)}" }
                    ?: progress.bytesTransferred?.let { "Transferred: $it" },
                progress.plannedTransferBytesRaw?.let { "Planned: ${formatBytes(it)}" },
                progress.speed?.let { "Speed: $it" },
                progress.averageBytesPerSecond?.let { "Avg speed: ${formatBytes(it)}/s" }
                    ?: progress.recentAverageBytesPerSecond?.let { "Avg speed: ${formatBytes(it)}/s" },
                fileLine,
            )
        }
        val bigText = notificationBigText(text, details, fileLine)
        val allowForceStop = progress.phase == RunProgressPhase.CANCELLING ||
            progress.phase == RunProgressPhase.FORCE_STOPPING

        return NotificationCompat.Builder(this, CHANNEL_RUNNING)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("Running ${progress.profileName ?: "backup"}")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setOngoing(true)
            .setContentIntent(openAppIntent())
            .setProgressFor(progress)
            .addAction(0, "Cancel", serviceIntent(ACTION_CANCEL))
            .let { builder ->
                if (allowForceStop) {
                    builder.addAction(0, "Force stop", serviceIntent(ACTION_FORCE_STOP))
                } else {
                    builder
                }
            }
            .build()
    }

    private fun notificationBigText(
        text: String,
        details: List<String>,
        fileLine: String?,
    ): CharSequence {
        val value = (listOf(text) + details).joinToString("\n")
        if (fileLine == null) return value
        val start = value.indexOf(fileLine)
        if (start < 0) return value
        return SpannableString(value).apply {
            setSpan(
                RelativeSizeSpan(0.86f),
                start,
                start + fileLine.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
    }

    private fun compactMiddle(value: String, maxLength: Int = 48): String {
        if (value.length <= maxLength) return value
        val keepStart = (maxLength * 0.45f).toInt().coerceAtLeast(12)
        val keepEnd = (maxLength - keepStart - 3).coerceAtLeast(12)
        return value.take(keepStart).trimEnd('/') + "..." + value.takeLast(keepEnd).trimStart('/')
    }

    private fun formatBytes(bytes: Long): String {
        val units = listOf("B", "KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var unitIndex = 0
        while (value >= 1024.0 && unitIndex < units.lastIndex) {
            value /= 1024.0
            unitIndex += 1
        }
        return if (unitIndex == 0) {
            "$bytes ${units[unitIndex]}"
        } else {
            String.format(Locale.US, "%.1f %s", value, units[unitIndex])
        }
    }

    private fun NotificationCompat.Builder.setProgressFor(progress: RunProgressState): NotificationCompat.Builder {
        val transferPercent = progress.transferProgressPercent()
        return when {
            progress.phase == RunProgressPhase.RUNNING_RSYNC && transferPercent != null -> {
                setProgress(100, transferPercent, false)
            }
            progress.phase.isActive() -> setProgress(0, 0, true)
            else -> setProgress(0, 0, false)
        }
    }

    private fun RunProgressPhase.isActive(): Boolean =
        this == RunProgressPhase.PREPARING ||
            this == RunProgressPhase.DRY_RUN ||
            this == RunProgressPhase.RUNNING_RSYNC ||
            this == RunProgressPhase.UPLOADING_STATUS ||
            this == RunProgressPhase.CANCELLING ||
            this == RunProgressPhase.FORCE_STOPPING

    private fun RunProgressPhase.notificationLabel(): String =
        when (this) {
            RunProgressPhase.IDLE -> "Waiting"
            RunProgressPhase.PREPARING -> "Preparing backup"
            RunProgressPhase.DRY_RUN -> "Estimating transfer size"
            RunProgressPhase.RUNNING_RSYNC -> "Running rsync"
            RunProgressPhase.UPLOADING_STATUS -> "Uploading backup status"
            RunProgressPhase.CANCELLING -> "Cancelling backup"
            RunProgressPhase.FORCE_STOPPING -> "Force stopping backup"
            RunProgressPhase.COMPLETED -> "Backup completed"
            RunProgressPhase.FAILED -> "Backup failed"
            RunProgressPhase.CANCELLED -> "Backup cancelled"
        }

    private fun resultNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(
            RESULT_NOTIFICATION_ID,
            NotificationCompat.Builder(this, CHANNEL_RESULT)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle("Backup result")
                .setContentText(text)
                .setAutoCancel(true)
                .setContentIntent(openAppIntent())
                .build(),
        )
    }

    private fun tailscaleProblemNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(
            TAILSCALE_NOTIFICATION_ID,
            NotificationCompat.Builder(this, CHANNEL_TAILSCALE)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle("Tailscale setup problem")
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .setContentIntent(openAppIntent(TAILSCALE_SCREEN))
                .addAction(0, "Open setup", openAppIntent(TAILSCALE_SCREEN))
                .build(),
        )
    }

    private fun openAppIntent(screenName: String? = null): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            screenName?.let { putExtra(MainActivity.EXTRA_START_SCREEN, it) }
        }
        return PendingIntent.getActivity(
            this,
            screenName?.hashCode() ?: 1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun BackupLog.isTailscaleProblem(): Boolean {
        if (status != RunStatus.FAILED) return false
        val text = "$summary\n$raw".lowercase()
        return text.contains("tailscale") || text.contains("tsnet") || text.contains("proxycommand")
    }

    private fun serviceIntent(action: String): PendingIntent =
        PendingIntent.getService(
            this,
            action.hashCode(),
            Intent(this, BackupService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun runAnywayIntent(profileId: String): PendingIntent =
        PendingIntent.getService(
            this,
            (ACTION_RUN_ANYWAY + profileId).hashCode(),
            Intent(this, BackupService::class.java)
                .setAction(ACTION_RUN_ANYWAY)
                .putExtra(EXTRA_PROFILE_ID, profileId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    companion object {
        private const val ACTION_RUN = "com.ttv20.rsyncbackup.RUN"
        private const val ACTION_RUN_SCHEDULED = "com.ttv20.rsyncbackup.RUN_SCHEDULED"
        private const val ACTION_RUN_ANYWAY = "com.ttv20.rsyncbackup.RUN_ANYWAY"
        private const val ACTION_CANCEL = "com.ttv20.rsyncbackup.CANCEL"
        private const val ACTION_FORCE_STOP = "com.ttv20.rsyncbackup.FORCE_STOP"
        private const val EXTRA_PROFILE_ID = "profileId"
        private const val NOTIFICATION_ID = 100
        private const val RESULT_NOTIFICATION_ID = 101
        private const val CONSTRAINT_NOTIFICATION_ID = 102
        private const val TAILSCALE_NOTIFICATION_ID = 103
        private const val SCHEDULED_BLOCKED_NOTIFICATION_ID = 104
        private const val CANCEL_GRACE_MS = 10_000L
        private const val TAILSCALE_SCREEN = "Tailscale"

        const val CHANNEL_RUNNING = "running-backup"
        const val CHANNEL_RESULT = "backup-result"
        const val CHANNEL_TAILSCALE = "tailscale-problem"
        const val CHANNEL_CONSTRAINTS = "constraint-warning"

        fun start(context: Context, profileId: String) {
            startWithAction(context, profileId, ACTION_RUN)
        }

        fun startScheduled(context: Context, profileId: String) {
            startWithAction(context, profileId, ACTION_RUN_SCHEDULED)
        }

        fun cancel(context: Context) {
            startServiceAction(context, ACTION_CANCEL)
        }

        fun forceStop(context: Context) {
            startServiceAction(context, ACTION_FORCE_STOP)
        }

        fun notifyConstraintWarning(
            context: Context,
            profile: BackupProfile,
            failures: List<ConstraintFailure>,
            snapshot: ConstraintSnapshot? = null,
            trigger: BackupRunTrigger = BackupRunTrigger.MANUAL,
        ) {
            ensureNotificationChannels(context)
            val scheduled = trigger == BackupRunTrigger.AUTOMATIC
            val text = if (scheduled) {
                "${profile.name}: ${constraintFailureDetail(failures)}"
            } else {
                constraintFailureDetail(failures)
            }
            val title = if (scheduled) "Scheduled backup skipped" else "Backup constraints not met"
            val bigText = constraintBlockedDetailText(profile, failures, trigger, snapshot)
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.notify(
                CONSTRAINT_NOTIFICATION_ID,
                NotificationCompat.Builder(context, CHANNEL_CONSTRAINTS)
                    .setSmallIcon(R.drawable.ic_launcher)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
                    .setAutoCancel(true)
                    .setContentIntent(openAppIntent(context))
                    .let { builder ->
                        if (profile.constraints.manualOverrideAllowed) {
                            builder.addAction(0, "Run anyway", runAnywayIntent(context, profile.id))
                        } else {
                            builder
                        }
                    }
                    .build(),
            )
        }

        fun notifyScheduledConstraintWarning(
            context: Context,
            profile: BackupProfile,
            failures: List<ConstraintFailure>,
            snapshot: ConstraintSnapshot,
        ) {
            notifyConstraintWarning(
                context = context,
                profile = profile,
                failures = failures,
                snapshot = snapshot,
                trigger = BackupRunTrigger.AUTOMATIC,
            )
        }

        fun notifyScheduledStartBlocked(context: Context, profile: BackupProfile, reason: String) {
            ensureNotificationChannels(context)
            val text = "Android deferred the scheduled start for ${profile.name}: $reason"
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.notify(
                SCHEDULED_BLOCKED_NOTIFICATION_ID,
                NotificationCompat.Builder(context, CHANNEL_RESULT)
                    .setSmallIcon(R.drawable.ic_launcher)
                    .setContentTitle("Scheduled backup waiting")
                    .setContentText(text)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                    .setAutoCancel(true)
                    .setContentIntent(openAppIntent(context))
                    .addAction(0, "Run now", runAnywayIntent(context, profile.id))
                    .build(),
            )
        }

        fun ensureNotificationChannels(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_RUNNING, "Running backup", NotificationManager.IMPORTANCE_LOW),
            )
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_RESULT, "Backup result", NotificationManager.IMPORTANCE_DEFAULT),
            )
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_TAILSCALE, "Tailscale auth/state problem", NotificationManager.IMPORTANCE_DEFAULT),
            )
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_CONSTRAINTS, "Constraint warning", NotificationManager.IMPORTANCE_DEFAULT),
            )
        }

        private fun startWithAction(context: Context, profileId: String, action: String) {
            val intent = Intent(context, BackupService::class.java)
                .setAction(action)
                .putExtra(EXTRA_PROFILE_ID, profileId)
            startService(context, intent)
        }

        private fun startServiceAction(context: Context, action: String) {
            startService(context, Intent(context, BackupService::class.java).setAction(action))
        }

        private fun startService(context: Context, intent: Intent) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        private fun openAppIntent(context: Context, screenName: String? = null): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                screenName?.let { putExtra(MainActivity.EXTRA_START_SCREEN, it) }
            }
            return PendingIntent.getActivity(
                context,
                screenName?.hashCode() ?: 1,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private fun runAnywayIntent(context: Context, profileId: String): PendingIntent =
            PendingIntent.getService(
                context,
                (ACTION_RUN_ANYWAY + profileId).hashCode(),
                Intent(context, BackupService::class.java)
                    .setAction(ACTION_RUN_ANYWAY)
                    .putExtra(EXTRA_PROFILE_ID, profileId),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
    }
}
