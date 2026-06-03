package com.ttv20.rsyncbackup.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

@Serializable
data class BackupStatusMarker(
    val profileId: String,
    val profileName: String,
    val phoneHostname: String,
    val appVersion: String,
    val sourcePath: String,
    val targetHostUsed: String,
    val targetMode: TargetMode,
    val status: String,
    val finishTime: String,
    val rsyncExitCode: Int,
)

fun BackupStatusMarker.toJson(): String = ExportCodec.json.encodeToString(this)
