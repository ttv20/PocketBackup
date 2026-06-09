package com.ttv20.rsyncbackup.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ttv20.rsyncbackup.RsyncBackupApplication

class ScheduleRestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? RsyncBackupApplication ?: return
        app.rescheduleEnabledBackups()
    }
}
