package com.m8droid.emulator

import java.io.File

/** Add-only factory seeding: never replace a saved project, even a modified factory demo. */
object BuiltInDemoProjects {
    fun ensureSeeded(projectDir: File): List<File> {
        projectDir.mkdirs()
        val current = M8ProjectSnapshot.encode(M8Song().apply { loadDemoSong() }, MeridianDemo.instruments())
        val newDemo = File(projectDir, "New Demo.m8droid")
        val priorNewDemo = newDemo.exists()
        if (!priorNewDemo) newDemo.writeBytes(current)
        val oldDemo = File(projectDir, "Old Demo.m8droid")
        if (!oldDemo.exists()) {
            oldDemo.writeBytes(M8ProjectSnapshot.encode(M8Song().apply { loadOldDemoSong() }, M8Instrument.createDefaults()))
        }
        // Upgraders keep New Demo byte-for-byte. The same new composition is available
        // through the existing Projects loader under its own name, without changing recents.
        val namedDemo = File(projectDir, "Meridian Light.m8droid")
        if (priorNewDemo && !newDemo.readBytes().contentEquals(current) && !namedDemo.exists()) {
            namedDemo.writeBytes(current)
        }
        return listOf(newDemo, oldDemo) + if (namedDemo.exists()) listOf(namedDemo) else emptyList()
    }
}
