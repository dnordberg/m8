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
fun DawSystemView(viewModel: M8ViewModel, modifier: Modifier = Modifier) {
    val tick by viewModel.displayTick.collectAsState()
    @Suppress("UNUSED_EXPRESSION") tick

    val song = viewModel.songData
    val scale = song.scales[song.activeScale]

    Column(
        modifier = modifier
            .background(DawTheme.BgPanel)
            .verticalScroll(rememberScrollState())
            .padding(DawTheme.SpaceLg),
        verticalArrangement = Arrangement.spacedBy(DawTheme.SpaceMd),
    ) {
        SectionCard(title = "SONG") {
            KeyValueRow("NAME", song.name)
            Spacer(Modifier.height(DawTheme.SpaceSm))
            TempoControl(
                tempo = song.tempo,
                onAdjust = { viewModel.adjustTempo(it) },
            )
            Spacer(Modifier.height(DawTheme.SpaceSm))
            KeyValueRow("TRANSPOSE", M8Song.hex2(song.transpose))
            KeyValueRow("QUANTIZE", M8Song.hex2(song.quantize))
        }

        SectionCard(title = "SCALE") {
            KeyValueRow("ACTIVE", scale.name)
            KeyValueRow("KEY", M8Song.hex2(scale.key))
            KeyValueRow("SLOT", M8Song.hex2(song.activeScale))
        }

        SectionCard(title = "EFFECTS") {
            Text(
                text = "REVERB",
                color = DawTheme.AccentCyan,
                fontSize = DawTheme.FontLabel,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
            KeyValueRow("SIZE", M8Song.hex2(song.reverb.size))
            KeyValueRow("DAMP", M8Song.hex2(song.reverb.damping))
            KeyValueRow("HP", M8Song.hex2(song.reverb.filterHP))
            KeyValueRow("LP", M8Song.hex2(song.reverb.filterLP))
            Spacer(Modifier.height(DawTheme.SpaceSm))
            Text(
                text = "DELAY",
                color = DawTheme.AccentCyan,
                fontSize = DawTheme.FontLabel,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
            KeyValueRow("TIME L", M8Song.hex2(song.delay.timeL))
            KeyValueRow("TIME R", M8Song.hex2(song.delay.timeR))
            KeyValueRow("FBK", M8Song.hex2(song.delay.feedback))
            KeyValueRow("WIDTH", M8Song.hex2(song.delay.width))
            Spacer(Modifier.height(DawTheme.SpaceSm))
            Text(
                text = "CHORUS",
                color = DawTheme.AccentCyan,
                fontSize = DawTheme.FontLabel,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
            KeyValueRow("DEPTH", M8Song.hex2(song.chorus.modDepth))
            KeyValueRow("FREQ", M8Song.hex2(song.chorus.modFreq))
            KeyValueRow("WIDTH", M8Song.hex2(song.chorus.width))
        }

        TutorialButton(onClick = { viewModel.toggleTutorial() })
        Spacer(Modifier.height(DawTheme.SpaceLg))
    }
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
private fun TempoControl(tempo: Int, onAdjust: (Int) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "TEMPO",
            color = DawTheme.TextDim,
            fontSize = DawTheme.FontLabel,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
        )
        Spacer(Modifier.height(DawTheme.SpaceXs))
        Text(
            text = "$tempo BPM",
            color = DawTheme.AccentGreen,
            fontSize = DawTheme.FontTitle,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace,
        )
        Spacer(Modifier.height(DawTheme.SpaceSm))
        Row(
            horizontalArrangement = Arrangement.spacedBy(DawTheme.SpaceSm),
        ) {
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
        Text(
            text = label,
            color = DawTheme.AccentGreen,
            fontSize = DawTheme.FontHeading,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace,
        )
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
        Text(
            text = "TUTORIAL",
            color = DawTheme.AccentMagenta,
            fontSize = DawTheme.FontHeading,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
        )
    }
}
