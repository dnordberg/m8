package com.m8droid.ui.daw

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import com.m8droid.protocol.M8Commands
import kotlinx.coroutines.launch

@Composable
fun DawLayout(
    viewModel: M8ViewModel,
    onToggleMode: () -> Unit,
    onLoad: () -> Unit,
    onSettings: () -> Unit,
    onHelp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val modules = DawModule.values()
    val pagerState = rememberPagerState(pageCount = { modules.size })
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DawTheme.BgRoot),
    ) {
        DawHeaderBar(
            subtitle = modules[pagerState.currentPage].label,
            isDawMode = true,
            onToggleMode = onToggleMode,
            onLoad = onLoad,
            onSettings = onSettings,
            onHelp = onHelp,
        )

        // Module tabs — compact, swipe-synced
        ModuleTabRow(
            modules = modules,
            selectedIndex = pagerState.currentPage,
            onSelect = { idx -> scope.launch { pagerState.animateScrollToPage(idx) } },
        )

        // Swipeable content pages
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) { page ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(DawTheme.BgPanel),
            ) {
                when (modules[page]) {
                    DawModule.PATTERN -> {
                        val nav = rememberDawNavState(DawDestination.PatternGrid)
                        DawPatternView(viewModel, nav, Modifier.fillMaxSize())
                    }
                    DawModule.INSTRUMENT -> {
                        val nav = rememberDawNavState(DawDestination.InstrumentList)
                        DawInstrumentView(viewModel, nav, Modifier.fillMaxSize())
                    }
                    DawModule.MIXER -> DawMixerView(viewModel, Modifier.fillMaxSize())
                    DawModule.SAMPLES -> {
                        val nav = rememberDawNavState(DawDestination.SampleBrowser)
                        DawSamplesView(viewModel, nav, Modifier.fillMaxSize())
                    }
                    DawModule.SYSTEM -> {
                        val nav = rememberDawNavState(DawDestination.SystemMain)
                        DawSystemView(viewModel, nav, Modifier.fillMaxSize())
                    }
                }
            }
        }

        DawTransportBar(viewModel)
    }
}

@Composable
private fun ModuleTabRow(
    modules: Array<DawModule>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DawTheme.BgPanel)
            .padding(horizontal = DawTheme.SpaceSm, vertical = DawTheme.SpaceXs),
        horizontalArrangement = Arrangement.spacedBy(DawTheme.SpaceXs),
    ) {
        modules.forEachIndexed { index, module ->
            val selected = index == selectedIndex
            val bg = if (selected) DawTheme.AccentGreen else Color.Transparent
            val fg = if (selected) Color.Black else DawTheme.TextDim
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(DawTheme.CornerSm))
                    .background(bg)
                    .clickable { onSelect(index) }
                    .padding(vertical = DawTheme.SpaceSm),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    when (module) {
                        DawModule.PATTERN -> DawPatternIcon(tint = fg, size = 14.dp)
                        DawModule.INSTRUMENT -> DawInstrumentIcon(tint = fg, size = 14.dp)
                        DawModule.MIXER -> DawMixerIcon(tint = fg, size = 14.dp)
                        DawModule.SAMPLES -> DawSamplesIcon(tint = fg, size = 14.dp)
                        DawModule.SYSTEM -> DawSystemIcon(tint = fg, size = 14.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun DawTransportBar(viewModel: M8ViewModel) {
    val tick by viewModel.displayTick.collectAsState()
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
            viewModel.setTouchKeys(M8Commands.KEY_EDIT)
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
