package com.ttv20.rsyncbackup

import android.app.Application
import com.ttv20.rsyncbackup.backup.NativeBinaryManager
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
        repository = AppRepository(File(filesDir, "app-state.json"), defaultExcludes).also {
            it.loadBlocking()
        }
        secretStore = AndroidKeystoreSecretStore(this)
        NativeBinaryManager(this).ensureInstalled()
    }
}
