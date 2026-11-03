package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.widget.DayZServerWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class DayZServerRepository private constructor() {

    private val client = DayZA2SClient()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    private val _statusFlow = MutableStateFlow(DayZServerStatus.initial())
    val statusFlow: StateFlow<DayZServerStatus> = _statusFlow.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    fun initFromCache(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val hasCached = prefs.getBoolean(KEY_HAS_CACHE, false)
        if (hasCached) {
            val cached = DayZServerStatus(
                isOnline = prefs.getBoolean(KEY_IS_ONLINE, false),
                serverName = prefs.getString(KEY_NAME, "HOD DayZ Heaven S16 | PVE ONLY") ?: "HOD DayZ Heaven S16 | PVE ONLY",
                players = prefs.getInt(KEY_PLAYERS, 0),
                maxPlayers = prefs.getInt(KEY_MAX_PLAYERS, 60),
                map = prefs.getString(KEY_MAP, "ChernarusPlus") ?: "ChernarusPlus",
                inGameTime = prefs.getString(KEY_TIME, "08:00") ?: "08:00",
                inGamePhase = prefs.getString(KEY_PHASE, "Day") ?: "Day",
                rawKeywords = prefs.getString(KEY_KEYWORDS, "") ?: "",
                pingMs = prefs.getLong(KEY_PING, 45L),
                lastUpdatedMillis = prefs.getLong(KEY_LAST_UPDATED, System.currentTimeMillis()),
                errorMessage = prefs.getString(KEY_ERROR, null)
            )
            _statusFlow.value = cached
        }
    }

    suspend fun refresh(context: Context? = null): DayZServerStatus {
        return mutex.withLock {
            _isRefreshing.value = true
            val result = try {
                client.query()
            } catch (e: Exception) {
                DayZServerStatus.offline()
            }
            _statusFlow.value = result
            _isRefreshing.value = false

            if (context != null) {
                saveToCache(context, result)
                DayZServerWidgetProvider.updateAllWidgets(context, result)
            }
            result
        }
    }

    private fun saveToCache(context: Context, status: DayZServerStatus) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().apply {
                putBoolean(KEY_HAS_CACHE, true)
                putBoolean(KEY_IS_ONLINE, status.isOnline)
                putString(KEY_NAME, status.serverName)
                putInt(KEY_PLAYERS, status.players)
                putInt(KEY_MAX_PLAYERS, status.maxPlayers)
                putString(KEY_MAP, status.map)
                putString(KEY_TIME, status.inGameTime)
                putString(KEY_PHASE, status.inGamePhase)
                putString(KEY_KEYWORDS, status.rawKeywords)
                putLong(KEY_PING, status.pingMs)
                putLong(KEY_LAST_UPDATED, status.lastUpdatedMillis)
                putString(KEY_ERROR, status.errorMessage)
                apply()
            }
        } catch (_: Exception) {}
    }

    companion object {
        private const val PREFS_NAME = "dayz_server_cache"
        private const val KEY_HAS_CACHE = "has_cache"
        private const val KEY_IS_ONLINE = "is_online"
        private const val KEY_NAME = "name"
        private const val KEY_PLAYERS = "players"
        private const val KEY_MAX_PLAYERS = "max_players"
        private const val KEY_MAP = "map"
        private const val KEY_TIME = "time"
        private const val KEY_PHASE = "phase"
        private const val KEY_KEYWORDS = "keywords"
        private const val KEY_PING = "ping"
        private const val KEY_LAST_UPDATED = "last_updated"
        private const val KEY_ERROR = "error"

        val instance: DayZServerRepository by lazy { DayZServerRepository() }
    }
}
