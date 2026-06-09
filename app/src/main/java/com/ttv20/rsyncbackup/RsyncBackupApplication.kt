package com.ttv20.rsyncbackup

import android.app.Application
import android.content.Context
import com.ttv20.rsyncbackup.backup.NativeBinaryManager
import com.ttv20.rsyncbackup.diagnostics.AcraOpenObserveReportSenderFactory
import com.ttv20.rsyncbackup.diagnostics.DiagnosticsController
import com.ttv20.rsyncbackup.diagnostics.DiagnosticsAttributes
import com.ttv20.rsyncbackup.model.GlobalSshKeySettings
import com.ttv20.rsyncbackup.model.suggestedSshKeyName
import com.ttv20.rsyncbackup.model.withDetectedPhoneHostname
import com.ttv20.rsyncbackup.settings.DeviceHostnameReader
import com.ttv20.rsyncbackup.scheduling.BackupScheduler
import com.ttv20.rsyncbackup.ssh.SshKeyManager
import com.ttv20.rsyncbackup.storage.AndroidKeystoreSecretStore
import com.ttv20.rsyncbackup.storage.AppRepository
import com.ttv20.rsyncbackup.storage.SecretStore
import org.acra.ReportField
import org.acra.data.StringFormat
import org.acra.ktx.initAcra
import org.acra.plugins.SimplePluginLoader
import java.io.File

class RsyncBackupApplication : Application() {
    lateinit var repository: AppRepository
        private set

    lateinit var secretStore: SecretStore
        private set

    lateinit var diagnostics: DiagnosticsController
        private set

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        initAcra {
            buildConfigClass = BuildConfig::class.java
            reportFormat = StringFormat.JSON
            reportContent = listOf(
                ReportField.REPORT_ID,
                ReportField.APP_VERSION_CODE,
                ReportField.APP_VERSION_NAME,
                ReportField.PACKAGE_NAME,
                ReportField.PHONE_MODEL,
                ReportField.ANDROID_VERSION,
                ReportField.STACK_TRACE,
                ReportField.STACK_TRACE_HASH,
                ReportField.USER_CRASH_DATE,
                ReportField.CUSTOM_DATA,
                ReportField.IS_SILENT,
            )
            pluginLoader = SimplePluginLoader(AcraOpenObserveReportSenderFactory::class.java)
        }
    }

    override fun onCreate() {
        super.onCreate()
        diagnostics = DiagnosticsController(this).also { it.initialize() }
        val defaultExcludes = resources.openRawResource(R.raw.default_android_excludes)
            .bufferedReader()
            .use { it.readText().trimEnd() }
        secretStore = AndroidKeystoreSecretStore(this)
        repository = AppRepository(File(filesDir, "app-state.json"), defaultExcludes).also {
            it.loadBlocking()
            DeviceHostnameReader.read(this)?.let { deviceHostname ->
                it.update { state -> state.withDetectedPhoneHostname(deviceHostname) }
            }
            ensureGlobalSshKey(it)
        }
        diagnostics.syncConsent(repository.state.value.settings.diagnosticsEnabled)
        rescheduleEnabledBackups()
        val nativeInstall = NativeBinaryManager(this).ensureInstalled()
        if (!nativeInstall.isComplete) {
            diagnostics.trackEvent(
                "native_payload_install_failed",
                mapOf(DiagnosticsAttributes.NATIVE_MISSING_COMPONENTS to nativeInstall.missing),
            )
        }
    }

    override fun onTerminate() {
        diagnostics.shutdown()
        super.onTerminate()
    }

    fun rescheduleEnabledBackups() {
        val scheduler = BackupScheduler(this)
        repository.state.value.profiles.forEach { profile ->
            scheduler.schedule(profile)
        }
    }

    private fun ensureGlobalSshKey(repository: AppRepository) {
        val state = repository.state.value
        val settings = state.sshKeySettings
        val hasUsablePrivateKey = settings.privateKeySecretAlias
            ?.let { alias -> secretStore.get(alias) != null }
            ?: false
        if (hasUsablePrivateKey && settings.publicKey != null) return

        val generated = SshKeyManager(secretStore).generateEd25519(
            keyName = suggestedSshKeyName(state.settings.phoneHostname),
        )
        repository.update { state ->
            state.copy(
                sshKeySettings = GlobalSshKeySettings(
                    publicKey = generated.publicKey,
                    privateKeySecretAlias = generated.privateKeyAlias,
                    generatedAt = generated.generatedAt,
                ),
            )
        }
        diagnostics.trackEvent(
            "ssh_key_generated",
            mapOf(DiagnosticsAttributes.SOURCE to "startup"),
        )
    }
}
