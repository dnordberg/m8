package com.m8droid.academy.data

data class EmulatorSnapshot(
    val playing: Boolean = false,
    val screen: Int = 0,
    val songRow: Int = 0,
    val chainRow: Int = 0,
    val phraseRow: Int = 0,
    val bpm: Int = 120,
    val cursorX: Int = 0,
    val cursorY: Int = 0,
    val editMode: Boolean = false,
    val midiActive: Boolean = false,
    val selectedSongChain: Int = 0xFF,
    val selectedChainRowPhrase: Int = 0xFF,
    val selectedPhraseStepNote: Int = 0xFF,
)
