package com.vfxsal.filemanager.util

import java.text.DateFormat
import java.util.Date
import kotlin.math.ln
import kotlin.math.pow

object FormatUtils {

    /** Renders a byte count as e.g. "3.2 MB", "820 KB", "1.1 GB". */
    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (ln(bytes.toDouble()) / ln(1024.0)).toInt().coerceIn(0, units.size - 1)
        val value = bytes / 1024.0.pow(digitGroups.toDouble())
        return if (digitGroups == 0) {
            "$bytes ${units[digitGroups]}"
        } else {
            String.format("%.1f %s", value, units[digitGroups])
        }
    }

    fun formatDate(millis: Long): String =
        DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(millis))

    fun formatDateTime(millis: Long): String =
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis))

    /** Renders milliseconds of media duration as "mm:ss" or "h:mm:ss". */
    fun formatDuration(millis: Long): String {
        val totalSeconds = millis / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%d:%02d", minutes, seconds)
        }
    }
}
