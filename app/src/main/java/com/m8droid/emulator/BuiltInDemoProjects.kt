package com.m8droid.emulator

import java.io.File

/** Seeds the built-in demo projects into the app-native Projects folder. */
object BuiltInDemoProjects {
    private const val NEW_DEMO_FILE = "New Demo.m8droid"
    private const val OLD_DEMO_FILE = "Old Demo.m8droid"

    fun ensureSeeded(projectDir: File): List<File> {
        projectDir.mkdirs()
        val defaults = M8Instrument.createDefaults()
        val newDemo = File(projectDir, NEW_DEMO_FILE).also { file ->
            if (!file.exists()) {
                val song = M8Song().apply { loadDemoSong() }
                file.writeBytes(M8ProjectSnapshot.encode(song, defaults))
            }
        }
        val oldDemo = File(projectDir, OLD_DEMO_FILE).also { file ->
            if (!file.exists()) {
                val song = M8Song().apply { loadOldDemoSong() }
                file.writeBytes(M8ProjectSnapshot.encode(song, defaults))
            }
        }
        return listOf(newDemo, oldDemo)
    }
}
