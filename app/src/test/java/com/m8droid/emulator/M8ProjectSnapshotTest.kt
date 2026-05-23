package com.m8droid.emulator

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class M8ProjectSnapshotTest {
    @Test
    fun signatureChangesWhenSongDataChanges() {
        val song = M8Song()
        val instruments = M8Instrument.createDefaults()
        val clean = M8ProjectSnapshot.signature(song, instruments)

        song.songGrid[3][2] = 0x12

        assertTrue(M8ProjectSnapshot.signature(song, instruments) != clean)
    }

    @Test
    fun roundTripRestoresEditableSongDataAndInstruments() {
        val song = M8Song().apply {
            name = "SAVE ME"
            tempo = 142
            transpose = -3
            songGrid[7][4] = 0x22
            chains[0x22].rows[5].phrase = 0x33
            chains[0x22].rows[5].transpose = -12
            phrases[0x33].steps[9].note = 61
            phrases[0x33].steps[9].instrument = 17
            phrases[0x33].steps[9].volume = 0x70
            phrases[0x33].steps[9].fx1Cmd = 3
            phrases[0x33].steps[9].fx1Val = 0x44
            tables[2].rows[1].transpose = 5
            grooves[1].ticks[3] = 9
            activeScale = 4
            quantize = 3
            chorus.modDepth = 0x22
            chorus.width = 0x77
            delay.timeL = 0x11
            delay.feedback = 0x66
            reverb.size = 0x99
            reverb.damping = 0x55
            mixer.trackVolumes[2] = 0x91
            mixer.trackPans[2] = 0x30
            mixer.trackDelaySend[2] = 0x44
            mixer.masterVolume = 0xC0
            mixer.djFilter = 0x70
        }
        val instruments = M8Instrument.createDefaults().apply {
            this[17] = M8Instrument("KICK SAVE", InstrumentType.SAMPLER).apply {
                sampler.samplePath = "Samples/kick.wav"
                sampler.start = 0x11
                amp.amp = 0x99
            }
        }

        val restored = M8ProjectSnapshot.decode(M8ProjectSnapshot.encode(song, instruments))

        assertEquals("SAVE ME", restored.song.name)
        assertEquals(142, restored.song.tempo)
        assertEquals(-3, restored.song.transpose)
        assertEquals(0x22, restored.song.songGrid[7][4])
        assertEquals(0x33, restored.song.chains[0x22].rows[5].phrase)
        assertEquals(-12, restored.song.chains[0x22].rows[5].transpose)
        assertEquals(61, restored.song.phrases[0x33].steps[9].note)
        assertEquals(17, restored.song.phrases[0x33].steps[9].instrument)
        assertEquals(0x70, restored.song.phrases[0x33].steps[9].volume)
        assertEquals(3, restored.song.phrases[0x33].steps[9].fx1Cmd)
        assertEquals(0x44, restored.song.phrases[0x33].steps[9].fx1Val)
        assertEquals(5, restored.song.tables[2].rows[1].transpose)
        assertEquals(9, restored.song.grooves[1].ticks[3])
        assertEquals(4, restored.song.activeScale)
        assertEquals(3, restored.song.quantize)
        assertEquals(0x22, restored.song.chorus.modDepth)
        assertEquals(0x77, restored.song.chorus.width)
        assertEquals(0x11, restored.song.delay.timeL)
        assertEquals(0x66, restored.song.delay.feedback)
        assertEquals(0x99, restored.song.reverb.size)
        assertEquals(0x55, restored.song.reverb.damping)
        assertEquals(0x91, restored.song.mixer.trackVolumes[2])
        assertEquals(0x30, restored.song.mixer.trackPans[2])
        assertEquals(0x44, restored.song.mixer.trackDelaySend[2])
        assertEquals(0xC0, restored.song.mixer.masterVolume)
        assertEquals(0x70, restored.song.mixer.djFilter)
        assertEquals("KICK SAVE", restored.instruments[17].name)
        assertEquals(InstrumentType.SAMPLER, restored.instruments[17].type)
        assertEquals("Samples/kick.wav", restored.instruments[17].sampler.samplePath)
        assertEquals(0x11, restored.instruments[17].sampler.start)
        assertEquals(0x99, restored.instruments[17].amp.amp)
    }

    @Test
    fun dirtyGuardRequiresConfirmationOnlyWhenSignatureDiffers() {
        val song = M8Song()
        val instruments = M8Instrument.createDefaults()
        val guard = SongDirtyGuard(M8ProjectSnapshot.signature(song, instruments))

        assertFalse(guard.isDirty(M8ProjectSnapshot.signature(song, instruments)))
        assertFalse(guard.shouldConfirmBeforeReplace(M8ProjectSnapshot.signature(song, instruments)))

        song.phrases[0].steps[0].note = 48
        val edited = M8ProjectSnapshot.signature(song, instruments)

        assertTrue(guard.isDirty(edited))
        assertTrue(guard.shouldConfirmBeforeReplace(edited))

        guard.markClean(edited)
        assertFalse(guard.shouldConfirmBeforeReplace(edited))
    }

    @Test
    fun autosaveDebouncesUntilMeaningfulEditsGoQuiet() {
        val autosave = ProjectAutosaveDebouncer(delayMs = 2_000)

        autosave.markMeaningfulEdit(nowMs = 100)
        assertFalse(autosave.shouldAutosave(nowMs = 2_099))

        autosave.markMeaningfulEdit(nowMs = 1_000)
        assertFalse(autosave.shouldAutosave(nowMs = 2_999))
        assertTrue(autosave.shouldAutosave(nowMs = 3_000))
        assertFalse(autosave.shouldAutosave(nowMs = 3_001), "autosave should fire once per dirty burst")
    }

    @Test
    fun autosaveCanBeCancelledAfterManualSaveOrProjectLoad() {
        val autosave = ProjectAutosaveDebouncer(delayMs = 2_000)

        autosave.markMeaningfulEdit(nowMs = 100)
        autosave.cancelPending()

        assertFalse(autosave.shouldAutosave(nowMs = 5_000))
    }

    @Test
    fun projectLibraryListsOnlyM8DroidProjectsNewestFirst() {
        val dir = createTempDir(prefix = "m8-projects-")
        try {
            val oldProject = writeProject(dir, "old_song.m8droid", "OLD", tempo = 120)
            val newProject = writeProject(dir, "new_song.m8droid", "NEW", tempo = 144)
            File(dir, "notes.txt").writeText("ignore me")
            oldProject.setLastModified(1_000L)
            newProject.setLastModified(2_000L)

            val projects = M8ProjectLibrary.list(dir)

            assertEquals(listOf("new_song.m8droid", "old_song.m8droid"), projects.map { it.fileName })
            assertEquals("NEW", projects[0].songName)
            assertEquals(144, projects[0].tempo)
            assertTrue(projects[0].sizeBytes > 0)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun projectLibraryLoadsSavedProjectSnapshot() {
        val dir = createTempDir(prefix = "m8-projects-")
        try {
            val file = writeProject(dir, "restore_me.m8droid", "RESTORE ME", tempo = 151) { song, instruments ->
                song.songGrid[2][1] = 0x24
                song.chains[0x24].rows[3].phrase = 0x35
                song.phrases[0x35].steps[4].note = 67
                instruments[3] = M8Instrument("RESTORED INST", InstrumentType.FM_SYNTH)
            }

            val restored = M8ProjectLibrary.load(file)

            assertEquals("RESTORE ME", restored.song.name)
            assertEquals(151, restored.song.tempo)
            assertEquals(0x24, restored.song.songGrid[2][1])
            assertEquals(0x35, restored.song.chains[0x24].rows[3].phrase)
            assertEquals(67, restored.song.phrases[0x35].steps[4].note)
            assertEquals("RESTORED INST", restored.instruments[3].name)
            assertEquals(InstrumentType.FM_SYNTH, restored.instruments[3].type)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun projectLibraryRenamesDuplicatesAndDeletesProjectFilesSafely() {
        val dir = createTempDir(prefix = "m8-projects-")
        try {
            val original = writeProject(dir, "sketch.m8droid", "SKETCH", tempo = 133)

            val renamed = M8ProjectLibrary.rename(dir, original, "Renamed Sketch")
            assertEquals("Renamed_Sketch.m8droid", renamed.name)
            assertFalse(original.exists())
            assertTrue(renamed.exists())

            val duplicate = M8ProjectLibrary.duplicate(dir, renamed, "Renamed Sketch Copy")
            assertEquals("Renamed_Sketch_Copy.m8droid", duplicate.name)
            assertTrue(renamed.exists())
            assertTrue(duplicate.exists())
            assertEquals("SKETCH", M8ProjectLibrary.load(duplicate).song.name)

            assertTrue(M8ProjectLibrary.delete(dir, renamed))
            assertFalse(renamed.exists())
            assertEquals(listOf("Renamed_Sketch_Copy.m8droid"), M8ProjectLibrary.list(dir).map { it.fileName })
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun projectLibraryRejectsUnsafeProjectOperationNames() {
        val dir = createTempDir(prefix = "m8-projects-")
        try {
            val original = writeProject(dir, "safe.m8droid", "SAFE", tempo = 120)

            val renamed = M8ProjectLibrary.rename(dir, original, "../unsafe path")

            assertEquals("unsafe_path.m8droid", renamed.name)
            assertEquals(dir.canonicalPath, renamed.parentFile!!.canonicalPath)
            assertTrue(renamed.exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun projectLibraryRejectsProjectOperationsOutsideManagedDirectory() {
        val dir = createTempDir(prefix = "m8-projects-")
        val outside = createTempDir(prefix = "m8-projects-outside-")
        try {
            val externalProject = writeProject(outside, "external.m8droid", "EXTERNAL", tempo = 120)

            assertThrows(IllegalArgumentException::class.java) {
                M8ProjectLibrary.rename(dir, externalProject, "Should Not Move")
            }
            assertThrows(IllegalArgumentException::class.java) {
                M8ProjectLibrary.duplicate(dir, externalProject, "Should Not Copy")
            }
            assertThrows(IllegalArgumentException::class.java) {
                M8ProjectLibrary.delete(dir, externalProject)
            }
            assertTrue(externalProject.exists())
        } finally {
            dir.deleteRecursively()
            outside.deleteRecursively()
        }
    }

    private fun writeProject(
        dir: File,
        name: String,
        songName: String,
        tempo: Int,
        mutate: (M8Song, Array<M8Instrument>) -> Unit = { _, _ -> },
    ): File {
        val song = M8Song().apply {
            this.name = songName
            this.tempo = tempo
        }
        val instruments = M8Instrument.createDefaults()
        mutate(song, instruments)
        return File(dir, name).also { it.writeBytes(M8ProjectSnapshot.encode(song, instruments)) }
    }
}
