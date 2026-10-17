package com.m8droid.emulator

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class M8DemoSongTest {
    @TempDir lateinit var dir: File

    private fun song() = M8Song().apply { loadDemoSong() }

    /** Steps a track performs in a section, so an empty grid cell reads as a real rest. */
    private fun performed(song: M8Song, section: Int, track: Int): List<PhraseStep> {
        val chain = song.songGrid[section][track]
        if (chain == M8Song.EMPTY) return emptyList()
        return song.chains[chain].rows.filter { it.phrase != M8Song.EMPTY }
            .flatMap { song.phrases[it.phrase].steps.toList() }
    }

    private fun notes(song: M8Song, section: Int, track: Int) =
        performed(song, section, track).filter { it.note in 1..127 }

    @Test fun `startup is the complete factory composition arranged across eight labelled sections`() {
        val emulator = M8Emulator()
        val song = emulator.song
        assertEquals(MeridianDemo.NAME, song.name)
        assertEquals(108, song.tempo)
        assertEquals(48, MeridianDemo.sectionBars.sum())
        assertEquals(MeridianDemo.sectionNames.size, song.songGrid.count { row -> row.any { it != M8Song.EMPTY } })
        MeridianDemo.sectionBars.forEachIndexed { section, bars ->
            for (track in 0..7) {
                val chain = song.songGrid[section][track]
                if (chain == M8Song.EMPTY) continue
                assertEquals(bars, song.chains[chain].rows.count { it.phrase != M8Song.EMPTY },
                    "section $section track $track must cover every bar")
            }
        }
        MeridianDemo.sectionNames.indices.forEach { s ->
            assertTrue(performed(song, s, 6).isNotEmpty(), "the pad carries section $s")
        }
        assertTrue(song.phrases.count { !it.isEmpty() } > 60, "the composition needs real phrase content")
        assertEquals(M8Song.EMPTY, song.songGrid[8][0])
    }

    @Test fun `recorded drums bass keys lead and pad are mapped onto the default instrument pool`() {
        val emulator = M8Emulator()
        val pool = emulator.instruments
        assertEquals(InstrumentType.SAMPLER, pool[3].type)
        assertEquals(MeridianDemo.drumKitZones, pool[3].sampler.zones)
        assertEquals(MeridianDemo.bassRoots, pool[1].sampler.zones.map { it.rootMidi })
        assertEquals(InstrumentType.FM_SYNTH, pool[2].type)
        assertEquals(InstrumentType.WAVSYNTH, pool[5].type)
        assertEquals(InstrumentType.WAVSYNTH, pool[6].type)
        assertEquals(InstrumentType.WAVSYNTH, pool[0].type, "unused slot 0 stays the app default")
        val song = emulator.song
        assertEquals(listOf(3, 3, 3, 1, 2, 5, 6, 3), (0..7).map { song.instrumentIndices[it] })
        val kitHits = (0..7).flatMap { section -> listOf(0, 1, 2, 7).flatMap { track -> notes(song, section, track) } }
        assertEquals(setOf(36, 38, 42, 45, 46, 49), kitHits.map { hit ->
            pool[3].sampler.zones.minByOrNull { kotlin.math.abs(it.rootMidi - hit.note) }!!.rootMidi
        }.toSet(), "every recorded drum in the kit is used by the arrangement")
    }

    @Test fun `sections change orchestration instead of repeating one loop`() {
        val song = song()
        assertTrue(performed(song, 0, 4).all { it.note == M8Song.EMPTY }, "keys must not play in the intro")
        assertEquals(M8Song.EMPTY, song.songGrid[0][4])
        assertTrue(notes(song, 0, 0).isEmpty(), "intro has no kick")
        assertTrue(notes(song, 4, 0).isEmpty() && notes(song, 4, 1).isEmpty(), "breakdown drops the kit")
        assertTrue(notes(song, 4, 3).isNotEmpty() && notes(song, 4, 6).isNotEmpty(), "breakdown keeps bass and pad")
        assertTrue(notes(song, 3, 5).size >= notes(song, 2, 5).size, "lead theme arrives after the verse")
        assertNotEquals(notes(song, 3, 5), notes(song, 5, 5), "variation restates the theme differently")
        assertTrue(notes(song, 6, 2).size > notes(song, 3, 2).size, "final adds hat movement")
        assertEquals(M8Song.EMPTY, song.songGrid[7][7], "outro has no percussion")
        assertEquals(M8Song.EMPTY, song.songGrid[7][1], "outro has no snare")
        assertEquals(M8Song.EMPTY, song.songGrid[0][0], "intro starts without the kit")
        val lastBar = song.chains[song.songGrid[7][6]].rows[3].phrase
        assertEquals(M8Song.NOTE_OFF, song.phrases[lastBar].steps[15].note, "coda releases the pad")
    }

    @Test fun `bass is monophonic with explicit releases and stays in E minor`() {
        val song = song()
        val bass = (0..7).flatMap { section -> notes(song, section, 3) }
        assertTrue(bass.size > 40, "bass plays through the song")
        assertTrue(bass.map { it.note }.all { it in 24..60 }, "bass stays in a playable register")
        val scale = song.scales[2]
        assertEquals("MINOR", scale.name)
        assertEquals(4, scale.key)
        val scaleDegrees = listOf(0, 2, 3, 5, 7, 8, 10)
        assertTrue(bass.all { (it.note - 4).mod(12) in scaleDegrees },
            "bass notes belong to E natural minor: ${bass.map { it.note }.distinct()}")
        for (section in 0..7) {
            var sounding = false
            performed(song, section, 3).forEach { step ->
                when {
                    step.note in 1..127 -> assertFalse(sounding, "bass must release before the next note")
                    step.note == M8Song.NOTE_OFF -> sounding = false
                }
                if (step.note in 1..127) sounding = true
            }
            assertFalse(sounding, "each bass section must release its last note")
        }
    }

    @Test fun `old demo is unchanged and keeps the original instrument pool`() {
        val old = M8Song().apply { loadOldDemoSong() }
        assertEquals("NIGHTCIRCUIT", old.name)
        assertEquals(118, old.tempo)
        assertEquals(7, old.songGrid[0][2])
        BuiltInDemoProjects.ensureSeeded(dir)
        val loaded = M8ProjectLibrary.load(File(dir, "Old Demo.m8droid"))
        assertEquals(M8ProjectSnapshot.signature(old, M8Instrument.createDefaults()),
            M8ProjectSnapshot.signature(loaded.song, loaded.instruments))
    }

    @Test fun `seed and upgrade never touch existing project or autosave bytes`() {
        BuiltInDemoProjects.ensureSeeded(dir)
        val project = M8ProjectLibrary.load(File(dir, "New Demo.m8droid"))
        assertEquals(MeridianDemo.NAME, project.song.name)
        assertEquals(M8ProjectSnapshot.signature(M8Song().apply { loadDemoSong() }, MeridianDemo.instruments()),
            M8ProjectSnapshot.signature(project.song, project.instruments))
        val protected = listOf("New Demo.m8droid", "Old Demo.m8droid", "Autosave.m8droid", "User.m8droid", "recent_songs.tsv")
        protected.forEach { File(dir, it).writeText("user bytes: $it") }
        BuiltInDemoProjects.ensureSeeded(dir)
        protected.forEach { assertEquals("user bytes: $it", File(dir, it).readText()) }
        val named = File(dir, "Meridian Light.m8droid")
        assertEquals(MeridianDemo.NAME, M8ProjectLibrary.load(named).song.name)
        named.writeText("user edited the new composition")
        BuiltInDemoProjects.ensureSeeded(dir)
        assertEquals("user edited the new composition", named.readText())
    }

    @Test fun `composition survives the existing project save and reopen path`() {
        val song = song()
        val instruments = MeridianDemo.instruments()
        val file = M8ProjectLibrary.saveProject(dir, song, instruments)
        val restored = M8ProjectLibrary.load(file)
        assertEquals(M8ProjectSnapshot.signature(song, instruments), M8ProjectSnapshot.signature(restored.song, restored.instruments))
    }

    @Test fun `demo loaders reset custom scale definitions`() {
        val song = M8Song()
        song.scales[2].name = "BROKEN"
        song.scales[2].intervals.fill(true)
        song.loadDemoSong()
        assertEquals("MINOR", song.scales[2].name)
        assertEquals(4, song.scales[2].key)
        song.loadOldDemoSong()
        assertEquals(0, song.scales[2].key)
        assertTrue(song.scales[2].intervals.contentEquals(booleanArrayOf(true, false, true, true, false, true, false, true, true, false, true, false)))
    }
}
