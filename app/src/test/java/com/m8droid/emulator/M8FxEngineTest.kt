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
}
