package com.m8droid.ui.daw

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import com.m8droid.emulator.M8Song

@Composable
fun DawMixerView(viewModel: M8ViewModel, modifier: Modifier = Modifier) {
    val tick by viewModel.displayTick.collectAsState()
    @Suppress("UNUSED_EXPRESSION") tick

    val mixer = viewModel.songData.mixer
    val liveTracks = viewModel.liveTrackLevelArray
    val (liveL, liveR) = viewModel.liveMasterLevels
    val masterLevel = ((liveL + liveR) / 2.0).toFloat().coerceIn(0f, 1f)

    Row(
        modifier = modifier
            .background(DawTheme.BgPanel)
            .horizontalScroll(rememberScrollState())
            .padding(DawTheme.SpaceLg),
        horizontalArrangement = Arrangement.spacedBy(DawTheme.SpaceSm),
        verticalAlignment = Alignment.Top,
    ) {
        for (i in 0 until 8) {
            val level = liveTracks?.getOrNull(i)?.toFloat()?.coerceIn(0f, 1f) ?: 0f
            TrackStrip(
                label = "TR${i + 1}",
                level = level,
                volume = mixer.trackVolumes[i],
                pan = mixer.trackPans[i],
                onVolumeChange = { v -> viewModel.setTrackVolume(i, (v * 255f).toInt().coerceIn(0, 255)) },
                onPanChange = { p -> viewModel.setTrackPan(i, (p * 255f).toInt().coerceIn(0, 255)) },
                accent = DawTheme.AccentGreen,
            )
        }

        MasterStrip(
            label = "MASTER",
            level = masterLevel,
            volume = mixer.masterVolume,
            onVolumeChange = { v -> viewModel.setMasterVolume((v * 255f).toInt().coerceIn(0, 255)) },
        )
    }
}

@Composable
private fun TrackStrip(
    label: String,
    level: Float,
    volume: Int,
    pan: Int,
    onVolumeChange: (Float) -> Unit,
    onPanChange: (Float) -> Unit,
    accent: Color,
) {
    StripCard(width = 90.dp) {
        StripLabel(label)
        Spacer(Modifier.height(DawTheme.SpaceSm))
        VuBar(level = level)
        Spacer(Modifier.height(DawTheme.SpaceSm))
        StripCaption("VOL")
        NeonSlider(
            value = (volume / 255f).coerceIn(0f, 1f),
            onValueChange = onVolumeChange,
        )
        HexValue(M8Song.hex2(volume))
        Spacer(Modifier.height(DawTheme.SpaceXs))
        StripCaption("PAN")
        NeonSlider(
            value = (pan / 255f).coerceIn(0f, 1f),
            onValueChange = onPanChange,
        )
        HexValue(M8Song.hex2(pan))
    }
}

@Composable
private fun MasterStrip(
    label: String,
    level: Float,
    volume: Int,
    onVolumeChange: (Float) -> Unit,
) {
    StripCard(width = 100.dp, borderColor = DawTheme.AccentMagenta) {
        Text(
            text = label,
            color = DawTheme.AccentMagenta,
            fontSize = DawTheme.FontLabel,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
        )
        Spacer(Modifier.height(DawTheme.SpaceSm))
        VuBar(level = level)
        Spacer(Modifier.height(DawTheme.SpaceSm))
        StripCaption("MSTR")
        NeonSlider(
            value = (volume / 255f).coerceIn(0f, 1f),
            onValueChange = onVolumeChange,
        )
        HexValue(M8Song.hex2(volume))
    }
}

@Composable
private fun StripCard(
    width: androidx.compose.ui.unit.Dp,
    borderColor: Color = DawTheme.BorderDim,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .width(width)
            .clip(RoundedCornerShape(DawTheme.CornerMd))
            .background(DawTheme.BgCard)
            .border(1.dp, borderColor, RoundedCornerShape(DawTheme.CornerMd))
            .padding(horizontal = DawTheme.SpaceSm, vertical = DawTheme.SpaceMd),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

@Composable
private fun StripLabel(text: String) {
    Text(
        text = text,
        color = DawTheme.TextLabel,
        fontSize = DawTheme.FontLabel,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
    )
}

@Composable
private fun StripCaption(text: String) {
    Text(
        text = text,
        color = DawTheme.TextDim,
        fontSize = DawTheme.FontLabel,
        fontFamily = FontFamily.Monospace,
    )
}

@Composable
private fun HexValue(text: String) {
    Text(
        text = text,
        color = DawTheme.AccentGreen,
        fontSize = DawTheme.FontMono,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun VuBar(level: Float) {
    val clamped = level.coerceIn(0f, 1f)
    val fillColor = when {
        clamped > 0.85f -> DawTheme.AccentRed
        clamped > 0.6f -> DawTheme.AccentYellow
        else -> DawTheme.AccentGreen
    }
    Box(
        modifier = Modifier
            .width(18.dp)
            .height(80.dp)
            .clip(RoundedCornerShape(DawTheme.CornerSm))
            .background(DawTheme.BgCardHi)
            .border(1.dp, DawTheme.BorderDim, RoundedCornerShape(DawTheme.CornerSm)),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(clamped)
                .background(fillColor),
        )
    }
}

@Composable
private fun NeonSlider(value: Float, onValueChange: (Float) -> Unit) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp),
        colors = SliderDefaults.colors(
            thumbColor = DawTheme.AccentGreen,
            activeTrackColor = DawTheme.AccentGreen,
            inactiveTrackColor = DawTheme.BgCardHi,
        ),
    )
}
