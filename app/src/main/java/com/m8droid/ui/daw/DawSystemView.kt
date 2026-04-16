package com.m8droid.ui.daw

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.m8droid.M8ViewModel
import com.m8droid.emulator.M8Song

@Composable
fun DawSystemView(
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
                is DawDestination.SystemMain -> SystemMainContent(viewModel, navState)
                is DawDestination.EffectDetail -> EffectDetailContent(viewModel, navState, dest.effectType)
                is DawDestination.MidiConfig -> MidiConfigContent(viewModel, navState)
                else -> SystemMainContent(viewModel, navState)
            }
        }
    }
}

// ── System Main ──────────────────────────────────────────────────────────────

@Composable
private fun SystemMainContent(viewModel: M8ViewModel, navState: DawNavState) {
    val song = viewModel.songData
    val scale = song.scales[song.activeScale]

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DawTheme.BgPanel)
            .verticalScroll(rememberScrollState())
            .padding(DawTheme.SpaceLg),
        verticalArrangement = Arrangement.spacedBy(DawTheme.SpaceMd),
    ) {
        // Song section
        SectionCard(title = "SONG") {
            EditableParamRow(
                label = "TEMPO",
                value = song.tempo,
                maxValue = 300,
                displayFn = { "$it BPM" },
                onValueChange = { viewModel.setTempo(it) },
            )
            EditableParamRow(
                label = "TRANSPOSE",
                value = song.transpose + 0x80,
                displayFn = { v ->
                    val s = v - 0x80
                    if (s >= 0) "+${M8Song.hex2(s)}" else "-${M8Song.hex2(-s)}"
                },
                onValueChange = { song.transpose = it - 0x80 },
            )
            EditableParamRow(
                label = "QUANTIZE",
                value = song.quantize,
                onValueChange = { song.quantize = it },
            )
        }

        // Scale section
        SectionCard(title = "SCALE") {
            EditableParamRow(
                label = "SLOT",
                value = song.activeScale,
                maxValue = 15,
                displayFn = { M8Song.hex2(it) },
                onValueChange = { song.activeScale = it },
            )
            KeyValueRow("NAME", song.scales[song.activeScale].name)
            EditableParamRow(
                label = "KEY",
                value = scale.key,
                maxValue = 11,
                displayFn = { arrayOf("C","C#","D","D#","E","F","F#","G","G#","A","A#","B").getOrElse(it) { "?" } },
                onValueChange = { scale.key = it },
            )
        }

        // Effects — clickable drill-down cards
        Text(
            text = "EFFECTS",
            color = DawTheme.TextLabel,
            fontSize = DawTheme.FontLabel,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(start = DawTheme.SpaceXs),
        )

        EffectCard(
            name = "REVERB",
            preview = "SIZE ${M8Song.hex2(song.reverb.size)}  DAMP ${M8Song.hex2(song.reverb.damping)}",
            onClick = { navState.push(DawDestination.EffectDetail("REVERB")) },
        )
        EffectCard(
            name = "DELAY",
            preview = "TIME ${M8Song.hex2(song.delay.timeL)}/${M8Song.hex2(song.delay.timeR)}  FBK ${M8Song.hex2(song.delay.feedback)}",
            onClick = { navState.push(DawDestination.EffectDetail("DELAY")) },
        )
        EffectCard(
            name = "CHORUS",
            preview = "DEPTH ${M8Song.hex2(song.chorus.modDepth)}  FREQ ${M8Song.hex2(song.chorus.modFreq)}",
            onClick = { navState.push(DawDestination.EffectDetail("CHORUS")) },
        )

        // Mixer overview
        SectionCard(title = "MIXER") {
            EditableParamRow(
                label = "MASTER VOL",
                value = song.mixer.masterVolume,
                onValueChange = { viewModel.setMasterVolume(it) },
            )
            EditableParamRow(
                label = "DJ FILTER",
                value = song.mixer.djFilter,
                onValueChange = { song.mixer.djFilter = it },
            )
            EditableParamRow(
                label = "CHORUS VOL",
                value = song.mixer.chorusVolume,
                onValueChange = { song.mixer.chorusVolume = it },
            )
            EditableParamRow(
                label = "DELAY VOL",
                value = song.mixer.delayVolume,
                onValueChange = { song.mixer.delayVolume = it },
            )
            EditableParamRow(
                label = "REVERB VOL",
                value = song.mixer.reverbVolume,
                onValueChange = { song.mixer.reverbVolume = it },
            )
        }

        // MIDI
        MidiCard(onClick = { navState.push(DawDestination.MidiConfig) })

        // Tutorial
        TutorialButton(onClick = { viewModel.toggleTutorial() })
        Spacer(Modifier.height(DawTheme.SpaceLg))
    }
}

// ── Effect Detail Views ──────────────────────────────────────────────────────

@Composable
private fun EffectDetailContent(viewModel: M8ViewModel, navState: DawNavState, effectType: String) {
    val song = viewModel.songData

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DawTheme.BgPanel)
            .verticalScroll(rememberScrollState())
            .padding(DawTheme.SpaceLg),
        verticalArrangement = Arrangement.spacedBy(DawTheme.SpaceMd),
    ) {
        Text(
            text = effectType,
            color = DawTheme.AccentCyan,
            fontSize = DawTheme.FontHeading,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
        )

        when (effectType) {
            "REVERB" -> {
                SectionCard("REVERB PARAMETERS") {
                    EditableParamRow(label = "SIZE", value = song.reverb.size,
                        onValueChange = { song.reverb.size = it })
                    EditableParamRow(label = "DAMPING", value = song.reverb.damping,
                        onValueChange = { song.reverb.damping = it })
                    EditableParamRow(label = "FILTER HP", value = song.reverb.filterHP,
                        onValueChange = { song.reverb.filterHP = it })
                    EditableParamRow(label = "FILTER LP", value = song.reverb.filterLP,
                        onValueChange = { song.reverb.filterLP = it })
                    EditableParamRow(label = "MOD DEPTH", value = song.reverb.modDepth,
                        onValueChange = { song.reverb.modDepth = it })
                    EditableParamRow(label = "MOD FREQ", value = song.reverb.modFreq,
                        onValueChange = { song.reverb.modFreq = it })
                    EditableParamRow(label = "WIDTH", value = song.reverb.width,
                        onValueChange = { song.reverb.width = it })
                }
            }
            "DELAY" -> {
                SectionCard("DELAY PARAMETERS") {
                    EditableParamRow(label = "TIME L", value = song.delay.timeL,
                        onValueChange = { song.delay.timeL = it })
                    EditableParamRow(label = "TIME R", value = song.delay.timeR,
                        onValueChange = { song.delay.timeR = it })
                    EditableParamRow(label = "FEEDBACK", value = song.delay.feedback,
                        onValueChange = { song.delay.feedback = it })
                    EditableParamRow(label = "FILTER HP", value = song.delay.filterHP,
                        onValueChange = { song.delay.filterHP = it })
                    EditableParamRow(label = "FILTER LP", value = song.delay.filterLP,
                        onValueChange = { song.delay.filterLP = it })
                    EditableParamRow(label = "WIDTH", value = song.delay.width,
                        onValueChange = { song.delay.width = it })
                    EditableParamRow(label = "REVERB SEND", value = song.delay.reverbSend,
                        onValueChange = { song.delay.reverbSend = it })
                }
            }
            "CHORUS" -> {
                SectionCard("CHORUS PARAMETERS") {
                    EditableParamRow(label = "MOD DEPTH", value = song.chorus.modDepth,
                        onValueChange = { song.chorus.modDepth = it })
                    EditableParamRow(label = "MOD FREQ", value = song.chorus.modFreq,
                        onValueChange = { song.chorus.modFreq = it })
                    EditableParamRow(label = "WIDTH", value = song.chorus.width,
                        onValueChange = { song.chorus.width = it })
                    EditableParamRow(label = "REVERB SEND", value = song.chorus.reverbSend,
                        onValueChange = { song.chorus.reverbSend = it })
                }
            }
        }
    }
}

// ── MIDI Config View ─────────────────────────────────────────────────────────

@Composable
private fun MidiConfigContent(viewModel: M8ViewModel, navState: DawNavState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DawTheme.BgPanel)
            .verticalScroll(rememberScrollState())
            .padding(DawTheme.SpaceLg),
        verticalArrangement = Arrangement.spacedBy(DawTheme.SpaceMd),
    ) {
        Text(
            text = "MIDI CONFIG",
            color = DawTheme.AccentCyan,
            fontSize = DawTheme.FontHeading,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
        )

        SectionCard("TRACK CHANNELS") {
            for (t in 0 until 8) {
                KeyValueRow("TRACK ${t + 1}", "CH ${t + 1}")
            }
        }

        SectionCard("STATUS") {
            KeyValueRow("INPUT", "ACTIVE")
            KeyValueRow("OUTPUT", "ACTIVE")
            KeyValueRow("SYNC", "INTERNAL")
            KeyValueRow("CLOCK OUT", "ON")
        }
    }
}

// ── Shared composables ───────────────────────────────────────────────────────

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
            fontSize = DawTheme.FontHeading,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
        )
        Spacer(Modifier.height(DawTheme.SpaceSm))
        content()
    }
}

@Composable
private fun KeyValueRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = DawTheme.SpaceXs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = DawTheme.TextDim,
            fontSize = DawTheme.FontLabel,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            color = DawTheme.TextBright,
            fontSize = DawTheme.FontMono,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun EffectCard(name: String, preview: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(DawTheme.CornerMd))
            .background(DawTheme.BgCard)
            .border(1.dp, DawTheme.BorderDim, RoundedCornerShape(DawTheme.CornerMd))
            .clickable(onClick = onClick)
            .padding(DawTheme.SpaceMd),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                color = DawTheme.AccentCyan,
                fontSize = DawTheme.FontBody,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = preview,
                color = DawTheme.TextDim,
                fontSize = DawTheme.FontLabel,
                fontFamily = FontFamily.Monospace,
            )
        }
        Text(
            text = "\u25B6",
            color = DawTheme.AccentGreen,
            fontSize = DawTheme.FontBody,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun MidiCard(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(DawTheme.CornerMd))
            .background(DawTheme.BgCard)
            .border(1.dp, DawTheme.BorderDim, RoundedCornerShape(DawTheme.CornerMd))
            .clickable(onClick = onClick)
            .padding(DawTheme.SpaceMd),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "MIDI",
                color = DawTheme.AccentCyan,
                fontSize = DawTheme.FontBody,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                text = "Channel assignments, sync, clock",
                color = DawTheme.TextDim,
                fontSize = DawTheme.FontLabel,
                fontFamily = FontFamily.Monospace,
            )
        }
        Text(
            text = "\u25B6",
            color = DawTheme.AccentGreen,
            fontSize = DawTheme.FontBody,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun TempoControl(tempo: Int, onAdjust: (Int) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("TEMPO", color = DawTheme.TextDim, fontSize = DawTheme.FontLabel,
            fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        Spacer(Modifier.height(DawTheme.SpaceXs))
        Text("$tempo BPM", color = DawTheme.AccentGreen, fontSize = DawTheme.FontTitle,
            fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
        Spacer(Modifier.height(DawTheme.SpaceSm))
        Row(horizontalArrangement = Arrangement.spacedBy(DawTheme.SpaceSm)) {
            TempoButton("-10") { onAdjust(-10) }
            TempoButton("-") { onAdjust(-1) }
            TempoButton("+") { onAdjust(1) }
            TempoButton("+10") { onAdjust(10) }
        }
    }
}

@Composable
private fun TempoButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(width = 56.dp, height = 40.dp)
            .clip(RoundedCornerShape(DawTheme.CornerMd))
            .background(DawTheme.BgCardHi)
            .border(1.dp, DawTheme.AccentGreen, RoundedCornerShape(DawTheme.CornerMd))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = DawTheme.AccentGreen, fontSize = DawTheme.FontHeading,
            fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun TutorialButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(DawTheme.CornerMd))
            .background(DawTheme.BgCardHi)
            .border(1.dp, DawTheme.AccentMagenta, RoundedCornerShape(DawTheme.CornerMd))
            .clickable(onClick = onClick)
            .padding(vertical = DawTheme.SpaceMd),
        contentAlignment = Alignment.Center,
    ) {
        Text("TUTORIAL", color = DawTheme.AccentMagenta, fontSize = DawTheme.FontHeading,
            fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
}
