package com.example.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DayZTimeHelper {
    private const val THREE_HOURS_MS = 3L * 3600L * 1000L

    /**
     * Calculates milliseconds remaining until the next 3-hour restart boundary.
     * The server restarts every 3 hours on the hour (00:00, 03:00, 06:00, 09:00, 12:00, 15:00, 18:00, 21:00 UTC).
     */
    fun getRestartCountdownMs(nowMillis: Long = System.currentTimeMillis()): Long {
        val elapsedInCycle = nowMillis % THREE_HOURS_MS
        val remaining = (THREE_HOURS_MS - elapsedInCycle) % THREE_HOURS_MS
        return remaining
    }

    /**
     * Formats remaining ms into HH:mm:ss
     */
    fun formatCountdown(remainingMs: Long): String {
        val totalSeconds = (remainingMs / 1000L).coerceAtLeast(0L)
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    }

    /**
     * Formats timestamp into HH:mm:ss
     */
    fun formatTimestamp(millis: Long): String {
        if (millis <= 0) return "--:--:--"
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(millis))
    }

    /**
     * Parses in-game time from DayZ A2S keywords/tags.
     * In DayZ, server tags report `etm8.000000` (float representing hour of day).
     * Also checks alternative patterns: `etm:HH:MM`, `time:HH:MM`, or numeric hours.
     */
    fun parseInGameTime(keywords: String): Pair<String, String> {
        if (keywords.isBlank()) return Pair("08:00", "Day")

        // 1. Try etm float format: "etm8.000000" or "etm14.500000"
        val etmFloatRegex = Regex("""etm([0-9]+(?:\.[0-9]+)?)""", RegexOption.IGNORE_CASE)
        val etmMatch = etmFloatRegex.find(keywords)
        if (etmMatch != null) {
            val hourVal = etmMatch.groupValues[1].toFloatOrNull()
            if (hourVal != null && hourVal in 0f..24f) {
                val hours = hourVal.toInt() % 24
                val minutes = (((hourVal - hours) * 60f) + 0.5f).toInt() % 60
                val timeStr = String.format(Locale.US, "%02d:%02d", hours, minutes)
                val phase = getPhaseForHour(hours)
                return Pair(timeStr, phase)
            }
        }

        // 2. Try standard colon format: "etm:14:30" or "time:12:00"
        val colonRegex = Regex("""(?:etm|time|t)[:=]\s*(\d{1,2}):(\d{2})""", RegexOption.IGNORE_CASE)
        val colonMatch = colonRegex.find(keywords)
        if (colonMatch != null) {
            val hours = colonMatch.groupValues[1].toIntOrNull() ?: 12
            val minutes = colonMatch.groupValues[2].toIntOrNull() ?: 0
            val timeStr = String.format(Locale.US, "%02d:%02d", hours, minutes)
            val phase = getPhaseForHour(hours)
            return Pair(timeStr, phase)
        }

        // 3. Check for day/night keyword tags
        if (keywords.contains("night", ignoreCase = true)) {
            return Pair("01:00", "Night")
        }
        if (keywords.contains("day", ignoreCase = true)) {
            return Pair("12:00", "Day")
        }

        return Pair("08:00", "Day")
    }

    private fun getPhaseForHour(hour: Int): String {
        return when (hour) {
            in 6..7 -> "Dawn"
            in 8..19 -> "Day"
            in 20..21 -> "Dusk"
            else -> "Night"
        }
    }
}
