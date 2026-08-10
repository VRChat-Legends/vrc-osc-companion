package com.vrchatlegends.osccompanion.vrcl

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class UsageRange(val id: String, val label: String) {
    TODAY("today", "Today"),
    WEEK("week", "This week"),
    MONTH("month", "This month"),
    ALL("all", "All time"),
}

data class LeaderboardState(
    val entries: List<VrclUsageEntry> = emptyList(),
    val range: UsageRange = UsageRange.ALL,
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val error: String? = null,
) {
    val viewer: VrclUsageEntry? get() = entries.firstOrNull { it.isViewer }
}

/** Time-in-app ranking. Only Legends with a linked profile are tracked by the backend. */
class LeaderboardRepository(
    private val scope: CoroutineScope,
    private val client: VrclClient,
) {
    private val _state = MutableStateFlow(LeaderboardState())
    val state: StateFlow<LeaderboardState> = _state.asStateFlow()

    fun loadOnce() {
        if (_state.value.loaded || _state.value.loading) return
        refresh()
    }

    fun selectRange(range: UsageRange) {
        if (_state.value.range == range) return
        _state.update { it.copy(range = range) }
        refresh()
    }

    fun refresh() {
        if (_state.value.loading) return
        scope.launch {
            _state.update { it.copy(loading = true, error = null) }
            client.usageLeaderboard(_state.value.range.id)
                .onSuccess { entries ->
                    _state.update { it.copy(entries = entries, loading = false, loaded = true, error = null) }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            loading = false,
                            loaded = true,
                            error = error.message ?: "Could not load the leaderboard",
                        )
                    }
                }
        }
    }
}

/** "4h 12m", "12m", "48s". Kept out of the composable so it can be unit tested. */
fun formatUsage(seconds: Long): String {
    if (seconds < 60) return "${seconds}s"
    val minutes = seconds / 60
    if (minutes < 60) return "${minutes}m"
    val hours = minutes / 60
    val remainder = minutes % 60
    if (hours < 24) return if (remainder == 0L) "${hours}h" else "${hours}h ${remainder}m"
    val days = hours / 24
    val leftoverHours = hours % 24
    return if (leftoverHours == 0L) "${days}d" else "${days}d ${leftoverHours}h"
}
