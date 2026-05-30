package com.m8droid.emulator

object StartupRecovery {
    data class Failure(
        val title: String,
        val detail: String,
        val primaryAction: String = "Open Projects",
        val dismissAction: String = "Start Demo",
    )

    fun fromFailure(entry: RecentSongStore.Entry, error: Throwable): Failure {
        return Failure(
            title = "Could not restore ${entry.title.ifBlank { "last project" }}",
            detail = error.message ?: "The last project could not be opened.",
        )
    }
}
