package com.m8droid.tutorial

import com.m8droid.emulator.M8Emulator
import com.m8droid.protocol.M8Commands
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class M8TutorialTest {
    @Test
    fun skipAndPauseControlsChangeObservableRevision() {
        val tutorial = M8Tutorial(M8Emulator())

        tutorial.start()
        val afterStart = tutorial.revision
        tutorial.skip()

        assertEquals(1, tutorial.currentStepIndex)
        assertTrue(tutorial.revision > afterStart)

        val afterSkip = tutorial.revision
        tutorial.pause()

        assertTrue(tutorial.paused)
        assertTrue(tutorial.revision > afterSkip)
    }

    @Test
    fun buttonHintStepsExposeTouchableKeyMask() {
        val tutorial = M8Tutorial(M8Emulator())
        tutorial.start()

        assertEquals(M8Commands.KEY_RIGHT, tutorial.currentStepButtonMask)

        while (tutorial.currentStep?.title != "PLAY / STOP") {
            tutorial.skip()
        }
        assertEquals(M8Commands.KEY_PLAY, tutorial.currentStepButtonMask)

        while (tutorial.currentStep?.title != "EDIT MODE") {
            tutorial.skip()
        }
        assertEquals(M8Commands.KEY_SHIFT or M8Commands.KEY_EDIT, tutorial.currentStepButtonMask)
    }
}
