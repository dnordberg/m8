package com.m8droid.emulator

/**
 * The single factory composition that ships as the default demo. It is ordinary
 * tracker data: phrases, chains, chains in the song grid, and sample instruments
 * that reference the offline bank installed under /Samples/Factory/v1.
 *
 * Drums, bass and percussion are the CC0 recordings from assets/soundbank; keys,
 * lead and pad are the original app synth presets. Track roles:
 * A kit-kick, B kit-snare, C kit-hats, D recorded bass, E electric keys, F lead,
 * G pad, H kit-tom/cymbal.
 */
object MeridianDemo {
    const val NAME = "MERIDIAN LIGHT"
    val sectionNames = listOf("Intro", "Build", "Verse", "Theme", "Breakdown", "Variation", "Final", "Outro")
    val sectionBars = listOf(4, 4, 8, 8, 4, 8, 8, 4)

    // Instrument slots keep the app's default pool intact (slot 0 is the untouched
    // WAVSYNTH default) and repurpose only the slots this song performs with.
    private const val KIT = 3
    private const val BASS = 1
    private const val KEYS = 2
    private const val LEAD = 5
    private const val PAD = 6
    private val trackInstrument = intArrayOf(KIT, KIT, KIT, BASS, KEYS, LEAD, PAD, KIT)

    val drumKitZones = listOf(
        SampleZone("/Samples/Factory/v1/acoustic_kick.wav", 36),
        SampleZone("/Samples/Factory/v1/acoustic_snare.wav", 38),
        SampleZone("/Samples/Factory/v1/acoustic_hat_closed.wav", 42),
        SampleZone("/Samples/Factory/v1/acoustic_tom.wav", 45),
        SampleZone("/Samples/Factory/v1/acoustic_hat_open.wav", 46),
        SampleZone("/Samples/Factory/v1/acoustic_cymbal.wav", 49),
    )
    val bassRoots = listOf(24, 28, 31, 36, 40, 43, 48)

    fun instruments(): Array<M8Instrument> {
        val pool = M8Instrument.createDefaults()
        pool[KIT] = recording("DRUM KIT", samplePath = drumKitZones.first().path, rootMidi = 36).apply {
            sampler.zones = drumKitZones
            amp.amp = 235
        }
        pool[BASS] = recording("WOOD BASS", samplePath = "/Samples/Factory/v1/acoustic_bass_36.wav", rootMidi = 36).apply {
            sampler.zones = bassRoots.map { SampleZone("/Samples/Factory/v1/acoustic_bass_$it.wav", it) }
            amp.amp = 220
            filter = FilterParams(FilterType.LP, 150, 10)
        }
        val presets = SoundBank(emptyList())
        pool[KEYS] = presets.instrument("electric_keys")
        pool[LEAD] = presets.instrument("silk_lead").apply {
            wavSynth.shape = WavShape.TRIANGLE
            modulation.env1 = Envelope(attack = 3, decay = 55, sustain = 130, release = 28)
            modulation.env2 = Envelope(decay = 40, sustain = 0, dest = ModDestination.CUTOFF, amount = 165)
            amp.delaySend = 22
        }
        pool[PAD] = presets.instrument("warm_pad")
        return pool
    }

    private fun recording(name: String, samplePath: String, rootMidi: Int) =
        M8Instrument(name, InstrumentType.SAMPLER).apply {
            sampler = SamplerParams(samplePath = samplePath, rootMidi = rootMidi)
            filter.type = FilterType.OFF
            modulation.env1 = Envelope(attack = 0, decay = 0, sustain = 255, release = 18)
        }

    /**
     * E minor, Em–Cmaj7–G–D as the verse/theme cycle, C–D–Em–Bm for the bridge,
     * and a resolving Em close. Every track rests where the arrangement is sparse,
     * so sections differ by real orchestration rather than volume alone.
     */
    fun arrange(song: M8Song) {
        song.name = NAME
        song.tempo = 108
        song.activeScale = 2
        song.scales[2].key = 4
        for (track in 0..7) song.instrumentIndices[track] = trackInstrument[track]
        val phrases = linkedMapOf<List<PhraseStep>, Int>()
        for (section in sectionNames.indices) for (track in 0..7) {
            val stepsByBar = (0 until sectionBars[section]).map { bar -> barSteps(section, bar, track) }
            // A track that rests for the whole section gets no chain, so the grid shows the
            // real arrangement; voices stop because the previous section ends with its own offs.
            if (stepsByBar.none { bar -> bar.any { it.note in 1..127 } }) continue
            stepsByBar.filter { bar -> bar.none { it.note in 1..127 } }.forEach { rest ->
                rest[0] = PhraseStep(note = M8Song.NOTE_OFF, instrument = trackInstrument[track])
            }
            val chain = section * 8 + track
            song.songGrid[section][track] = chain
            stepsByBar.forEachIndexed { bar, steps ->
                val key = steps.toList()
                val index = phrases.getOrPut(key) {
                    val id = phrases.size
                    require(id < M8Song.EMPTY) { "Factory song phrase pool overflow" }
                    steps.forEachIndexed { i, step -> song.phrases[id].steps[i] = step }
                    id
                }
                song.chains[chain].rows[bar].phrase = index
            }
        }
        // Master trimmed so the busiest section keeps roughly 3 dB of headroom; the
        // per-track balance below is what the arrangement was mixed around.
        song.mixer.masterVolume = 132
        intArrayOf(230, 205, 175, 225, 160, 170, 145, 150).copyInto(song.mixer.trackVolumes)
        intArrayOf(128, 128, 150, 128, 92, 140, 178, 108).copyInto(song.mixer.trackPans)
        song.delay = DelaySettings(timeL = 48, timeR = 62, feedback = 45)
        song.reverb = ReverbSettings(size = 100, damping = 160)
    }

    private fun barSteps(section: Int, bar: Int, track: Int): Array<PhraseStep> {
        val steps = Array(16) { PhraseStep() }
        val bars = sectionBars[section]
        val full = section in listOf(2, 3, 5, 6)
        val fill = bar == bars - 1 && section in 1..6
        val chord = bar % 4
        val root = when {
            section == 4 -> intArrayOf(36, 38, 40, 35)[chord]   // C D Em Bm
            section == 7 -> intArrayOf(40, 36, 38, 40)[chord]   // Em C D Em
            else -> intArrayOf(40, 36, 43, 38)[chord]           // Em C G D
        }
        val third = if (root == 40 || root == 35) 3 else 4      // minor third for Em/Bm
        val fading = if (section == 7) 1.0 - bar * 0.18 else 1.0
        fun hit(row: Int, note: Int, volume: Int) {
            steps[row] = PhraseStep(note, trackInstrument[track], (volume * fading).toInt().coerceIn(1, 255))
        }
        fun off(row: Int) { steps[row] = PhraseStep(note = M8Song.NOTE_OFF, instrument = trackInstrument[track]) }
        when (track) {
            0 -> {  // kick
                if (full || section == 1 && bar >= 2 || section == 7 && bar < 2) {
                    listOf(0, 8).forEach { hit(it, 36, if (full) 220 else 175) }
                    if (full) hit(if (bar % 2 == 0) 6 else 7, 36, 160)
                    if (section >= 5) hit(11, 36, 145)
                }
            }
            1 -> if (full || section == 1 && bar >= 2) {  // snare
                listOf(4, 12).forEach { hit(it, 38, 185) }
                if (bar % 2 == 1) hit(11, 38, 70)
                if (fill) listOf(13, 14, 15).forEach { hit(it, 38, 90 + (it - 13) * 25) }
            }
            2 -> if (full || section == 1 || section == 0 && bar >= 2) {  // hats
                (0..14 step 2).forEach { hit(it, 42, if (it % 4 == 0) 110 else 78) }
                if (full && bar % 2 == 1) hit(14, 46, 85)
                if (section == 6) { hit(7, 42, 60); hit(15, 42, 65) }
            }
            3 -> if (section != 0 || bar >= 2) {  // recorded bass, monophonic with explicit releases
                hit(0, root, if (full) 195 else 170); off(3)
                if (full || section == 1) {
                    hit(6, root + 7, 145); off(7)
                    hit(8, root, 185); off(11)
                    hit(14, if (bar % 2 == 0) root + 12 else root + 7, 145); off(15)
                } else { hit(8, root + 7, 150); off(13) }
            }
            4 -> if (section in listOf(2, 3, 5, 6)) {  // broken chord voicings leave room for bass and lead
                val rows = if (full) listOf(1, 4, 9, 12) else listOf(0, 6, 10)
                rows.forEachIndexed { i, row ->
                    hit(row, root + 12 + intArrayOf(third, 7, 12, 7)[i], if (i == 0) 140 else 115)
                    off(row + 2)
                }
            }
            5 -> when {
                section in listOf(3, 5, 6) || section == 2 && bar >= 4 -> {  // lead theme, two related shapes
                    val melody = arrayOf(intArrayOf(76, 79, 78, 74), intArrayOf(76, 79, 83, 79),
                        intArrayOf(79, 81, 83, 78), intArrayOf(78, 76, 74, 71))[chord]
                    val rows = if (section == 5) intArrayOf(1, 5, 8, 12) else intArrayOf(0, 3, 8, 12)
                    rows.forEachIndexed { i, row ->
                        hit(row, melody[if (section == 5) 3 - i else i], if (i == 0) 155 else 130)
                        off(row + 2)
                    }
                    if (section == 6 && bar >= 4) hit(15, melody[0], 100)
                }
                section == 7 -> { hit(0, intArrayOf(79, 76, 74, 76)[chord], 130); off(if (bar == 3) 8 else 6) }
            }
            6 -> {  // sustained pad, one voice per bar
                hit(0, root + 19, if (section == 4) 140 else 105)
                off(if (section == 7 && bar == 3) 8 else 13)
            }
            else -> when {  // tom/cymbal accents at section turns
                bar == 0 && section in listOf(3, 6) -> hit(0, 49, 115)
                fill -> listOf(10, 11, 14, 15).forEachIndexed { i, row -> hit(row, 45, 115 + i * 20) }
                section == 5 -> { hit(7, 45, 90); hit(15, 46, 100) }
            }
        }
        if (section == 7 && bar == bars - 1) off(15)
        return steps
    }
}
