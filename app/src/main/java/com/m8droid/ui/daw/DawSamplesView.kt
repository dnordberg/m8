package com.m8droid.ui.daw

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.m8droid.M8ViewModel
import com.m8droid.emulator.*

@Composable
fun DawSamplesView(
    viewModel: M8ViewModel,
    navState: DawNavState,
    modifier: Modifier = Modifier,
) {
    val tick by viewModel.displayTick.collectAsState()
    @Suppress("UNUSED_EXPRESSION") tick

    DawBackHandler(navState = navState, modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            DawBreadcrumb(navState)

            when (val dest = navState.current) {
                is DawDestination.SampleBrowser -> SampleBrowserContent(viewModel, navState)
                is DawDestination.SampleDetail -> SampleDetailContent(viewModel, navState, dest.slot)
                else -> SampleBrowserContent(viewModel, navState)
            }
        }
    }
}

// ── Main Browser ─────────────────────────────────────────────────────────────

@Composable
private fun SampleBrowserContent(viewModel: M8ViewModel, navState: DawNavState) {
    val instruments = viewModel.instrumentList
    val song = viewModel.songData
    var expandedCategory by remember { mutableStateOf<InstrumentType?>(null) }
    var loadTarget by remember { mutableIntStateOf(-1) }
    var loadPreset by remember { mutableStateOf<DawPresets.Preset?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(DawTheme.BgPanel)
            .padding(DawTheme.SpaceLg),
        verticalArrangement = Arrangement.spacedBy(DawTheme.SpaceSm),
    ) {
        // Active instruments section
        item {
            Text(
                text = "ACTIVE_INSTRUMENTS",
                color = DawTheme.TextLabel,
                fontSize = DawTheme.FontHeading,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
        }

        itemsIndexed(instruments.toList().take(8)) { index, inst ->
            val usage = countInstrumentUsage(song, index)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(DawTheme.CornerMd))
                    .background(DawTheme.BgCard)
                    .border(1.dp, DawTheme.BorderDim, RoundedCornerShape(DawTheme.CornerMd))
                    .clickable { navState.push(DawDestination.SampleDetail(index)) }
                    .padding(horizontal = DawTheme.SpaceMd, vertical = DawTheme.SpaceSm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = String.format("%02X", index),
                    color = DawTheme.AccentGreen,
                    fontSize = DawTheme.FontMono,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                )
                Spacer(Modifier.width(DawTheme.SpaceMd))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = inst.name,
                        color = DawTheme.TextBright,
                        fontSize = DawTheme.FontBody,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                    )
                    Text(
                        text = inst.type.label,
                        color = DawTheme.AccentCyan,
                        fontSize = DawTheme.FontLabel,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                // Usage badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(DawTheme.CornerSm))
                        .background(if (usage > 0) DawTheme.AccentGreenDim else DawTheme.BgCardHi)
                        .padding(horizontal = DawTheme.SpaceSm, vertical = 2.dp),
                ) {
                    Text(
                        text = "$usage steps",
                        color = if (usage > 0) DawTheme.AccentGreen else DawTheme.TextDim,
                        fontSize = DawTheme.FontLabel,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }

        // Preset browser section
        item {
            Spacer(Modifier.height(DawTheme.SpaceMd))
            Text(
                text = "PRESET_BROWSER",
                color = DawTheme.TextLabel,
                fontSize = DawTheme.FontHeading,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
        }

        // Categories
        val categories = DawPresets.presets.keys.toList()
        items(categories.size) { catIdx ->
            val type = categories[catIdx]
            val presets = DawPresets.presets[type] ?: emptyList()
            val isExpanded = expandedCategory == type

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(DawTheme.CornerMd))
                    .background(DawTheme.BgCard)
                    .border(
                        1.dp,
                        if (isExpanded) DawTheme.AccentCyan else DawTheme.BorderDim,
                        RoundedCornerShape(DawTheme.CornerMd),
                    ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expandedCategory = if (isExpanded) null else type }
                        .padding(DawTheme.SpaceMd),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = type.label,
                        color = DawTheme.AccentCyan,
                        fontSize = DawTheme.FontBody,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "${presets.size} presets",
                        color = DawTheme.TextDim,
                        fontSize = DawTheme.FontLabel,
                        fontFamily = FontFamily.Monospace,
                    )
                    Spacer(Modifier.width(DawTheme.SpaceSm))
                    Text(
                        text = if (isExpanded) "\u25B2" else "\u25BC",
                        color = DawTheme.TextDim,
                        fontSize = DawTheme.FontLabel,
                        fontFamily = FontFamily.Monospace,
                    )
                }

                if (isExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DawTheme.BgCardHi)
                            .padding(DawTheme.SpaceSm),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        presets.forEach { preset ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(DawTheme.CornerSm))
                                    .background(DawTheme.BgCard)
                                    .clickable { loadPreset = preset; loadTarget = 0 }
                                    .padding(horizontal = DawTheme.SpaceMd, vertical = DawTheme.SpaceSm),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = preset.name,
                                    color = DawTheme.TextBright,
                                    fontSize = DawTheme.FontMono,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = "LOAD",
                                    color = DawTheme.AccentGreen,
                                    fontSize = DawTheme.FontLabel,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Quick actions
        item {
            Spacer(Modifier.height(DawTheme.SpaceMd))
            Text(
                text = "QUICK_ACTIONS",
                color = DawTheme.TextLabel,
                fontSize = DawTheme.FontLabel,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
        }
        item { Spacer(Modifier.height(DawTheme.SpaceLg)) }
    }

    // Load confirmation dialog
    if (loadPreset != null) {
        LoadPresetDialog(
            preset = loadPreset!!,
            instruments = instruments,
            onConfirm = { slot ->
                val newInst = loadPreset!!.create()
                viewModel.replaceInstrument(slot, newInst)
                loadPreset = null
            },
            onDismiss = { loadPreset = null },
        )
    }
}

@Composable
private fun LoadPresetDialog(
    preset: DawPresets.Preset,
    instruments: Array<M8Instrument>,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .clip(RoundedCornerShape(DawTheme.CornerLg))
                .background(DawTheme.BgCard)
                .border(1.dp, DawTheme.AccentGreen, RoundedCornerShape(DawTheme.CornerLg))
                .clickable(enabled = false) {} // consume clicks
                .padding(DawTheme.SpaceLg),
            verticalArrangement = Arrangement.spacedBy(DawTheme.SpaceMd),
        ) {
            Text(
                text = "LOAD \"${preset.name}\"",
                color = DawTheme.AccentGreen,
                fontSize = DawTheme.FontHeading,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                text = "Select target slot:",
                color = DawTheme.TextNormal,
                fontSize = DawTheme.FontBody,
                fontFamily = FontFamily.Monospace,
            )

            for (i in 0 until minOf(8, instruments.size)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(DawTheme.CornerSm))
                        .background(DawTheme.BgCardHi)
                        .clickable { onConfirm(i) }
                        .padding(DawTheme.SpaceSm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${M8Song.hex2(i)}",
                        color = DawTheme.AccentGreen,
                        fontSize = DawTheme.FontMono,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                    )
                    Spacer(Modifier.width(DawTheme.SpaceSm))
                    Text(
                        text = instruments[i].name,
                        color = DawTheme.TextNormal,
                        fontSize = DawTheme.FontMono,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "REPLACE",
                        color = DawTheme.AccentYellow,
                        fontSize = DawTheme.FontLabel,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(DawTheme.CornerSm))
                    .border(1.dp, DawTheme.TextDim, RoundedCornerShape(DawTheme.CornerSm))
                    .clickable(onClick = onDismiss)
                    .padding(DawTheme.SpaceSm),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "CANCEL",
                    color = DawTheme.TextDim,
                    fontSize = DawTheme.FontBody,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

// ── Sample Detail View ───────────────────────────────────────────────────────

@Composable
private fun SampleDetailContent(viewModel: M8ViewModel, navState: DawNavState, slot: Int) {
    val instruments = viewModel.instrumentList
    if (slot !in instruments.indices) return
    val inst = instruments[slot]
    val song = viewModel.songData
    val usage = findInstrumentUsageDetails(song, slot)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .background(DawTheme.BgPanel)
            .padding(DawTheme.SpaceLg),
        verticalArrangement = Arrangement.spacedBy(DawTheme.SpaceMd),
    ) {
        // Header
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "SLOT ${M8Song.hex2(slot)}",
                color = DawTheme.AccentGreen,
                fontSize = DawTheme.FontHeading,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "${inst.name} (${inst.type.label})",
                color = DawTheme.TextBright,
                fontSize = DawTheme.FontBody,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
        }

        // Params preview
        SectionCard("PARAMETERS") {
            inst.getTypeParams().forEach { (label, value) ->
                ParamPreviewRow(label, value)
            }
            ParamPreviewRow("FILTER", "${inst.filter.type.label} CUT:${M8Song.hex2(inst.filter.cutoff)}")
            ParamPreviewRow("AMP", M8Song.hex2(inst.amp.amp))
            ParamPreviewRow("PAN", M8Song.hex2(inst.amp.pan))
        }

        // Usage
        SectionCard("USAGE (${usage.size} phrases)") {
            if (usage.isEmpty()) {
                Text(
                    text = "Not used in any phrase",
                    color = DawTheme.TextDim,
                    fontSize = DawTheme.FontBody,
                    fontFamily = FontFamily.Monospace,
                )
            } else {
                usage.take(20).forEach { (phraseIdx, stepCount) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = DawTheme.SpaceXs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "PHRASE ${M8Song.hex2(phraseIdx)}",
                            color = DawTheme.AccentCyan,
                            fontSize = DawTheme.FontMono,
                            fontFamily = FontFamily.Monospace,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "$stepCount steps",
                            color = DawTheme.TextNormal,
                            fontSize = DawTheme.FontMono,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        }

        // Init button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(DawTheme.CornerMd))
                .background(DawTheme.BgCardHi)
                .border(1.dp, DawTheme.AccentRed, RoundedCornerShape(DawTheme.CornerMd))
                .clickable {
                    val defaults = M8Instrument.createDefaults()
                    val newInst = if (slot < defaults.size) defaults[slot] else M8Instrument()
                    viewModel.replaceInstrument(slot, newInst)
                }
                .padding(DawTheme.SpaceMd),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "INIT (RESET TO DEFAULT)",
                color = DawTheme.AccentRed,
                fontSize = DawTheme.FontBody,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
        }

        Spacer(Modifier.height(DawTheme.SpaceLg))
    }
}

// ── Helpers ──────────────────────────────────────────────────────────────────

private fun countInstrumentUsage(song: M8Song, instrumentIdx: Int): Int {
    var count = 0
    for (phrase in song.phrases) {
        for (step in phrase.steps) {
            if (step.instrument == instrumentIdx) count++
        }
    }
    return count
}

private fun findInstrumentUsageDetails(song: M8Song, instrumentIdx: Int): List<Pair<Int, Int>> {
    val result = mutableListOf<Pair<Int, Int>>()
    for (pi in song.phrases.indices) {
        val count = song.phrases[pi].steps.count { it.instrument == instrumentIdx }
        if (count > 0) result.add(pi to count)
    }
    return result
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(DawTheme.CornerMd))
            .background(DawTheme.BgCard)
            .border(1.dp, DawTheme.BorderDim, RoundedCornerShape(DawTheme.CornerMd))
            .padding(DawTheme.SpaceMd),
    ) {
        Text(
            text = title,
            color = DawTheme.TextLabel,
            fontSize = DawTheme.FontLabel,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(bottom = DawTheme.SpaceXs),
        )
        content()
    }
}

@Composable
private fun ParamPreviewRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = DawTheme.SpaceXs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, color = DawTheme.TextDim, fontSize = DawTheme.FontMono, fontFamily = FontFamily.Monospace)
        Spacer(Modifier.weight(1f))
        Text(text = value, color = DawTheme.TextBright, fontSize = DawTheme.FontMono,
            fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
}

// ── Presets ──────────────────────────────────────────────────────────────────

object DawPresets {
    data class Preset(val name: String, val type: InstrumentType, val create: () -> M8Instrument)

    val presets: Map<InstrumentType, List<Preset>> = mapOf(
        InstrumentType.WAVSYNTH to listOf(
            Preset("INIT SAW", InstrumentType.WAVSYNTH) {
                M8Instrument("INIT SAW", InstrumentType.WAVSYNTH).apply {
                    wavSynth = WavSynthParams(WavShape.SAW)
                    filter = FilterParams(FilterType.LP, 0xFF, 0x00)
                }
            },
            Preset("THICK BASS", InstrumentType.WAVSYNTH) {
                M8Instrument("THICK BASS", InstrumentType.WAVSYNTH).apply {
                    wavSynth = WavSynthParams(WavShape.SAW, 0x80, 0x01, 0x20)
                    filter = FilterParams(FilterType.LP, 0x50, 0x50)
                    amp = AmpParams(0x90, LimiterType.CLIP, 0x80, 0xC0)
                }
            },
            Preset("SOFT PAD", InstrumentType.WAVSYNTH) {
                M8Instrument("SOFT PAD", InstrumentType.WAVSYNTH).apply {
                    wavSynth = WavSynthParams(WavShape.TRIANGLE)
                    filter = FilterParams(FilterType.LP, 0xD0, 0x10)
                    amp = AmpParams(0x70, LimiterType.CLIP, 0x80, 0xC0, 0x40, 0x00, 0x40)
                }
            },
            Preset("NOISE HIT", InstrumentType.WAVSYNTH) {
                M8Instrument("NOISE HIT", InstrumentType.WAVSYNTH).apply {
                    wavSynth = WavSynthParams(WavShape.NOISE)
                    filter = FilterParams(FilterType.HP, 0x90, 0x20)
                }
            },
        ),
        InstrumentType.MACROSYNTH to listOf(
            Preset("CSAW LEAD", InstrumentType.MACROSYNTH) {
                M8Instrument("CSAW LEAD", InstrumentType.MACROSYNTH).apply {
                    macroSynth = MacroSynthParams(model = 0, timbre = 0x60, color = 0x80)
                    filter = FilterParams(FilterType.LP, 0xB0, 0x30)
                }
            },
            Preset("VOWEL PAD", InstrumentType.MACROSYNTH) {
                M8Instrument("VOWEL PAD", InstrumentType.MACROSYNTH).apply {
                    macroSynth = MacroSynthParams(model = 22, timbre = 0x80, color = 0x40)
                    filter = FilterParams(FilterType.LP, 0xE0, 0x10)
                    amp = AmpParams(0x70, LimiterType.CLIP, 0x80, 0xC0, 0x30, 0x00, 0x30)
                }
            },
            Preset("PLUCKED", InstrumentType.MACROSYNTH) {
                M8Instrument("PLUCKED", InstrumentType.MACROSYNTH).apply {
                    macroSynth = MacroSynthParams(model = 28, timbre = 0x80, color = 0x60)
                }
            },
            Preset("KICK DRUM", InstrumentType.MACROSYNTH) {
                M8Instrument("KICK DRUM", InstrumentType.MACROSYNTH).apply {
                    macroSynth = MacroSynthParams(model = 33, timbre = 0x80, color = 0x40)
                    filter = FilterParams(FilterType.LP, 0xFF, 0x00)
                    amp = AmpParams(0xA0, LimiterType.CLIP, 0x80, 0xC0)
                }
            },
        ),
        InstrumentType.FM_SYNTH to listOf(
            Preset("FM BELL", InstrumentType.FM_SYNTH) {
                M8Instrument("FM BELL", InstrumentType.FM_SYNTH).apply {
                    fmSynth = FmSynthParams(
                        algorithm = FmAlgorithm.ALG_A,
                        op1Ratio = 0x01, op1Level = 0xFF,
                        op2Ratio = 0x03, op2Level = 0xC0,
                        op3Ratio = 0x05, op3Level = 0x80,
                        op4Ratio = 0x07, op4Level = 0x60,
                    )
                    amp = AmpParams(0x70, LimiterType.SIN, 0x80, 0xC0, 0x00, 0x00, 0x40)
                }
            },
            Preset("FM BASS", InstrumentType.FM_SYNTH) {
                M8Instrument("FM BASS", InstrumentType.FM_SYNTH).apply {
                    fmSynth = FmSynthParams(
                        algorithm = FmAlgorithm.ALG_A,
                        op1Ratio = 0x01, op1Level = 0xFF,
                        op2Ratio = 0x02, op2Level = 0xE0,
                    )
                    filter = FilterParams(FilterType.LP, 0x80, 0x30)
                }
            },
            Preset("FM KEYS", InstrumentType.FM_SYNTH) {
                M8Instrument("FM KEYS", InstrumentType.FM_SYNTH).apply {
                    fmSynth = FmSynthParams(
                        algorithm = FmAlgorithm.ALG_B,
                        op1Ratio = 0x01, op1Level = 0xFF,
                        op2Ratio = 0x02, op2Level = 0x90,
                        op3Ratio = 0x04, op3Level = 0x60,
                    )
                }
            },
            Preset("FM PAD", InstrumentType.FM_SYNTH) {
                M8Instrument("FM PAD", InstrumentType.FM_SYNTH).apply {
                    fmSynth = FmSynthParams(
                        algorithm = FmAlgorithm.ALG_D,
                        op1Ratio = 0x01, op1Level = 0xFF,
                        op2Ratio = 0x02, op2Level = 0x80,
                        op3Ratio = 0x01, op3Level = 0xC0,
                        op4Ratio = 0x03, op4Level = 0x60,
                    )
                    amp = AmpParams(0x60, LimiterType.SIN, 0x80, 0xC0, 0x20, 0x20, 0x40)
                }
            },
        ),
        InstrumentType.HYPERSYNTH to listOf(
            Preset("MAJOR CHORD", InstrumentType.HYPERSYNTH) {
                M8Instrument("MAJOR CHORD", InstrumentType.HYPERSYNTH).apply {
                    hyperSynth = HyperSynthParams(chordBank = 0, chord = 0, swarm = 0x20, width = 0xFF)
                }
            },
            Preset("MINOR CHORD", InstrumentType.HYPERSYNTH) {
                M8Instrument("MINOR CHORD", InstrumentType.HYPERSYNTH).apply {
                    hyperSynth = HyperSynthParams(chordBank = 1, chord = 0, swarm = 0x20, width = 0xFF)
                }
            },
            Preset("POWER STACK", InstrumentType.HYPERSYNTH) {
                M8Instrument("POWER STACK", InstrumentType.HYPERSYNTH).apply {
                    hyperSynth = HyperSynthParams(chordBank = 9, chord = 0, swarm = 0x40, width = 0xFF)
                    amp = AmpParams(0x80, LimiterType.CLIP, 0x80, 0xC0)
                }
            },
            Preset("WIDE DETUNE", InstrumentType.HYPERSYNTH) {
                M8Instrument("WIDE DETUNE", InstrumentType.HYPERSYNTH).apply {
                    hyperSynth = HyperSynthParams(chordBank = 0, chord = 0, swarm = 0x80, width = 0xFF)
                    filter = FilterParams(FilterType.LP, 0xC0, 0x20)
                }
            },
        ),
    )
}
