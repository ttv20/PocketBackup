package com.ttv20.rsyncbackup.diagnostics

import org.acra.data.CrashReportData
import org.junit.Assert.fail
import org.junit.Test

class AcraOpenObserveReportSenderTest {
    @Test
    fun dropsCrashReportWhenConsentIsDisabled() {
        val sender = AcraOpenObserveReportSender(
            shouldSend = { false },
            installId = { "install-id" },
            endpointProvider = {
                DiagnosticsEndpoint(
                    url = "https://diagnostics.example.test/api/default/pocketbackup/_json",
                    authorizationHeader = "Basic test",
                )
            },
            postJson = { _, _, _ -> fail("sender should not post when consent is disabled") },
        )

        sender.sendForTest(null, CrashReportData())
    }
}
