package com.m8droid.ui.daw

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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

/**
 * Top-level DAW layout.
 * Header (title + mode toggle) / module sidebar (left) / content (center) /
 * transport bar (bottom).
 *
 * Module content composables are delegated: each module lives in its own file
 * and is wired by [moduleContent] so agents could iterate in parallel.
 */
@Composable
fun DawLayout(
    viewModel: M8ViewModel,
    onToggleMode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selected by remember { mutableStateOf(DawModule.PATTERN) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DawTheme.BgRoot),
    ) {
        DawHeader(onToggleMode = onToggleMode)

        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            DawSidebar(
                selected = selected,
                onSelect = { selected = it },
                modifier = Modifier
                    .width(150.dp)
                    .fillMaxHeight(),
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(DawTheme.BgPanel)
                    .border(1.dp, DawTheme.BorderDim),
            ) {
                when (selected) {
                    DawModule.PATTERN -> DawPatternView(viewModel, Modifier.fillMaxSize())
                    DawModule.INSTRUMENT -> DawInstrumentView(viewModel, Modifier.fillMaxSize())
                    DawModule.MIXER -> DawMixerView(viewModel, Modifier.fillMaxSize())
                    DawModule.SAMPLES -> DawSamplesView(viewModel, Modifier.fillMaxSize())
                    DawModule.SYSTEM -> DawSystemView(viewModel, Modifier.fillMaxSize())
                }
            }
        }

        DawTransportBar(viewModel)
    }
}

@Composable
private fun DawHeader(onToggleMode: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DawTheme.BgPanel)
            .border(1.dp, DawTheme.BorderDim)
            .padding(horizontal = DawTheme.SpaceMd, vertical = DawTheme.SpaceSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = ">_",
            color = DawTheme.AccentGreen,
            fontSize = DawTheme.FontTitle,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace,
        )
        Spacer(Modifier.width(DawTheme.SpaceSm))
        Text(
            text = "TRACKER_OS_V1",
            color = DawTheme.AccentGreen,
            fontSize = DawTheme.FontHeading,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
        )
        Spacer(Modifier.weight(1f))
        // Mode toggle chip — swap back to classic M8 view
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(DawTheme.CornerMd))
                .background(DawTheme.BgCardHi)
                .border(1.dp, DawTheme.AccentMagenta, RoundedCornerShape(DawTheme.CornerMd))
                .clickable(onClick = onToggleMode)
                .padding(horizontal = DawTheme.SpaceMd, vertical = DawTheme.SpaceXs),
        ) {
            Text(
                text = "M8 CLASSIC",
                color = DawTheme.AccentMagenta,
                fontSize = DawTheme.FontLabel,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
private fun DawSidebar(
    selected: DawModule,
    onSelect: (DawModule) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(DawTheme.BgPanel)
            .border(1.dp, DawTheme.BorderDim)
            .padding(DawTheme.SpaceSm),
        verticalArrangement = Arrangement.spacedBy(DawTheme.SpaceXs),
    ) {
        Text(
            text = "MODULE_SELECT",
            color = DawTheme.TextLabel,
            fontSize = DawTheme.FontLabel,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(bottom = DawTheme.SpaceSm, start = DawTheme.SpaceXs),
        )
        DawModule.values().forEach { module ->
            SidebarItem(
                module = module,
                selected = module == selected,
                onClick = { onSelect(module) },
            )
        }
    }
}

@Composable
private fun SidebarItem(module: DawModule, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) DawTheme.AccentGreen else DawTheme.BgCard
    val fg = if (selected) Color.Black else DawTheme.TextNormal
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(DawTheme.CornerSm))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = DawTheme.SpaceMd, vertical = DawTheme.SpaceMd),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (module) {
            DawModule.PATTERN -> DawPatternIcon(tint = fg, size = 18.dp)
            DawModule.INSTRUMENT -> DawInstrumentIcon(tint = fg, size = 18.dp)
            DawModule.MIXER -> DawMixerIcon(tint = fg, size = 18.dp)
            DawModule.SAMPLES -> DawSamplesIcon(tint = fg, size = 18.dp)
            DawModule.SYSTEM -> DawSystemIcon(tint = fg, size = 18.dp)
        }
        Spacer(Modifier.width(DawTheme.SpaceMd))
        Text(
            text = module.label,
            color = fg,
            fontSize = DawTheme.FontBody,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun DawTransportBar(viewModel: M8ViewModel) {
    val tick by viewModel.displayTick.collectAsState()
    // Reading tick keeps this composable recomposing ~30fps so isPlaying stays fresh.
    @Suppress("UNUSED_EXPRESSION") tick

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DawTheme.BgPanel)
            .border(1.dp, DawTheme.BorderDim)
            .padding(horizontal = DawTheme.SpaceLg, vertical = DawTheme.SpaceMd),
        horizontalArrangement = Arrangement.spacedBy(DawTheme.SpaceMd, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TransportButton(color = DawTheme.AccentGreen, onClick = { viewModel.adjustTempo(-1) }) { c ->
            DawRewindIcon(tint = c, size = 22.dp)
        }
        TransportButton(
            color = DawTheme.AccentGreen,
            filled = true,
            onClick = { viewModel.togglePlayback() },
        ) { c ->
            if (viewModel.isPlaying) DawPauseIcon(tint = c, size = 22.dp)
            else DawPlayIcon(tint = c, size = 22.dp)
        }
        TransportButton(color = DawTheme.AccentMagenta, onClick = {
            // Record placeholder — toggles edit mode on the underlying emulator.
            viewModel.setTouchKeys(com.m8droid.protocol.M8Commands.KEY_EDIT)
            viewModel.setTouchKeys(0)
        }) { c -> DawRecordIcon(tint = c, size = 22.dp) }
        TransportButton(color = DawTheme.AccentGreen, onClick = { viewModel.stopPlayback() }) { c ->
            DawStopIcon(tint = c, size = 22.dp)
        }
        TransportButton(color = DawTheme.AccentGreen, onClick = { viewModel.adjustTempo(1) }) { c ->
            DawFastForwardIcon(tint = c, size = 22.dp)
        }
    }
}

@Composable
private fun TransportButton(
    color: Color,
    filled: Boolean = false,
    onClick: () -> Unit,
    content: @Composable (Color) -> Unit,
) {
    val bg = if (filled) color else DawTheme.BgCard
    val fg = if (filled) Color.Black else color
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(DawTheme.CornerMd))
            .background(bg)
            .border(1.dp, color, RoundedCornerShape(DawTheme.CornerMd))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content(fg)
    }
}
