package com.m8droid.ui.daw

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
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

@Composable
fun DawSamplesView(viewModel: M8ViewModel, modifier: Modifier = Modifier) {
    val tick by viewModel.displayTick.collectAsState()
    @Suppress("UNUSED_EXPRESSION") tick

    val instruments = viewModel.instrumentList.toList().take(8)

    LazyColumn(
        modifier = modifier
            .background(DawTheme.BgPanel)
            .padding(DawTheme.SpaceLg),
        verticalArrangement = Arrangement.spacedBy(DawTheme.SpaceSm),
    ) {
        item {
            Text(
                text = "SAMPLE_BROWSER",
                color = DawTheme.TextLabel,
                fontSize = DawTheme.FontHeading,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
        }
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(DawTheme.CornerMd))
                    .background(DawTheme.BgCard)
                    .border(1.dp, DawTheme.BorderDim, RoundedCornerShape(DawTheme.CornerMd))
                    .padding(DawTheme.SpaceMd),
            ) {
                Text(
                    text = "Sample browsing uses the classic load dialog.",
                    color = DawTheme.TextBright,
                    fontSize = DawTheme.FontBody,
                    fontFamily = FontFamily.Monospace,
                )
                Spacer(Modifier.height(DawTheme.SpaceXs))
                Text(
                    text = "Switch to M8 Classic mode to browse remote sample packs.",
                    color = DawTheme.TextDim,
                    fontSize = DawTheme.FontBody,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
        item {
            Spacer(Modifier.height(DawTheme.SpaceSm))
            Text(
                text = "LOADED_INSTRUMENTS",
                color = DawTheme.TextLabel,
                fontSize = DawTheme.FontLabel,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
        }
        itemsIndexed(instruments) { index, inst ->
            InstrumentRow(index = index, name = inst.name, type = inst.type.name)
        }
    }
}

@Composable
private fun InstrumentRow(index: Int, name: String, type: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(DawTheme.CornerMd))
            .background(DawTheme.BgCard)
            .border(1.dp, DawTheme.BorderDim, RoundedCornerShape(DawTheme.CornerMd))
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
        Text(
            text = name,
            color = DawTheme.TextBright,
            fontSize = DawTheme.FontBody,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = type,
            color = DawTheme.AccentCyan,
            fontSize = DawTheme.FontLabel,
            fontFamily = FontFamily.Monospace,
        )
    }
}
