package com.m8droid.emulator

/**
 * Debounces project autosaves so bursts of edits produce one disk write after
 * the song has been quiet for [delayMs]. Pure state object for deterministic
 * unit tests; M8ViewModel owns the coroutine/timer that calls into it.
 */
class ProjectAutosaveDebouncer(
    val delayMs: Long = DEFAULT_DELAY_MS,
) {
    companion object {
        const val DEFAULT_DELAY_MS: Long = 2_000L
    }

    private var dueAtMs: Long? = null

    val hasPending: Boolean get() = dueAtMs != null

    fun markMeaningfulEdit(nowMs: Long) {
        dueAtMs = nowMs + delayMs
    }

    fun remainingDelay(nowMs: Long): Long {
        val due = dueAtMs ?: return Long.MAX_VALUE
        return (due - nowMs).coerceAtLeast(0L)
    }

    fun shouldAutosave(nowMs: Long): Boolean {
        val due = dueAtMs ?: return false
        if (nowMs < due) return false
        dueAtMs = null
        return true
    }

    fun cancelPending() {
        dueAtMs = null
    }
}
