package com.m8droid.academy

import com.m8droid.emulator.M8Instrument
import com.m8droid.emulator.M8ProjectSnapshot
import com.m8droid.emulator.M8Song

/** Builds the controlled blank project used by Academy lessons. */
object AcademyTutorialProject {
    fun freshSession(): M8ProjectSnapshot.Restored {
        val song = M8Song().apply {
            name = "ACADEMY 01"
            tempo = 96
        }
        return M8ProjectSnapshot.Restored(song, M8Instrument.createDefaults())
    }
}
