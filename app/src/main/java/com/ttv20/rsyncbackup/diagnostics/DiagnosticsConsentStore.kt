package com.ttv20.rsyncbackup.diagnostics

import android.content.Context
import java.util.UUID

private const val PREFS_NAME = "diagnostics-consent"
private const val KEY_ENABLED = "enabled"
private const val KEY_INSTALL_ID = "install_id"

class DiagnosticsConsentStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun consent(): Boolean? =
        if (prefs.contains(KEY_ENABLED)) {
            prefs.getBoolean(KEY_ENABLED, false)
        } else {
            null
        }

    fun isEnabled(): Boolean = diagnosticsConsentAllowsNetwork(consent())

    fun setConsent(consent: Boolean?) {
        prefs.edit().apply {
            if (consent == null) {
                remove(KEY_ENABLED)
                remove(KEY_INSTALL_ID)
            } else {
                putBoolean(KEY_ENABLED, consent)
                if (!consent) {
                    remove(KEY_INSTALL_ID)
                }
            }
        }.apply()
    }

    fun installIdOrCreate(): String? {
        if (!isEnabled()) return null
        prefs.getString(KEY_INSTALL_ID, null)?.let { return it }
        val id = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_INSTALL_ID, id).apply()
        return id
    }
}
