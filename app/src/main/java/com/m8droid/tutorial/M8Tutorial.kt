package com.m8droid.tutorial

import com.m8droid.emulator.M8Emulator

/**
 * Interactive tutorial system for the M8 tracker.
 * Guides users through functionality with contextual steps.
 * Can be paused and resumed at any time, and adapts to the current screen.
 */
class M8Tutorial(private val emulator: M8Emulator) {

    data class Step(
        val section: Section,
        val title: String,
        val instruction: String,
        val buttonHint: String? = null,
        val check: (M8Emulator) -> Boolean,
    )

    enum class Section(val label: String, val screen: Int?) {
        WELCOME("WELCOME", null),
        SONG("SONG", M8Emulator.SCREEN_SONG),
        NAVIGATION("NAVIGATION", null),
        PLAYBACK("PLAYBACK", null),
        CHAIN("CHAIN", M8Emulator.SCREEN_CHAIN),
        PHRASE("PHRASE", M8Emulator.SCREEN_PHRASE),
        INSTRUMENT("INSTRUMENT", M8Emulator.SCREEN_INSTRUMENT),
        MIXER("MIXER", M8Emulator.SCREEN_MIXER),
        FX("EFFECTS", M8Emulator.SCREEN_FX),
        CONFIG("CONFIG", M8Emulator.SCREEN_CONFIG),
        COMPLETE("COMPLETE", null),
    }

    var active = false
        private set
    var currentStepIndex = 0
        private set
    var paused = false
        private set

    // Snapshot of emulator state when step was shown (for detecting changes)
    private var stepShownScreen = -1
    private var stepShownCursorX = -1
    private var stepShownCursorY = -1
    private var stepShownPlaying = false

    val steps: List<Step> = buildSteps()

    val currentStep: Step?
        get() = steps.getOrNull(currentStepIndex)

    val progress: Float
        get() = if (steps.isEmpty()) 1f else currentStepIndex.toFloat() / steps.size

    val progressText: String
        get() = "${currentStepIndex + 1}/${steps.size}"

    val isComplete: Boolean
        get() = currentStepIndex >= steps.size

    fun start() {
        active = true
        paused = false
        currentStepIndex = 0
        captureState()
    }

    fun resume() {
        active = true
        paused = false
        captureState()
    }

    fun pause() {
        paused = true
    }

    fun stop() {
        active = false
        paused = false
    }

    fun skip() {
        if (currentStepIndex < steps.size - 1) {
            currentStepIndex++
            captureState()
        }
    }

    fun previousStep() {
        if (currentStepIndex > 0) {
            currentStepIndex--
            captureState()
        }
    }

    /** Jump to the first step for the current screen (for context-sensitive resume) */
    fun jumpToScreen(screen: Int) {
        val idx = steps.indexOfFirst { it.section.screen == screen }
        if (idx >= 0) {
            currentStepIndex = idx
            captureState()
        }
    }

    /** Check if the current step's completion condition is met */
    fun checkCompletion(): Boolean {
        val step = currentStep ?: return false
        if (step.check(emulator)) {
            currentStepIndex++
            if (currentStepIndex < steps.size) {
                captureState()
            }
            return true
        }
        return false
    }

    private fun captureState() {
        stepShownScreen = emulator.screen
        stepShownCursorX = emulator.cursorX
        stepShownCursorY = emulator.cursorY
        stepShownPlaying = emulator.playing
    }

    private fun buildSteps(): List<Step> = listOf(
        // === WELCOME ===
        Step(
            Section.WELCOME,
            "WELCOME TO M8",
            "The M8 is a portable tracker for making music.\nLet's learn the basics. Press any direction key to begin.",
            "D-PAD",
        ) { e ->
            e.cursorX != stepShownCursorX || e.cursorY != stepShownCursorY
        },

        // === SONG SCREEN ===
        Step(
            Section.SONG,
            "SONG VIEW",
            "This is the SONG screen. It shows 8 tracks (columns)\nand rows of chain references. Each hex value is a chain number.",
            "Look around with D-PAD",
        ) { e ->
            e.screen == M8Emulator.SCREEN_SONG &&
                (e.cursorX != stepShownCursorX || e.cursorY != stepShownCursorY)
        },
        Step(
            Section.SONG,
            "TRACKS",
            "The 8 columns are tracks. Track 1 might be lead,\ntrack 2 bass, etc. Move LEFT/RIGHT between tracks.",
            "LEFT / RIGHT",
        ) { e ->
            e.cursorX != stepShownCursorX
        },
        Step(
            Section.SONG,
            "SONG ROWS",
            "Move UP/DOWN to see different song rows.\nEach row plays all 8 tracks simultaneously.",
            "UP / DOWN",
        ) { e ->
            e.cursorY != stepShownCursorY
        },

        // === PLAYBACK ===
        Step(
            Section.PLAYBACK,
            "PLAY / STOP",
            "Press PLAY to start the music.\nThe green arrows show the current play position.",
            "PLAY",
        ) { e ->
            e.playing != stepShownPlaying
        },
        Step(
            Section.PLAYBACK,
            "PLAYING",
            "Notice the waveform and VU meter moving.\nPress PLAY again to stop.",
            "PLAY",
        ) { e ->
            e.playing != stepShownPlaying
        },

        // === NAVIGATION ===
        Step(
            Section.NAVIGATION,
            "SCREEN SWITCHING",
            "The M8 has 8 screens. Press OPT to go to the next screen.\nYou'll move from SONG to CHAIN.",
            "OPT",
        ) { e ->
            e.screen != stepShownScreen
        },

        // === CHAIN SCREEN ===
        Step(
            Section.CHAIN,
            "CHAIN VIEW",
            "Chains link phrases together in sequence.\nEach row has a phrase number (PHR) and transpose (TSP).",
            "Look around with D-PAD",
        ) { e ->
            e.screen == M8Emulator.SCREEN_CHAIN &&
                (e.cursorX != stepShownCursorX || e.cursorY != stepShownCursorY)
        },
        Step(
            Section.CHAIN,
            "CHAIN COLUMNS",
            "Move LEFT/RIGHT to switch between\nthe phrase index and transpose columns.",
            "LEFT / RIGHT",
        ) { e ->
            e.cursorX != stepShownCursorX
        },
        Step(
            Section.CHAIN,
            "TO PHRASE VIEW",
            "Press OPT to continue to the PHRASE screen.\nThis is where notes live.",
            "OPT",
        ) { e ->
            e.screen == M8Emulator.SCREEN_PHRASE
        },

        // === PHRASE SCREEN ===
        Step(
            Section.PHRASE,
            "PHRASE VIEW",
            "Phrases hold the actual notes! Each column is a track.\nYou can see note names like C-5, D#4, etc.",
            "D-PAD to explore",
        ) { e ->
            e.screen == M8Emulator.SCREEN_PHRASE &&
                (e.cursorX != stepShownCursorX || e.cursorY != stepShownCursorY)
        },
        Step(
            Section.PHRASE,
            "TRACKS IN PHRASE",
            "Each track shows NOTE + INSTRUMENT number.\nMove LEFT/RIGHT to see different tracks.",
            "LEFT / RIGHT",
        ) { e ->
            e.cursorX != stepShownCursorX
        },
        Step(
            Section.PHRASE,
            "EDIT MODE",
            "Press SHIFT+EDIT together to enter edit mode.\n'EDIT' will appear in the header.",
            "SHIFT + EDIT",
        ) { e ->
            e.editMode
        },
        Step(
            Section.PHRASE,
            "EDITING NOTES",
            "In edit mode, hold SHIFT and press UP/DOWN\nto change notes by semitone. Try it!",
            "SHIFT + UP/DN",
        ) { e ->
            // Detect any cursor movement or note change
            e.cursorY != stepShownCursorY || e.cursorX != stepShownCursorX
        },
        Step(
            Section.PHRASE,
            "EXIT EDIT MODE",
            "Press SHIFT+EDIT again to exit edit mode.\nThen press OPT to continue.",
            "SHIFT + EDIT, then OPT",
        ) { e ->
            e.screen == M8Emulator.SCREEN_INSTRUMENT
        },

        // === INSTRUMENT SCREEN ===
        Step(
            Section.INSTRUMENT,
            "INSTRUMENT VIEW",
            "Instruments define how notes sound.\nYou'll see the synth type, shape, filter settings.",
            "UP / DOWN to scroll",
        ) { e ->
            e.screen == M8Emulator.SCREEN_INSTRUMENT &&
                e.cursorY != stepShownCursorY
        },
        Step(
            Section.INSTRUMENT,
            "SYNTH TYPES",
            "The M8 has WAVSYNTH, MACROSYNTH, FM SYNTH,\nHYPERSYNTH, and SAMPLER engines. Press OPT to continue.",
            "OPT",
        ) { e ->
            e.screen != M8Emulator.SCREEN_INSTRUMENT
        },

        // === TABLE (skip through) ===
        Step(
            Section.NAVIGATION,
            "TABLE VIEW",
            "Tables are per-tick automation sequences.\nThey modulate instruments in real-time. Press OPT.",
            "OPT",
        ) { e ->
            e.screen == M8Emulator.SCREEN_MIXER
        },

        // === MIXER SCREEN ===
        Step(
            Section.MIXER,
            "MIXER",
            "The mixer shows volume levels for all 8 tracks.\nGreen bars show live audio levels.",
            "LEFT / RIGHT to select track",
        ) { e ->
            e.screen == M8Emulator.SCREEN_MIXER &&
                e.cursorX != stepShownCursorX
        },
        Step(
            Section.MIXER,
            "SEND EFFECTS",
            "Below each track you can see send levels\nfor chorus (CHO), delay (DEL), and reverb (REV).",
            "OPT to continue",
        ) { e ->
            e.screen == M8Emulator.SCREEN_FX
        },

        // === FX SCREEN ===
        Step(
            Section.FX,
            "EFFECTS",
            "The FX screen controls the global effects:\nCHORUS, DELAY, and REVERB. Scroll through parameters.",
            "UP / DOWN",
        ) { e ->
            e.screen == M8Emulator.SCREEN_FX &&
                e.cursorY != stepShownCursorY
        },
        Step(
            Section.FX,
            "EFFECT PARAMETERS",
            "Each effect has time, feedback, width, and\nfilter controls. Press OPT for CONFIG.",
            "OPT",
        ) { e ->
            e.screen == M8Emulator.SCREEN_CONFIG
        },

        // === CONFIG SCREEN ===
        Step(
            Section.CONFIG,
            "CONFIGURATION",
            "The CONFIG screen shows song settings:\ntempo, transpose, scale, and playback state.",
            "UP / DOWN to browse",
        ) { e ->
            e.screen == M8Emulator.SCREEN_CONFIG &&
                e.cursorY != stepShownCursorY
        },

        // === COMPLETE ===
        Step(
            Section.COMPLETE,
            "TUTORIAL COMPLETE",
            "You've learned the M8 basics!\nPress OPT to return to SONG and start making music.",
            "OPT",
        ) { e ->
            e.screen == M8Emulator.SCREEN_SONG
        },
    )

    companion object {
        /** Get contextual tips for a specific screen (for on-demand help) */
        fun getScreenTips(screen: Int): List<String> = when (screen) {
            M8Emulator.SCREEN_SONG -> listOf(
                "D-PAD: Navigate tracks and rows",
                "PLAY: Start/stop playback",
                "OPT/EDIT: Switch screens",
                "Values are chain numbers (FE=empty)",
            )
            M8Emulator.SCREEN_CHAIN -> listOf(
                "D-PAD: Navigate rows and columns",
                "PHR column: Phrase reference",
                "TSP column: Transpose in semitones",
                "Chains sequence phrases together",
            )
            M8Emulator.SCREEN_PHRASE -> listOf(
                "D-PAD: Navigate notes and tracks",
                "SHIFT+EDIT: Toggle edit mode",
                "Edit: SHIFT+UP/DN = semitone",
                "Edit: SHIFT+LT/RT = octave",
            )
            M8Emulator.SCREEN_INSTRUMENT -> listOf(
                "UP/DOWN: Browse parameters",
                "Synth types: WAV, MACRO, FM, HYPER",
                "Filter: LP, HP, BP, BS, LP>HP",
                "AMP/LIM: Volume and clipping",
            )
            M8Emulator.SCREEN_TABLE -> listOf(
                "Tables run per-tick automation",
                "TSP: Transpose offset",
                "VOL: Volume change",
                "FX1-3: Effect commands per tick",
            )
            M8Emulator.SCREEN_MIXER -> listOf(
                "LEFT/RIGHT: Select track",
                "Bars show live audio levels",
                "CHO/DEL/REV: Effect send levels",
                "Master section at bottom",
            )
            M8Emulator.SCREEN_FX -> listOf(
                "UP/DOWN: Browse parameters",
                "CHORUS: Modulation depth/freq/width",
                "DELAY: Time, feedback, filtering",
                "REVERB: Size, damping, diffusion",
            )
            M8Emulator.SCREEN_CONFIG -> listOf(
                "Song name, tempo, transpose",
                "Scale and quantize settings",
                "Current playback position",
                "SHIFT: Cycle octave (O1-O8)",
            )
            else -> listOf("Press OPT/EDIT to switch screens")
        }
    }
}
