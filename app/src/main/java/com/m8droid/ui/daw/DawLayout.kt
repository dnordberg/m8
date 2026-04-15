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
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(DawTheme.CornerSm))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = DawTheme.SpaceMd, vertical = DawTheme.SpaceMd),
    ) {
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
        TransportButton("\u23EA", DawTheme.AccentGreen) {
            viewModel.adjustTempo(-1)
        }
        TransportButton(
            glyph = if (viewModel.isPlaying) "\u23F8" else "\u25B6",
            color = DawTheme.AccentGreen,
            filled = true,
        ) { viewModel.togglePlayback() }
        TransportButton("\u23FA", DawTheme.AccentMagenta) {
            // Record placeholder — toggles edit mode on the underlying emulator.
            viewModel.setTouchKeys(com.m8droid.protocol.M8Commands.KEY_EDIT)
            viewModel.setTouchKeys(0)
        }
        TransportButton("\u25A0", DawTheme.AccentGreen) { viewModel.stopPlayback() }
        TransportButton("\u23E9", DawTheme.AccentGreen) { viewModel.adjustTempo(1) }
    }
}

@Composable
private fun TransportButton(
    glyph: String,
    color: Color,
    filled: Boolean = false,
    onClick: () -> Unit,
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
        Text(
            text = glyph,
            color = fg,
            fontSize = DawTheme.FontTitle,
            fontWeight = FontWeight.Bold,
        )
    }
}
