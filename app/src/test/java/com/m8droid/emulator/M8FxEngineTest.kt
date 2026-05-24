package com.m8droid.emulator

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class M8FxEngineTest {
    @Test
    fun `VOL command exposes volume override for playback row`() {
        val engine = M8FxEngine()
        val step = PhraseStep(
            note = 60,
            instrument = 0,
            volume = 0x20,
            fx1Cmd = M8FxEngine.FX_VOL,
            fx1Val = 0x7F,
        )

        val result = engine.processStepFx(track = 0, step = step, currentTick = 0, baseNote = 60)

        assertEquals(0x7F, result.volumeOverride)
    }

    @Test
    fun `PAN command exposes pan override for synth track`() {
        val engine = M8FxEngine()
        val step = PhraseStep(
            note = 60,
            instrument = 0,
            volume = 0x7F,
            fx1Cmd = M8FxEngine.FX_PAN,
            fx1Val = 0x00,
        )

        val result = engine.processStepFx(track = 0, step = step, currentTick = 0, baseNote = 60)

        assertEquals(0x00, result.panOverride)
    }

    @Test
    fun `RET command reports retrigger on configured tick interval`() {
        val engine = M8FxEngine()
        val step = PhraseStep(
            note = 60,
            instrument = 0,
            volume = 0x7F,
            fx1Cmd = M8FxEngine.FX_RET,
            fx1Val = 0x20,
        )
        engine.processStepFx(track = 0, step = step, currentTick = 0, baseNote = 60)

        assertTrue(!engine.processTick(track = 0, tick = 1).retrigger)
        assertTrue(engine.processTick(track = 0, tick = 2).retrigger)
    }

    @Test
    fun `DEL command reports delayed note only after delay expires`() {
        val engine = M8FxEngine()
        val step = PhraseStep(
            note = 64,
            instrument = 3,
            volume = 0x66,
            fx1Cmd = M8FxEngine.FX_DEL,
            fx1Val = 2,
        )

        val rowResult = engine.processStepFx(track = 0, step = step, currentTick = 0, baseNote = 64)
        assertTrue(rowResult.skipNote)

        assertEquals(-1, engine.processTick(track = 0, tick = 1).delayedNote)
        val tickResult = engine.processTick(track = 0, tick = 2)
        assertEquals(64, tickResult.delayedNote)
        assertEquals(3, tickResult.delayedInstrument)
        assertEquals(0x66, tickResult.delayedVolume)
    }

    @Test
    fun `HOP command exposes phrase row jump target`() {
        val engine = M8FxEngine()
        val step = PhraseStep(
            note = 60,
            instrument = 0,
            volume = 0x7F,
            fx1Cmd = M8FxEngine.FX_HOP,
            fx1Val = 0x0A,
        )

        val result = engine.processStepFx(track = 0, step = step, currentTick = 0, baseNote = 60)

        assertEquals(10, result.hopToRow)
    }

    @Test
    fun `SNG command exposes song row jump target`() {
        val engine = M8FxEngine()
        val step = PhraseStep(
            note = 60,
            instrument = 0,
            volume = 0x7F,
            fx1Cmd = M8FxEngine.FX_SNG,
            fx1Val = 0x12,
        )

        val result = engine.processStepFx(track = 0, step = step, currentTick = 0, baseNote = 60)

        assertEquals(0x12, result.songHopToRow)
    }

    @Test
    fun `KIL command reports note release once kill tick is reached`() {
        val engine = M8FxEngine()
        val step = PhraseStep(
            note = 60,
            instrument = 0,
            volume = 0x7F,
            fx1Cmd = M8FxEngine.FX_KIL,
            fx1Val = 2,
        )
        engine.processStepFx(track = 0, step = step, currentTick = 0, baseNote = 60)

        assertTrue(!engine.processTick(track = 0, tick = 1).releaseNote)
        assertTrue(engine.processTick(track = 0, tick = 2).releaseNote)
    }

    @Test
    fun `TBL command applies table transpose and volume on tick`() {
        val engine = M8FxEngine()
        val tables = Array(256) { Table() }
        tables[3].rows[0].transpose = 12
        tables[3].rows[0].volume = 0x40
        val step = PhraseStep(
            note = 60,
            instrument = 0,
            volume = 0x7F,
            fx1Cmd = M8FxEngine.FX_TBL,
            fx1Val = 3,
        )
        engine.processStepFx(track = 0, step = step, currentTick = 0, baseNote = 60)

        val tickResult = engine.processTableTick(track = 0, tables = tables)

        assertEquals(12, tickResult.noteOffset)
        assertEquals(0x40, tickResult.volumeOverride)
    }

    @Test
    fun `TIC command waits configured ticks before advancing table row`() {
        val engine = M8FxEngine()
        val tables = Array(256) { Table() }
        tables[2].rows[0].transpose = 7
        tables[2].rows[1].transpose = 12
        val step = PhraseStep(
            note = 60,
            instrument = 0,
            volume = 0x7F,
            fx1Cmd = M8FxEngine.FX_TBL,
            fx1Val = 2,
            fx2Cmd = M8FxEngine.FX_TIC,
            fx2Val = 2,
        )
        engine.processStepFx(track = 0, step = step, currentTick = 0, baseNote = 60)

        assertEquals(7, engine.processTableTick(track = 0, tables = tables).noteOffset)
        assertEquals(7, engine.processTableTick(track = 0, tables = tables).noteOffset)
        assertEquals(12, engine.processTableTick(track = 0, tables = tables).noteOffset)
    }
}
