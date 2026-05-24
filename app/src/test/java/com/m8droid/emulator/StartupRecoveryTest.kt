package com.m8droid.emulator

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class StartupRecoveryTest {
    @Test
    fun reportsRecoverableProjectRestoreFailureWithOpenProjectsAction() {
        val entry = RecentSongStore.Entry(
            location = "/projects/missing.m8droid",
            title = "Missing Project",
            kind = RecentSongStore.Kind.PROJECT,
            loadedAt = 100,
        )

        val failure = StartupRecovery.fromFailure(entry, IllegalArgumentException("Project does not exist"))

        assertEquals("Could not restore Missing Project", failure.title)
        assertEquals("Project does not exist", failure.detail)
        assertEquals("Open Projects", failure.primaryAction)
        assertEquals("Start Demo", failure.dismissAction)
    }
}
