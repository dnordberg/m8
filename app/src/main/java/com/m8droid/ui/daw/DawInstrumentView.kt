package com.m8droid.ui.daw

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.m8droid.emulator.M8Instrument
import com.m8droid.emulator.M8Song

@Composable
fun DawInstrumentView(viewModel: M8ViewModel, modifier: Modifier = Modifier) {
    // Recompose at display tick rate so values stay fresh with the emulator.
    val tick by viewModel.displayTick.collectAsState()
    @Suppress("UNUSED_EXPRESSION") tick

    var selectedIdx by remember { mutableStateOf(0) }
    val instruments = viewModel.instrumentList
    val safeIdx = selectedIdx.coerceIn(0, instruments.size - 1)
    val current = instruments[safeIdx]

    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(DawTheme.SpaceLg),
        horizontalArrangement = Arrangement.spacedBy(DawTheme.SpaceLg),
    ) {
        // Left column — instrument list
        Column(
            modifier = Modifier
                .width(160.dp)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(DawTheme.SpaceSm),
        ) {
            Text(
                text = "INSTRUMENTS",
                color = DawTheme.TextLabel,
                fontSize = DawTheme.FontLabel,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(start = DawTheme.SpaceXs, bottom = DawTheme.SpaceXs),
            )
            instruments.forEachIndexed { index, inst ->
                InstrumentListItem(
                    index = index,
                    instrument = inst,
                    selected = index == safeIdx,
                    onClick = { selectedIdx = index },
                )
            }
        }

        // Right column — details
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(DawTheme.CornerMd))
                .background(DawTheme.BgCard)
                .border(1.dp, DawTheme.BorderDim, RoundedCornerShape(DawTheme.CornerMd))
                .padding(DawTheme.SpaceLg),
        ) {
            InstrumentDetails(index = safeIdx, instrument = current)
        }
    }
}

@Composable
private fun InstrumentListItem(
    index: Int,
    instrument: M8Instrument,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bg = if (selected) DawTheme.AccentGreen else DawTheme.BgCard
    val fg = if (selected) Color.Black else DawTheme.TextNormal
    val typeColor = if (selected) Color.Black else DawTheme.TextDim
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(DawTheme.CornerSm))
            .background(bg)
            .border(
                1.dp,
                if (selected) DawTheme.AccentGreen else DawTheme.BorderDim,
                RoundedCornerShape(DawTheme.CornerSm),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = DawTheme.SpaceMd, vertical = DawTheme.SpaceSm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "IN$index",
                color = fg,
                fontSize = DawTheme.FontLabel,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.width(DawTheme.SpaceSm))
            Text(
                text = instrument.name,
                color = fg,
                fontSize = DawTheme.FontBody,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = instrument.type.label,
            color = typeColor,
            fontSize = DawTheme.FontLabel,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun InstrumentDetails(index: Int, instrument: M8Instrument) {
    // TODO: wire up setters when ViewModel exposes write API for instrument params.
    val typeParams = instrument.getTypeParams()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(DawTheme.SpaceSm),
    ) {
        item {
            Column {
                Text(
                    text = "IN${index.toString()} / NAME",
                    color = DawTheme.TextLabel,
                    fontSize = DawTheme.FontLabel,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                )
                Spacer(Modifier.height(DawTheme.SpaceXs))
                Text(
                    text = instrument.name,
                    color = DawTheme.TextBright,
                    fontSize = DawTheme.FontTitle,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                )
                Spacer(Modifier.height(DawTheme.SpaceSm))
                Text(
                    text = "TYPE",
                    color = DawTheme.TextLabel,
                    fontSize = DawTheme.FontLabel,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                )
                Spacer(Modifier.height(DawTheme.SpaceXs))
                Text(
                    text = instrument.type.label,
                    color = DawTheme.AccentCyan,
                    fontSize = DawTheme.FontHeading,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }

        item { SectionHeader("SYNTH PARAMS") }
        item {
            ParamCard {
                typeParams.forEach { (label, value) ->
                    ParamRow(label, value)
                }
                if (typeParams.isEmpty()) {
                    Text(
                        text = "(no params)",
                        color = DawTheme.TextDim,
                        fontSize = DawTheme.FontMono,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }

        item { SectionHeader("FILTER") }
        item {
            ParamCard {
                ParamRow("TYPE", instrument.filter.type.label)
                ParamRow("CUTOFF", M8Song.hex2(instrument.filter.cutoff))
                ParamRow("RES", M8Song.hex2(instrument.filter.resonance))
            }
        }

        item { SectionHeader("AMP") }
        item {
            ParamCard {
                ParamRow("AMP", M8Song.hex2(instrument.amp.amp))
                ParamRow("LIMITER", instrument.amp.limiter.label)
                ParamRow("PAN", M8Song.hex2(instrument.amp.pan))
                ParamRow("DRY", M8Song.hex2(instrument.amp.dry))
            }
        }

        item { SectionHeader("SENDS") }
        item {
            ParamCard {
                ParamRow("CHORUS", M8Song.hex2(instrument.amp.chorusSend))
                ParamRow("DELAY", M8Song.hex2(instrument.amp.delaySend))
                ParamRow("REVERB", M8Song.hex2(instrument.amp.reverbSend))
            }
        }

        item { Spacer(Modifier.height(DawTheme.SpaceLg)) }
    }
}

@Composable
private fun SectionHeader(label: String) {
    Text(
        text = label,
        color = DawTheme.TextLabel,
        fontSize = DawTheme.FontLabel,
        fontWeight = FontWeight.Black,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier.padding(top = DawTheme.SpaceSm, bottom = DawTheme.SpaceXs),
    )
}

@Composable
private fun ParamCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(DawTheme.CornerSm))
            .background(DawTheme.BgCardHi)
            .border(1.dp, DawTheme.BorderDim, RoundedCornerShape(DawTheme.CornerSm))
            .padding(horizontal = DawTheme.SpaceMd, vertical = DawTheme.SpaceSm),
        verticalArrangement = Arrangement.spacedBy(DawTheme.SpaceXs),
        content = content,
    )
}

@Composable
private fun ParamRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = DawTheme.TextDim,
            fontSize = DawTheme.FontMono,
            fontFamily = FontFamily.Monospace,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = value,
            color = DawTheme.TextBright,
            fontSize = DawTheme.FontMono,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
        )
    }
}
