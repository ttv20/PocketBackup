@file:OptIn(
    androidx.compose.animation.ExperimentalAnimationApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package com.ttv20.rsyncbackup.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.scale
import com.ttv20.rsyncbackup.model.RunProgressPhase

internal fun phaseLabel(phase: RunProgressPhase): String =
    phase.name.lowercase().replace('_', ' ')

internal fun RunProgressPhase.hasActiveMotion(): Boolean =
    when (this) {
        RunProgressPhase.PREPARING,
        RunProgressPhase.DRY_RUN,
        RunProgressPhase.RUNNING_RSYNC,
        RunProgressPhase.UPLOADING_STATUS,
        RunProgressPhase.CANCELLING,
        RunProgressPhase.FORCE_STOPPING -> true
        RunProgressPhase.IDLE,
        RunProgressPhase.COMPLETED,
        RunProgressPhase.FAILED,
        RunProgressPhase.CANCELLED -> false
    }

@Composable
internal fun activePulseScale(active: Boolean): Float {
    if (!active) return 1f
    val transition = rememberInfiniteTransition(label = "active-pulse")
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.07f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "active-pulse-scale",
    )
    return scale
}

@Composable
internal fun activeRotationDegrees(active: Boolean): Float {
    if (!active) return 0f
    val transition = rememberInfiniteTransition(label = "active-rotation")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "active-rotation-degrees",
    )
    return rotation
}

internal fun RunProgressPhase.notificationLabel(): String =
    when (this) {
        RunProgressPhase.IDLE -> "Waiting"
        RunProgressPhase.PREPARING -> "Preparing backup"
        RunProgressPhase.DRY_RUN -> "Estimating transfer size"
        RunProgressPhase.RUNNING_RSYNC -> "Running rsync"
        RunProgressPhase.UPLOADING_STATUS -> "Uploading backup status"
        RunProgressPhase.CANCELLING -> "Cancelling backup"
        RunProgressPhase.FORCE_STOPPING -> "Force stopping backup"
        RunProgressPhase.COMPLETED -> "Backup completed"
        RunProgressPhase.FAILED -> "Backup failed"
        RunProgressPhase.CANCELLED -> "Backup cancelled"
    }
