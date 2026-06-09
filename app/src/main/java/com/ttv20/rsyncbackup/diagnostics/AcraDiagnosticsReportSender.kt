package com.ttv20.rsyncbackup.diagnostics

import android.content.Context
import org.acra.config.CoreConfiguration
import org.acra.data.CrashReportData
import org.acra.sender.ReportSender
import org.acra.sender.ReportSenderException
import org.acra.sender.ReportSenderFactory

class AcraOpenObserveReportSenderFactory : ReportSenderFactory {
    override fun create(context: Context, config: CoreConfiguration): ReportSender =
        AcraOpenObserveReportSender()
}

class AcraOpenObserveReportSender(
    private val shouldSend: (Context?) -> Boolean = { context ->
        context != null &&
            DiagnosticsConsentStore(context).isEnabled() &&
            DiagnosticsEndpoints.crashEndpoint() != null
    },
    private val installId: (Context?) -> String? = { context ->
        context?.let { DiagnosticsConsentStore(it).installIdOrCreate() }
    },
    private val endpointProvider: () -> DiagnosticsEndpoint? = { DiagnosticsEndpoints.crashEndpoint() },
    private val postJson: (String, String, String?) -> Unit = { url, body, authorizationHeader ->
        UrlConnectionDiagnosticsHttpPoster.postJson(url, body, authorizationHeader)
    },
) : ReportSender {
    override fun send(context: Context, errorContent: CrashReportData) {
        sendInternal(context, errorContent)
    }

    internal fun sendForTest(context: Context?, errorContent: CrashReportData) {
        sendInternal(context, errorContent)
    }

    private fun sendInternal(context: Context?, errorContent: CrashReportData) {
        if (!shouldSend(context)) return
        val actualContext = context ?: return
        val id = installId(context) ?: return
        val endpoint = endpointProvider() ?: return
        val body = DiagnosticsPayloads.crashJson(actualContext, id, errorContent)
        try {
            postJson(endpoint.url, body, endpoint.authorizationHeader)
        } catch (error: Exception) {
            throw ReportSenderException("Failed to send diagnostics crash report", error)
        }
    }
}
