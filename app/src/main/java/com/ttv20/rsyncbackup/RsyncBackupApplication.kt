package com.ttv20.rsyncbackup

import android.app.Application
import com.ttv20.rsyncbackup.backup.NativeBinaryManager
import com.ttv20.rsyncbackup.model.GlobalSshKeySettings
import com.ttv20.rsyncbackup.model.suggestedSshKeyName
import com.ttv20.rsyncbackup.model.withDetectedPhoneHostname
import com.ttv20.rsyncbackup.settings.DeviceHostnameReader
import com.ttv20.rsyncbackup.ssh.SshKeyManager
import com.ttv20.rsyncbackup.storage.AndroidKeystoreSecretStore
import com.ttv20.rsyncbackup.storage.AppRepository
import com.ttv20.rsyncbackup.storage.SecretStore
import java.io.File

class RsyncBackupApplication : Application() {
    lateinit var repository: AppRepository
        private set

    lateinit var secretStore: SecretStore
        private set

    override fun onCreate() {
        super.onCreate()
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
        NativeBinaryManager(this).ensureInstalled()
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
    }
}
