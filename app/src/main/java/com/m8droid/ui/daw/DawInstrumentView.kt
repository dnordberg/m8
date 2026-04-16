package com.m8droid.ui.daw

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
fun DawInstrumentView(
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
                is DawDestination.InstrumentList -> InstrumentListContent(viewModel, navState)
                is DawDestination.InstrumentDetail -> InstrumentDetailContent(viewModel, navState, dest.slot)
                else -> InstrumentListContent(viewModel, navState)
            }
        }
    }
}

// ── Instrument List ──────────────────────────────────────────────────────────

@Composable
private fun InstrumentListContent(viewModel: M8ViewModel, navState: DawNavState) {
    var expandedIdx by remember { mutableIntStateOf(-1) }
    val instruments = viewModel.instrumentList

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(DawTheme.SpaceLg),
        verticalArrangement = Arrangement.spacedBy(DawTheme.SpaceSm),
    ) {
        item {
            Text(
                text = "INSTRUMENTS",
                color = DawTheme.TextLabel,
                fontSize = DawTheme.FontLabel,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(start = DawTheme.SpaceXs, bottom = DawTheme.SpaceXs),
            )
        }
        itemsIndexed(instruments) { index, inst ->
            InstrumentCard(
                index = index,
                instrument = inst,
                expanded = index == expandedIdx,
                onToggle = { expandedIdx = if (expandedIdx == index) -1 else index },
                onEdit = { navState.push(DawDestination.InstrumentDetail(index)) },
            )
        }
        item { Spacer(Modifier.height(DawTheme.SpaceLg)) }
    }
}

@Composable
private fun InstrumentCard(
    index: Int,
    instrument: M8Instrument,
    expanded: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
) {
    val borderColor = if (expanded) DawTheme.AccentGreen else DawTheme.BorderDim
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(DawTheme.CornerMd))
            .background(DawTheme.BgCard)
            .border(1.dp, borderColor, RoundedCornerShape(DawTheme.CornerMd)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = DawTheme.SpaceMd, vertical = DawTheme.SpaceMd),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "IN$index",
                color = if (expanded) DawTheme.AccentGreen else DawTheme.TextDim,
                fontSize = DawTheme.FontLabel,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.width(DawTheme.SpaceSm))
            Text(
                text = instrument.name,
                color = if (expanded) DawTheme.TextBright else DawTheme.TextNormal,
                fontSize = DawTheme.FontBody,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = instrument.type.label,
                color = DawTheme.AccentCyan,
                fontSize = DawTheme.FontLabel,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.width(DawTheme.SpaceSm))
            Text(
                text = if (expanded) "\u25B2" else "\u25BC",
                color = DawTheme.TextDim,
                fontSize = DawTheme.FontLabel,
                fontFamily = FontFamily.Monospace,
            )
        }

        AnimatedVisibility(visible = expanded, enter = expandVertically(), exit = shrinkVertically()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DawTheme.BgCardHi)
                    .padding(DawTheme.SpaceMd),
                verticalArrangement = Arrangement.spacedBy(DawTheme.SpaceSm),
            ) {
                // Quick param preview
                val typeParams = instrument.getTypeParams()
                typeParams.take(3).forEach { (label, value) ->
                    ReadOnlyParamRow(label, value)
                }
                ReadOnlyParamRow("FILTER", "${instrument.filter.type.label} ${M8Song.hex2(instrument.filter.cutoff)}")
                ReadOnlyParamRow("AMP", M8Song.hex2(instrument.amp.amp))

                // Edit button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(DawTheme.CornerSm))
                        .background(DawTheme.AccentGreen)
                        .clickable(onClick = onEdit)
                        .padding(vertical = DawTheme.SpaceSm),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "EDIT INSTRUMENT",
                        color = Color.Black,
                        fontSize = DawTheme.FontLabel,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
    }
}

// ── Instrument Detail (full editor) ──────────────────────────────────────────

@Composable
private fun InstrumentDetailContent(viewModel: M8ViewModel, navState: DawNavState, slot: Int) {
    val instruments = viewModel.instrumentList
    if (slot !in instruments.indices) return
    val inst = instruments[slot]

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
                text = "INST ${M8Song.hex2(slot)}",
                color = DawTheme.AccentGreen,
                fontSize = DawTheme.FontHeading,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = inst.name,
                color = DawTheme.TextBright,
                fontSize = DawTheme.FontHeading,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
        }

        // Type selector
        DetailSection("TYPE") {
            EditableEnumRow(
                label = "ENGINE",
                value = inst.type,
                values = InstrumentType.entries.toTypedArray(),
                labelFn = { it.label },
                onValueChange = { inst.type = it },
            )
        }

        // Type-specific params
        when (inst.type) {
            InstrumentType.WAVSYNTH -> WavSynthEditor(inst)
            InstrumentType.MACROSYNTH -> MacroSynthEditor(inst)
            InstrumentType.FM_SYNTH -> FmSynthEditor(inst)
            InstrumentType.HYPERSYNTH -> HyperSynthEditor(inst)
            InstrumentType.SAMPLER -> SamplerEditor(inst)
            InstrumentType.MIDI_OUT -> MidiOutInfo()
        }

        // Filter
        DetailSection("FILTER") {
            EditableEnumRow(
                label = "TYPE",
                value = inst.filter.type,
                values = FilterType.entries.toTypedArray(),
                labelFn = { it.label },
                onValueChange = { inst.filter.type = it },
            )
            EditableParamRow(label = "CUTOFF", value = inst.filter.cutoff,
                onValueChange = { inst.filter.cutoff = it })
            EditableParamRow(label = "RES", value = inst.filter.resonance,
                onValueChange = { inst.filter.resonance = it })
        }

        // Amp
        DetailSection("AMP") {
            EditableParamRow(label = "AMP", value = inst.amp.amp,
                onValueChange = { inst.amp.amp = it })
            EditableEnumRow(
                label = "LIMITER",
                value = inst.amp.limiter,
                values = LimiterType.entries.toTypedArray(),
                labelFn = { it.label },
                onValueChange = { inst.amp.limiter = it },
            )
            EditableParamRow(label = "PAN", value = inst.amp.pan,
                onValueChange = { inst.amp.pan = it })
            EditableParamRow(label = "DRY", value = inst.amp.dry,
                onValueChange = { inst.amp.dry = it })
        }

        // Sends
        DetailSection("SENDS") {
            EditableParamRow(label = "CHORUS", value = inst.amp.chorusSend,
                onValueChange = { inst.amp.chorusSend = it })
            EditableParamRow(label = "DELAY", value = inst.amp.delaySend,
                onValueChange = { inst.amp.delaySend = it })
            EditableParamRow(label = "REVERB", value = inst.amp.reverbSend,
                onValueChange = { inst.amp.reverbSend = it })
        }

        Spacer(Modifier.height(DawTheme.SpaceLg))
    }
}

// ── Type-specific editors ────────────────────────────────────────────────────

@Composable
private fun WavSynthEditor(inst: M8Instrument) {
    DetailSection("WAVSYNTH") {
        EditableEnumRow(
            label = "SHAPE",
            value = inst.wavSynth.shape,
            values = WavShape.entries.toTypedArray(),
            labelFn = { it.label },
            onValueChange = { inst.wavSynth.shape = it },
        )
        EditableParamRow(label = "SIZE", value = inst.wavSynth.size,
            onValueChange = { inst.wavSynth.size = it })
        EditableParamRow(label = "MULT", value = inst.wavSynth.mult,
            onValueChange = { inst.wavSynth.mult = it })
        EditableParamRow(label = "WARP", value = inst.wavSynth.warp,
            onValueChange = { inst.wavSynth.warp = it })
        EditableParamRow(label = "MIRROR", value = inst.wavSynth.mirror, maxValue = 1,
            displayFn = { if (it > 0) "ON" else "OFF" },
            onValueChange = { inst.wavSynth.mirror = it })
    }
}

@Composable
private fun MacroSynthEditor(inst: M8Instrument) {
    DetailSection("MACROSYNTH") {
        EditableParamRow(label = "MODEL", value = inst.macroSynth.model, maxValue = 43,
            displayFn = { MacroSynthParams.modelName(it) },
            onValueChange = { inst.macroSynth.model = it })
        EditableParamRow(label = "TIMBRE", value = inst.macroSynth.timbre,
            onValueChange = { inst.macroSynth.timbre = it })
        EditableParamRow(label = "COLOR", value = inst.macroSynth.color,
            onValueChange = { inst.macroSynth.color = it })
        EditableParamRow(label = "DEGRADE", value = inst.macroSynth.degrade,
            onValueChange = { inst.macroSynth.degrade = it })
        EditableParamRow(label = "REDUX", value = inst.macroSynth.redux,
            onValueChange = { inst.macroSynth.redux = it })
    }
}

@Composable
private fun FmSynthEditor(inst: M8Instrument) {
    DetailSection("FM SYNTH") {
        EditableEnumRow(
            label = "ALGO",
            value = inst.fmSynth.algorithm,
            values = FmAlgorithm.entries.toTypedArray(),
            labelFn = { it.label },
            onValueChange = { inst.fmSynth.algorithm = it },
        )
    }
    for (op in 1..4) {
        DetailSection("OPERATOR $op") {
            val shape: FmOperatorShape
            val ratio: Int
            val level: Int
            val fb: Int
            when (op) {
                1 -> { shape = inst.fmSynth.op1Shape; ratio = inst.fmSynth.op1Ratio; level = inst.fmSynth.op1Level; fb = inst.fmSynth.op1Feedback }
                2 -> { shape = inst.fmSynth.op2Shape; ratio = inst.fmSynth.op2Ratio; level = inst.fmSynth.op2Level; fb = inst.fmSynth.op2Feedback }
                3 -> { shape = inst.fmSynth.op3Shape; ratio = inst.fmSynth.op3Ratio; level = inst.fmSynth.op3Level; fb = inst.fmSynth.op3Feedback }
                else -> { shape = inst.fmSynth.op4Shape; ratio = inst.fmSynth.op4Ratio; level = inst.fmSynth.op4Level; fb = inst.fmSynth.op4Feedback }
            }
            EditableEnumRow(
                label = "SHAPE",
                value = shape,
                values = FmOperatorShape.entries.toTypedArray(),
                labelFn = { it.label },
                onValueChange = { v ->
                    when (op) {
                        1 -> inst.fmSynth.op1Shape = v; 2 -> inst.fmSynth.op2Shape = v
                        3 -> inst.fmSynth.op3Shape = v; else -> inst.fmSynth.op4Shape = v
                    }
                },
            )
            EditableParamRow(label = "RATIO", value = ratio, onValueChange = { v ->
                when (op) {
                    1 -> inst.fmSynth.op1Ratio = v; 2 -> inst.fmSynth.op2Ratio = v
                    3 -> inst.fmSynth.op3Ratio = v; else -> inst.fmSynth.op4Ratio = v
                }
            })
            EditableParamRow(label = "LEVEL", value = level, onValueChange = { v ->
                when (op) {
                    1 -> inst.fmSynth.op1Level = v; 2 -> inst.fmSynth.op2Level = v
                    3 -> inst.fmSynth.op3Level = v; else -> inst.fmSynth.op4Level = v
                }
            })
            EditableParamRow(label = "FEEDBACK", value = fb, onValueChange = { v ->
                when (op) {
                    1 -> inst.fmSynth.op1Feedback = v; 2 -> inst.fmSynth.op2Feedback = v
                    3 -> inst.fmSynth.op3Feedback = v; else -> inst.fmSynth.op4Feedback = v
                }
            })
        }
    }
}

@Composable
private fun HyperSynthEditor(inst: M8Instrument) {
    DetailSection("HYPERSYNTH") {
        EditableParamRow(label = "CHORD BNK", value = inst.hyperSynth.chordBank, maxValue = 15,
            displayFn = { HyperSynthParams.CHORD_BANK_NAMES.getOrElse(it) { "---" } },
            onValueChange = { inst.hyperSynth.chordBank = it })
        EditableParamRow(label = "CHORD", value = inst.hyperSynth.chord,
            onValueChange = { inst.hyperSynth.chord = it })
        EditableParamRow(label = "SHIFT", value = inst.hyperSynth.shift,
            onValueChange = { inst.hyperSynth.shift = it })
        EditableParamRow(label = "SWARM", value = inst.hyperSynth.swarm,
            onValueChange = { inst.hyperSynth.swarm = it })
        EditableParamRow(label = "WIDTH", value = inst.hyperSynth.width,
            onValueChange = { inst.hyperSynth.width = it })
        EditableParamRow(label = "SUB OSC", value = inst.hyperSynth.subOsc,
            onValueChange = { inst.hyperSynth.subOsc = it })
    }
}

@Composable
private fun SamplerEditor(inst: M8Instrument) {
    DetailSection("SAMPLER") {
        EditableParamRow(label = "PLAY MODE", value = inst.sampler.playMode, maxValue = 6,
            displayFn = { SamplerParams.PLAY_MODE_NAMES.getOrElse(it) { "FWD" } },
            onValueChange = { inst.sampler.playMode = it })
        EditableParamRow(label = "START", value = inst.sampler.start,
            onValueChange = { inst.sampler.start = it })
        EditableParamRow(label = "LOOP START", value = inst.sampler.loopStart,
            onValueChange = { inst.sampler.loopStart = it })
        EditableParamRow(label = "LENGTH", value = inst.sampler.length,
            onValueChange = { inst.sampler.length = it })
        EditableParamRow(label = "DEGRADE", value = inst.sampler.degrade,
            onValueChange = { inst.sampler.degrade = it })
        EditableParamRow(label = "DETUNE", value = inst.sampler.detune,
            onValueChange = { inst.sampler.detune = it })
    }
}

@Composable
private fun MidiOutInfo() {
    DetailSection("MIDI OUT") {
        ReadOnlyParamRow("PORT", "01")
        ReadOnlyParamRow("CHANNEL", "01")
        ReadOnlyParamRow("BANK SEL", "--")
        ReadOnlyParamRow("PROGRAM", "--")
    }
}

// ── Shared composables ───────────────────────────────────────────────────────

@Composable
private fun DetailSection(title: String, content: @Composable ColumnScope.() -> Unit) {
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
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(bottom = DawTheme.SpaceXs),
        )
        content()
    }
}

@Composable
private fun ReadOnlyParamRow(label: String, value: String) {
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
