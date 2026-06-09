package com.ttv20.rsyncbackup.diagnostics

import android.content.Context
import android.content.pm.PackageInfo
import android.os.Build
import com.ttv20.rsyncbackup.BuildConfig
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.acra.ReportField
import org.acra.data.CrashReportData
import java.time.Instant

internal object DiagnosticsPayloads {
    fun eventJson(
        context: Context,
        installId: String,
        eventName: String,
        attributes: Map<String, Any?>,
        timestamp: String = Instant.now().toString(),
    ): String =
        buildJsonObject {
            putCommonFields(context, installId)
            put("schema_version", 1)
            put("timestamp", timestamp)
            put("record_type", "event")
            put("event_name", DiagnosticsSanitizer.scrubAttributeText(eventName))
            put("attributes", attributesJson(DiagnosticsSanitizer.sanitizeAttributes(attributes)))
        }.toString()

    fun crashJson(
        context: Context,
        installId: String,
        report: CrashReportData,
        timestamp: String = Instant.now().toString(),
    ): String =
        buildJsonObject {
            putCommonFields(context, installId)
            put("schema_version", 1)
            put("timestamp", timestamp)
            put("record_type", "crash")
            put("crash_id", DiagnosticsSanitizer.scrubAttributeText(report.getString(ReportField.REPORT_ID)))
            put("crash_date", DiagnosticsSanitizer.scrubAttributeText(report.getString(ReportField.USER_CRASH_DATE)))
            put("stack_trace_hash", DiagnosticsSanitizer.scrubAttributeText(report.getString(ReportField.STACK_TRACE_HASH)))
            put("stack_trace", DiagnosticsSanitizer.scrubCrashText(report.getString(ReportField.STACK_TRACE)))
            put("is_silent", report[ReportField.IS_SILENT.toString()] as? Boolean ?: false)
            put(
                "custom_data",
                attributesJson(
                    DiagnosticsSanitizer.sanitizeAttributes(
                        customData(report[ReportField.CUSTOM_DATA.toString()]),
                    ),
                ),
            )
        }.toString()

    private fun JsonObjectBuilder.putCommonFields(context: Context, installId: String) {
        val packageInfo = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0)
        }.getOrNull()
        put("app_version", packageInfo?.versionName ?: BuildConfig.VERSION_NAME)
        put("app_version_code", packageInfo?.versionCodeCompat() ?: BuildConfig.VERSION_CODE.toLong())
        put("build_channel", BuildConfig.BUILD_CHANNEL)
        put("debug_build", BuildConfig.DEBUG)
        put("fdroid_build", BuildConfig.IS_FDROID_BUILD)
        put("android_version", Build.VERSION.RELEASE.orEmpty())
        put("sdk_level", Build.VERSION.SDK_INT)
        put("device_model", DiagnosticsSanitizer.scrubAttributeText("${Build.MANUFACTURER} ${Build.MODEL}".trim()))
        put("install_id", installId)
    }

    @Suppress("DEPRECATION")
    private fun PackageInfo.versionCodeCompat(): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            longVersionCode
        } else {
            versionCode.toLong()
        }

    private fun attributesJson(attributes: Map<String, Any>): JsonObject =
        buildJsonObject {
            attributes.forEach { (key, value) ->
                put(key, value.toJsonPrimitive())
            }
        }

    private fun Any.toJsonPrimitive(): JsonPrimitive =
        when (this) {
            is Boolean -> JsonPrimitive(this)
            is Int -> JsonPrimitive(this)
            is Long -> JsonPrimitive(this)
            is Double -> JsonPrimitive(this)
            is Float -> JsonPrimitive(this)
            is Number -> JsonPrimitive(this.toDouble())
            else -> JsonPrimitive(this.toString())
        }

    private fun customData(value: Any?): Map<String, Any?> {
        val raw = value?.toString().orEmpty()
        if (raw.isBlank()) return emptyMap()
        return raw
            .lineSequence()
            .mapNotNull { line ->
                val key = line.substringBefore("=", "").trim()
                val item = line.substringAfter("=", "").trim()
                if (key.isBlank() || item.isBlank()) null else key to item
            }
            .toMap()
    }
}

private typealias JsonObjectBuilder = kotlinx.serialization.json.JsonObjectBuilder
