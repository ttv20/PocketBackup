package com.ttv20.rsyncbackup.diagnostics

import android.content.Context
import com.ttv20.rsyncbackup.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import sh.measure.android.Measure
import sh.measure.android.attributes.AttributesBuilder
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

interface DiagnosticsReporter {
    fun trackEvent(name: String, attributes: Map<String, Any?> = emptyMap())
    fun trackHandledException(error: Throwable, attributes: Map<String, Any?> = emptyMap())
}

object NoopDiagnosticsReporter : DiagnosticsReporter {
    override fun trackEvent(name: String, attributes: Map<String, Any?>) = Unit
    override fun trackHandledException(error: Throwable, attributes: Map<String, Any?>) = Unit
}

class CompositeDiagnosticsReporter(private val reporters: List<DiagnosticsReporter>) : DiagnosticsReporter {
    override fun trackEvent(name: String, attributes: Map<String, Any?>) {
        reporters.forEach { it.trackEvent(name, attributes) }
    }

    override fun trackHandledException(error: Throwable, attributes: Map<String, Any?>) {
        reporters.forEach { it.trackHandledException(error, attributes) }
    }
}

class MeasureDiagnosticsReporter : DiagnosticsReporter {
    override fun trackEvent(name: String, attributes: Map<String, Any?>) {
        Measure.trackEvent(
            name = DiagnosticsSanitizer.scrubAttributeText(name),
            attributes = attributes.toMeasureAttributes(),
        )
    }

    override fun trackHandledException(error: Throwable, attributes: Map<String, Any?>) {
        Measure.trackHandledException(
            throwable = SanitizedHandledException(error),
            attributes = attributes.toMeasureAttributes(),
        )
    }

    private fun Map<String, Any?>.toMeasureAttributes(): MutableMap<String, sh.measure.android.attributes.AttributeValue> {
        val builder = AttributesBuilder()
        DiagnosticsSanitizer.sanitizeAttributes(this).forEach { (key, value) ->
            when (value) {
                is Boolean -> builder.put(key, value)
                is Int -> builder.put(key, value)
                is Long -> builder.put(key, value)
                is Double -> builder.put(key, value)
                is Float -> builder.put(key, value)
                else -> builder.put(key, value.toString())
            }
        }
        return builder.build()
    }

    private class SanitizedHandledException(original: Throwable) : RuntimeException(
        "${original.javaClass.name}: ${DiagnosticsSanitizer.scrubAttributeText(original.message)}",
    ) {
        init {
            stackTrace = original.stackTrace
        }
    }
}

class OpenObserveDiagnosticsReporter(
    private val context: Context,
    private val consentStore: DiagnosticsConsentStore,
    private val endpointProvider: () -> DiagnosticsEndpoint? = { DiagnosticsEndpoints.eventEndpoint() },
    private val scope: CoroutineScope,
    private val poster: DiagnosticsHttpPoster = UrlConnectionDiagnosticsHttpPoster,
) : DiagnosticsReporter {
    override fun trackEvent(name: String, attributes: Map<String, Any?>) {
        val installId = consentStore.installIdOrCreate() ?: return
        val endpoint = endpointProvider() ?: return
        val payload = DiagnosticsPayloads.eventJson(
            context = context,
            installId = installId,
            eventName = name,
            attributes = attributes,
        )
        scope.launch {
            runCatching {
                poster.postJson(endpoint.url, payload, endpoint.authorizationHeader)
            }
        }
    }

    override fun trackHandledException(error: Throwable, attributes: Map<String, Any?>) {
        trackEvent(
            name = "handled_exception",
            attributes = attributes + (
                DiagnosticsAttributes.EXCEPTION_TYPE to DiagnosticsSanitizer.scrubAttributeText(error.javaClass.name)
                ),
        )
    }
}

interface DiagnosticsHttpPoster {
    @Throws(IOException::class)
    fun postJson(url: String, body: String, authorizationHeader: String? = null)
}

object UrlConnectionDiagnosticsHttpPoster : DiagnosticsHttpPoster {
    override fun postJson(url: String, body: String, authorizationHeader: String?) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 8_000
            readTimeout = 8_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "PocketBackup/${BuildConfig.VERSION_NAME} (${BuildConfig.BUILD_CHANNEL})")
            authorizationHeader?.takeIf { it.isNotBlank() }?.let {
                setRequestProperty("Authorization", it)
            }
        }
        try {
            connection.outputStream.use { output ->
                output.write(body.toByteArray(Charsets.UTF_8))
            }
            val code = connection.responseCode
            if (code !in 200..299) {
                throw IOException("Diagnostics endpoint returned HTTP $code")
            }
        } finally {
            connection.disconnect()
        }
    }
}
