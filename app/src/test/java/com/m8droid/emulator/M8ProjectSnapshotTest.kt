package com.m8droid.emulator

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

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
}
