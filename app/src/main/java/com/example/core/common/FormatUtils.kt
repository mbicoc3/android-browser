package com.example.core.common

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object FormatUtils {
    fun formatByteSize(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        var value = bytes.toDouble()
        var unitIdx = 0
        while (value >= 1024.0 && unitIdx < units.lastIndex) {
            value /= 1024.0
            unitIdx++
        }
        return if (unitIdx == 0) {
            "${bytes} B"
        } else {
            String.format(Locale.US, "%.1f %s", value, units[unitIdx])
        }
    }

    fun formatDateBucket(timestampMs: Long, nowMs: Long = System.currentTimeMillis()): String {
        val targetCal = Calendar.getInstance().apply { timeInMillis = timestampMs }
        val todayCal = Calendar.getInstance().apply { timeInMillis = nowMs }
        val yesterdayCal = Calendar.getInstance().apply {
            timeInMillis = nowMs
            add(Calendar.DAY_OF_YEAR, -1)
        }

        return when {
            isSameDay(targetCal, todayCal) -> "Today"
            isSameDay(targetCal, yesterdayCal) -> "Yesterday"
            else -> SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(timestampMs))
        }
    }

    fun formatTimeShort(timestampMs: Long): String =
        SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(timestampMs))

    private fun isSameDay(a: Calendar, b: Calendar): Boolean =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
            a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
}
