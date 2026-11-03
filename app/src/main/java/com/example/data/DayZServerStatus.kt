package com.example.data

/**
 * Data model representing the live state of the HOD DayZ Heaven server.
 */
data class DayZServerStatus(
    val isOnline: Boolean,
    val serverName: String = "HOD DayZ Heaven S16 | PVE ONLY",
    val players: Int = 0,
    val maxPlayers: Int = 60,
    val map: String = "ChernarusPlus",
    val inGameTime: String = "--:--",
    val inGamePhase: String = "",
    val rawKeywords: String = "",
    val pingMs: Long = 0,
    val lastUpdatedMillis: Long = System.currentTimeMillis(),
    val errorMessage: String? = null
) {
    val displayPlayers: String
        get() = if (isOnline) "$players / $maxPlayers" else "-- / $maxPlayers"

    val occupancyPercent: Int
        get() = if (maxPlayers > 0) ((players.toFloat() / maxPlayers) * 100).toInt() else 0

    companion object {
        fun initial(): DayZServerStatus = DayZServerStatus(
            isOnline = false,
            serverName = "HOD DayZ Heaven S16 | PVE ONLY",
            players = 0,
            maxPlayers = 60,
            map = "ChernarusPlus",
            inGameTime = "--:--",
            inGamePhase = "",
            errorMessage = "CONNECTING..."
        )

        fun offline(reason: String = "SERVER RESTARTING OR OFFLINE"): DayZServerStatus = DayZServerStatus(
            isOnline = false,
            serverName = "HOD DayZ Heaven S16 | PVE ONLY",
            players = 0,
            maxPlayers = 60,
            map = "ChernarusPlus",
            inGameTime = "--:--",
            inGamePhase = "",
            errorMessage = reason,
            lastUpdatedMillis = System.currentTimeMillis()
        )
    }
}
