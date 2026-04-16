package com.m8droid.ui.daw

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import com.m8droid.emulator.M8Song

@Composable
fun DawPatternView(
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
                is DawDestination.PatternGrid -> PatternGridContent(viewModel, navState)
                is DawDestination.ChainDetail -> ChainDetailContent(viewModel, navState, dest)
                is DawDestination.PhraseDetail -> PhraseDetailContent(viewModel, navState, dest)
                else -> PatternGridContent(viewModel, navState)
            }
        }
    }
}

// ── Pattern Grid (top-level song view) ──────────────────────────────────────

@Composable
private fun PatternGridContent(viewModel: M8ViewModel, navState: DawNavState) {
    val song = viewModel.songData
    val playRow = viewModel.currentPlayRow
    val isPlaying = viewModel.isPlaying

    val lastUsed = (song.songGrid.indices.lastOrNull { row ->
        song.songGrid[row].any { it != M8Song.EMPTY }
    } ?: -1)
    val visibleCount = maxOf(16, lastUsed + 1)
    val rows = (0 until visibleCount).toList()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DawTheme.BgPanel)
            .padding(DawTheme.SpaceLg),
        verticalArrangement = Arrangement.spacedBy(DawTheme.SpaceMd),
    ) {
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

        // Column labels
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
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
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

        // Grid
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(DawTheme.CornerMd))
                .background(DawTheme.BgCard)
                .border(1.dp, DawTheme.BorderDim, RoundedCornerShape(DawTheme.CornerMd))
                .padding(DawTheme.SpaceXs),
        ) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(rows) { rowIdx ->
                    SongRowLine(
                        rowIdx = rowIdx,
                        cells = song.songGrid[rowIdx],
                        song = song,
                        isPlayRow = isPlaying && rowIdx == playRow,
                        onCellClick = { track, chainIdx ->
                            navState.push(DawDestination.ChainDetail(rowIdx, track, chainIdx))
                        },
                    )
                }
            }
        }

        SpectralMonitor(levels = viewModel.liveTrackLevelArray)
    }
}

@Composable
private fun SongRowLine(
    rowIdx: Int,
    cells: IntArray,
    song: M8Song,
    isPlayRow: Boolean,
    onCellClick: (track: Int, chainIdx: Int) -> Unit,
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
                    .clickable(enabled = !empty) { onCellClick(t, chainRef) }
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

// ── Chain Detail View ────────────────────────────────────────────────────────

@Composable
private fun ChainDetailContent(
    viewModel: M8ViewModel,
    navState: DawNavState,
    dest: DawDestination.ChainDetail,
) {
    val song = viewModel.songData
    val chain = song.chains[dest.chainIdx.coerceIn(0, 254)]

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DawTheme.BgPanel)
            .padding(DawTheme.SpaceLg),
        verticalArrangement = Arrangement.spacedBy(DawTheme.SpaceMd),
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "CHAIN ${M8Song.hex2(dest.chainIdx)}",
                color = DawTheme.AccentGreen,
                fontSize = DawTheme.FontHeading,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "T${dest.track + 1} ROW ${M8Song.hex2(dest.songRow)}",
                color = DawTheme.TextDim,
                fontSize = DawTheme.FontLabel,
                fontFamily = FontFamily.Monospace,
            )
        }

        // Column headers
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(DawTheme.CornerMd))
                .background(DawTheme.BgCardHi)
                .border(1.dp, DawTheme.BorderDim, RoundedCornerShape(DawTheme.CornerMd))
                .padding(horizontal = DawTheme.SpaceMd, vertical = DawTheme.SpaceXs),
        ) {
            Text("ROW", color = DawTheme.TextLabel, fontSize = DawTheme.FontLabel,
                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                modifier = Modifier.width(40.dp))
            Text("PHRASE", color = DawTheme.TextLabel, fontSize = DawTheme.FontLabel,
                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f))
            Text("TRANSPOSE", color = DawTheme.TextLabel, fontSize = DawTheme.FontLabel,
                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        }

        // Chain rows
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(DawTheme.CornerMd))
                .background(DawTheme.BgCard)
                .border(1.dp, DawTheme.BorderDim, RoundedCornerShape(DawTheme.CornerMd))
                .padding(DawTheme.SpaceXs),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            itemsIndexed(chain.rows.toList()) { idx, row ->
                val empty = row.phrase == M8Song.EMPTY
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(DawTheme.CornerSm))
                        .background(if (!empty) DawTheme.BgCardHi else Color.Transparent)
                        .clickable(enabled = !empty) {
                            navState.push(DawDestination.PhraseDetail(row.phrase, dest.chainIdx))
                        }
                        .padding(horizontal = DawTheme.SpaceMd, vertical = DawTheme.SpaceSm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = M8Song.hex2(idx),
                        color = DawTheme.TextDim,
                        fontSize = DawTheme.FontMono,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(40.dp),
                    )
                    Text(
                        text = if (empty) "--" else M8Song.hex2(row.phrase),
                        color = if (empty) DawTheme.TextDim else DawTheme.AccentGreen,
                        fontSize = DawTheme.FontMono,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )

                    // Editable transpose
                    if (!empty) {
                        EditableParamRow(
                            label = "",
                            value = row.transpose + 0x80,
                            maxValue = 0xFF,
                            displayFn = { v ->
                                val signed = v - 0x80
                                if (signed >= 0) "+${M8Song.hex2(signed)}" else "-${M8Song.hex2(-signed)}"
                            },
                            onValueChange = { row.transpose = it - 0x80 },
                        )
                    } else {
                        Text(
                            text = "--",
                            color = DawTheme.TextDim,
                            fontSize = DawTheme.FontMono,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        }
    }
}

// ── Phrase Detail View ───────────────────────────────────────────────────────

@Composable
private fun PhraseDetailContent(
    viewModel: M8ViewModel,
    navState: DawNavState,
    dest: DawDestination.PhraseDetail,
) {
    val song = viewModel.songData
    val phrase = song.phrases[dest.phraseIdx.coerceIn(0, 254)]
    var expandedStep by remember { mutableIntStateOf(-1) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DawTheme.BgPanel)
            .padding(DawTheme.SpaceLg),
        verticalArrangement = Arrangement.spacedBy(DawTheme.SpaceMd),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "PHRASE ${M8Song.hex2(dest.phraseIdx)}",
                color = DawTheme.AccentGreen,
                fontSize = DawTheme.FontHeading,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "CHAIN ${M8Song.hex2(dest.chainIdx)}",
                color = DawTheme.TextDim,
                fontSize = DawTheme.FontLabel,
                fontFamily = FontFamily.Monospace,
            )
        }

        // Column headers
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(DawTheme.CornerMd))
                .background(DawTheme.BgCardHi)
                .border(1.dp, DawTheme.BorderDim, RoundedCornerShape(DawTheme.CornerMd))
                .padding(horizontal = DawTheme.SpaceSm, vertical = DawTheme.SpaceXs),
        ) {
            val headers = listOf("ST" to 28.dp, "NOTE" to 40.dp, "IN" to 28.dp, "VOL" to 32.dp,
                "FX1" to 40.dp, "FX2" to 40.dp)
            headers.forEach { (label, width) ->
                Text(
                    text = label,
                    color = DawTheme.TextLabel,
                    fontSize = DawTheme.FontLabel,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(width),
                )
            }
        }

        // Steps
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(DawTheme.CornerMd))
                .background(DawTheme.BgCard)
                .border(1.dp, DawTheme.BorderDim, RoundedCornerShape(DawTheme.CornerMd))
                .padding(DawTheme.SpaceXs),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            itemsIndexed(phrase.steps.toList()) { idx, step ->
                val isExpanded = expandedStep == idx
                val empty = step.note == M8Song.EMPTY && step.instrument == M8Song.EMPTY
                val stepBg = when {
                    isExpanded -> DawTheme.BgCardHi
                    !empty -> DawTheme.BgCard
                    else -> Color.Transparent
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(DawTheme.CornerSm))
                        .background(stepBg)
                        .clickable { expandedStep = if (isExpanded) -1 else idx },
                ) {
                    // Compact step row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = DawTheme.SpaceSm, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = M8Song.hex2(idx),
                            color = DawTheme.TextDim,
                            fontSize = DawTheme.FontMono,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.width(28.dp),
                        )
                        Text(
                            text = when {
                                step.note == M8Song.EMPTY -> "---"
                                step.note == 0 -> "OFF"
                                else -> M8Song.noteName(step.note)
                            },
                            color = when {
                                step.note == M8Song.EMPTY -> DawTheme.TextDim
                                step.note == 0 -> DawTheme.AccentRed
                                else -> DawTheme.TextBright
                            },
                            fontSize = DawTheme.FontMono,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(40.dp),
                        )
                        Text(
                            text = if (step.instrument == M8Song.EMPTY) "--" else M8Song.hex2(step.instrument),
                            color = if (step.instrument == M8Song.EMPTY) DawTheme.TextDim else DawTheme.AccentCyan,
                            fontSize = DawTheme.FontMono,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.width(28.dp),
                        )
                        Text(
                            text = if (step.volume == M8Song.EMPTY) "--" else M8Song.hex2(step.volume),
                            color = if (step.volume == M8Song.EMPTY) DawTheme.TextDim else DawTheme.AccentYellow,
                            fontSize = DawTheme.FontMono,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.width(32.dp),
                        )
                        // FX1
                        Text(
                            text = if (step.fx1Cmd == 0 && step.fx1Val == 0) "----"
                            else "${M8Song.hex2(step.fx1Cmd)}${M8Song.hex2(step.fx1Val)}",
                            color = if (step.fx1Cmd == 0) DawTheme.TextDim else DawTheme.AccentMagenta,
                            fontSize = DawTheme.FontMono,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.width(40.dp),
                        )
                        // FX2
                        Text(
                            text = if (step.fx2Cmd == 0 && step.fx2Val == 0) "----"
                            else "${M8Song.hex2(step.fx2Cmd)}${M8Song.hex2(step.fx2Val)}",
                            color = if (step.fx2Cmd == 0) DawTheme.TextDim else DawTheme.AccentMagenta,
                            fontSize = DawTheme.FontMono,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.width(40.dp),
                        )
                    }

                    // Expanded edit section
                    if (isExpanded) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(DawTheme.BgCardHi)
                                .padding(DawTheme.SpaceMd),
                            verticalArrangement = Arrangement.spacedBy(DawTheme.SpaceXs),
                        ) {
                            Text(
                                text = "STEP ${M8Song.hex2(idx)} EDIT",
                                color = DawTheme.TextLabel,
                                fontSize = DawTheme.FontLabel,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                            )
                            EditableParamRow(
                                label = "NOTE",
                                value = step.note,
                                maxValue = 127,
                                displayFn = { v ->
                                    when (v) {
                                        M8Song.EMPTY -> "---"
                                        0 -> "OFF"
                                        else -> M8Song.noteName(v)
                                    }
                                },
                                onValueChange = { step.note = it },
                            )
                            EditableParamRow(
                                label = "INST",
                                value = if (step.instrument == M8Song.EMPTY) 0 else step.instrument,
                                maxValue = 7,
                                onValueChange = { step.instrument = it },
                            )
                            EditableParamRow(
                                label = "VOL",
                                value = if (step.volume == M8Song.EMPTY) 0xFF else step.volume,
                                onValueChange = { step.volume = it },
                            )
                            EditableParamRow(
                                label = "FX1 CMD",
                                value = step.fx1Cmd,
                                onValueChange = { step.fx1Cmd = it },
                            )
                            EditableParamRow(
                                label = "FX1 VAL",
                                value = step.fx1Val,
                                onValueChange = { step.fx1Val = it },
                            )
                            EditableParamRow(
                                label = "FX2 CMD",
                                value = step.fx2Cmd,
                                onValueChange = { step.fx2Cmd = it },
                            )
                            EditableParamRow(
                                label = "FX2 VAL",
                                value = step.fx2Val,
                                onValueChange = { step.fx2Val = it },
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Helpers ──────────────────────────────────────────────────────────────────

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
