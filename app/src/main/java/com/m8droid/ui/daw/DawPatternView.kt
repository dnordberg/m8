package com.m8droid.ui.daw

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.m8droid.M8ViewModel
import com.m8droid.emulator.M8Song

/**
 * DAW-style sequence editor view.
 * Shows the song grid (rows × 8 tracks) as a scrollable list of cells,
 * with the currently-playing row highlighted and a small per-track
 * level meter at the bottom.
 */
@Composable
fun DawPatternView(viewModel: M8ViewModel, modifier: Modifier = Modifier) {
    // Recompose at ~30fps so play-row highlight and meters stay live.
    val tick by viewModel.displayTick.collectAsState()
    @Suppress("UNUSED_EXPRESSION") tick

    val song = viewModel.songData
    val playRow = viewModel.currentPlayRow
    val isPlaying = viewModel.isPlaying

    // Collect visible rows: all rows up to the last non-empty row, min 16.
    val lastUsed = (song.songGrid.indices.lastOrNull { row ->
        song.songGrid[row].any { it != M8Song.EMPTY }
    } ?: -1)
    val visibleCount = maxOf(16, lastUsed + 1)
    val rows = (0 until visibleCount).toList()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DawTheme.BgPanel)
            .padding(DawTheme.SpaceLg),
        verticalArrangement = Arrangement.spacedBy(DawTheme.SpaceMd),
    ) {
        // ── Header ────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "SEQUENCE_EDITOR",
                color = DawTheme.TextLabel,
                fontSize = DawTheme.FontLabel,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "PATTERN ${M8Song.hex2(playRow.coerceAtLeast(0))}",
                color = DawTheme.AccentGreen,
                fontSize = DawTheme.FontLabel,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.width(DawTheme.SpaceMd))
            Text(
                text = if (isPlaying) "> PLAY" else "  STOP",
                color = if (isPlaying) DawTheme.AccentGreen else DawTheme.TextDim,
                fontSize = DawTheme.FontLabel,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
        }

        // ── Column labels ─────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(DawTheme.CornerMd))
                .background(DawTheme.BgCardHi)
                .border(1.dp, DawTheme.BorderDim, RoundedCornerShape(DawTheme.CornerMd))
                .padding(horizontal = DawTheme.SpaceSm, vertical = DawTheme.SpaceXs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "POS",
                color = DawTheme.TextLabel,
                fontSize = DawTheme.FontLabel,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(36.dp),
            )
            for (t in 0 until 8) {
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "T${t + 1}",
                        color = DawTheme.TextLabel,
                        fontSize = DawTheme.FontLabel,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        // ── Grid (scrollable) ─────────────────────────────────────────
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(DawTheme.CornerMd))
                .background(DawTheme.BgCard)
                .border(1.dp, DawTheme.BorderDim, RoundedCornerShape(DawTheme.CornerMd))
                .padding(DawTheme.SpaceXs),
        ) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(rows) { rowIdx ->
                    SongRowLine(
                        rowIdx = rowIdx,
                        cells = song.songGrid[rowIdx],
                        song = song,
                        isPlayRow = isPlaying && rowIdx == playRow,
                    )
                }
            }
        }

        // ── Spectral monitor ──────────────────────────────────────────
        SpectralMonitor(levels = viewModel.liveTrackLevelArray)
    }
}

@Composable
private fun SongRowLine(
    rowIdx: Int,
    cells: IntArray,
    song: M8Song,
    isPlayRow: Boolean,
) {
    val bg = if (isPlayRow) DawTheme.AccentGreenDim else Color.Transparent
    val posColor = when {
        isPlayRow -> Color.Black
        rowIdx % 4 == 0 -> DawTheme.AccentCyan
        else -> DawTheme.TextDim
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(DawTheme.CornerSm))
            .background(bg)
            .padding(horizontal = DawTheme.SpaceSm, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = M8Song.hex2(rowIdx),
            color = posColor,
            fontSize = DawTheme.FontMono,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(36.dp),
        )
        for (t in 0 until 8) {
            val chainRef = cells[t]
            val empty = chainRef == M8Song.EMPTY
            val firstNote = if (!empty) firstNoteForChain(song, chainRef) else null
            val cellBg = when {
                isPlayRow && !empty -> Color.Black.copy(alpha = 0.35f)
                !empty -> DawTheme.BgCardHi
                else -> Color.Transparent
            }
            val fg = when {
                isPlayRow && !empty -> DawTheme.AccentYellow
                empty -> DawTheme.TextDim
                else -> DawTheme.AccentGreen
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 2.dp)
                    .clip(RoundedCornerShape(DawTheme.CornerSm))
                    .background(cellBg)
                    .clickable(enabled = !empty) {
                        // TODO: open chain/phrase editor for this cell
                    }
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (empty) "--" else M8Song.hex2(chainRef),
                        color = fg,
                        fontSize = DawTheme.FontMono,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                    )
                    if (firstNote != null) {
                        Text(
                            text = firstNote,
                            color = if (isPlayRow) DawTheme.AccentYellow else DawTheme.TextNormal,
                            fontSize = DawTheme.FontLabel,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        }
    }
}

/** Peek the first phrase's first non-empty note in a chain for a compact preview. */
private fun firstNoteForChain(song: M8Song, chainRef: Int): String? {
    if (chainRef < 0 || chainRef >= song.chains.size) return null
    val chain = song.chains[chainRef]
    for (cr in chain.rows) {
        val p = cr.phrase
        if (p == M8Song.EMPTY || p < 0 || p >= song.phrases.size) continue
        val phrase = song.phrases[p]
        for (step in phrase.steps) {
            if (step.note != M8Song.EMPTY) return M8Song.noteName(step.note)
        }
    }
    return null
}

@Composable
private fun SpectralMonitor(levels: DoubleArray?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(DawTheme.CornerMd))
            .background(DawTheme.BgCard)
            .border(1.dp, DawTheme.BorderDim, RoundedCornerShape(DawTheme.CornerMd))
            .padding(DawTheme.SpaceSm),
        verticalArrangement = Arrangement.spacedBy(DawTheme.SpaceXs),
    ) {
        Text(
            text = "SPECTRAL_MON",
            color = DawTheme.TextLabel,
            fontSize = DawTheme.FontLabel,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            horizontalArrangement = Arrangement.spacedBy(DawTheme.SpaceXs),
            verticalAlignment = Alignment.Bottom,
        ) {
            for (t in 0 until 8) {
                val raw = levels?.getOrNull(t) ?: 0.0
                val lvl = raw.coerceIn(0.0, 1.0).toFloat()
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(DawTheme.CornerSm))
                        .background(DawTheme.BgCardHi),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    val barColor = when {
                        lvl > 0.85f -> DawTheme.AccentRed
                        lvl > 0.6f -> DawTheme.AccentYellow
                        else -> DawTheme.AccentGreen
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(fraction = lvl.coerceAtLeast(0.02f))
                            .clip(RoundedCornerShape(DawTheme.CornerSm))
                            .background(barColor),
                    )
                }
            }
        }
    }
}
