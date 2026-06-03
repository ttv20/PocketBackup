package com.ttv20.rsyncbackup

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import com.ttv20.rsyncbackup.ui.RsyncBackupApp

class MainActivity : ComponentActivity() {
    private data class ScreenRequest(val name: String?, val sequence: Int)

    private var requestSequence = 0
    private val requestedScreen = mutableStateOf(ScreenRequest(name = null, sequence = 0))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyRequestedScreen(intent)
        val app = application as RsyncBackupApplication
        setContent {
            val screenRequest = requestedScreen.value
            RsyncBackupApp(
                repository = app.repository,
                secretStore = app.secretStore,
                requestedScreenName = screenRequest.name,
                requestedScreenRequestId = screenRequest.sequence,
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyRequestedScreen(intent)
    }

    override fun onResume() {
        super.onResume()
        applyRequestedScreen(intent)
    }

    private fun applyRequestedScreen(intent: Intent) {
        val screenName = intent.getStringExtra(EXTRA_START_SCREEN) ?: return
        requestedScreen.value = ScreenRequest(
            name = screenName,
            sequence = ++requestSequence,
        )
    }

    companion object {
        const val EXTRA_START_SCREEN = "screen"
    }
}
