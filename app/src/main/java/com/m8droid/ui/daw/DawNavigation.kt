package com.m8droid.ui.daw

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.m8droid.emulator.M8Song
import kotlinx.coroutines.delay

// ── Destinations ──────────────────────────────────────────────────────────────

sealed class DawDestination {
    // Pattern module destinations
    object PatternGrid : DawDestination()
    data class ChainDetail(val songRow: Int, val track: Int, val chainIdx: Int) : DawDestination()
    data class PhraseDetail(val phraseIdx: Int, val chainIdx: Int) : DawDestination()

    // Instrument module destinations
    object InstrumentList : DawDestination()
    data class InstrumentDetail(val slot: Int) : DawDestination()

    // Samples module destinations
    object SampleBrowser : DawDestination()
    data class SampleDetail(val slot: Int) : DawDestination()

    // System module destinations
    object SystemMain : DawDestination()
    data class EffectDetail(val effectType: String) : DawDestination()  // "REVERB", "DELAY", "CHORUS"
    object MidiConfig : DawDestination()
}

// ── Navigation state ──────────────────────────────────────────────────────────

class DawNavState(initial: DawDestination) {
    private val _stack: SnapshotStateList<DawDestination> = mutableStateListOf(initial)

    val current: DawDestination
        get() = _stack.lastOrNull() ?: DawDestination.PatternGrid

    val canGoBack: Boolean
        get() = _stack.size > 1

    val depth: Int
        get() = _stack.size

    val stack: List<DawDestination>
        get() = _stack.toList()

    fun push(dest: DawDestination) {
        _stack.add(dest)
    }

    fun pop(): Boolean {
        if (_stack.size > 1) {
            _stack.removeLast()
            return true
        }
        return false
    }

    fun reset(root: DawDestination) {
        _stack.clear()
        _stack.add(root)
    }

    fun popToRoot() {
        if (_stack.size > 1) {
            val root = _stack.first()
            _stack.clear()
            _stack.add(root)
        }
    }

    fun popTo(index: Int) {
        if (index in 0 until _stack.size) {
            while (_stack.size > index + 1) {
                _stack.removeLast()
            }
        }
    }
}

@Composable
fun rememberDawNavState(initial: DawDestination = DawDestination.PatternGrid): DawNavState {
    return remember { DawNavState(initial) }
}

// ── Breadcrumb label helper ───────────────────────────────────────────────────

private fun destinationLabel(dest: DawDestination): String = when (dest) {
    is DawDestination.PatternGrid -> "PATTERN"
    is DawDestination.ChainDetail -> "CHAIN ${M8Song.hex2(dest.chainIdx)}"
    is DawDestination.PhraseDetail -> "PHRASE ${M8Song.hex2(dest.phraseIdx)}"
    is DawDestination.InstrumentList -> "INSTRUMENTS"
    is DawDestination.InstrumentDetail -> "INST ${M8Song.hex2(dest.slot)}"
    is DawDestination.SampleBrowser -> "SAMPLES"
    is DawDestination.SampleDetail -> "SAMPLE ${M8Song.hex2(dest.slot)}"
    is DawDestination.SystemMain -> "SYSTEM"
    is DawDestination.EffectDetail -> dest.effectType
    is DawDestination.MidiConfig -> "MIDI"
}

// ── Breadcrumb ────────────────────────────────────────────────────────────────

@Composable
fun DawBreadcrumb(navState: DawNavState, modifier: Modifier = Modifier) {
    if (!navState.canGoBack) return

    val stack = navState.stack

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = DawTheme.SpaceLg, vertical = DawTheme.SpaceXs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        stack.forEachIndexed { index, dest ->
            val isLast = index == stack.lastIndex
            val color = if (isLast) DawTheme.AccentGreen else DawTheme.TextDim

            Text(
                text = destinationLabel(dest),
                color = color,
                fontSize = DawTheme.FontLabel,
                fontFamily = FontFamily.Monospace,
                fontWeight = if (isLast) FontWeight.Bold else FontWeight.Normal,
                modifier = if (!isLast) {
                    Modifier.clickable { navState.popTo(index) }
                } else {
                    Modifier
                },
            )

            if (!isLast) {
                Text(
                    text = " > ",
                    color = DawTheme.TextDim,
                    fontSize = DawTheme.FontLabel,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

// ── Back handler with swipe gesture ───────────────────────────────────────────

@Composable
fun DawBackHandler(
    navState: DawNavState,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val edgeZonePx = with(density) { 60.dp.toPx() }
    val thresholdPx = with(density) { 80.dp.toPx() }

    var dragProgress by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var startedInEdge by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(navState.canGoBack) {
                if (!navState.canGoBack) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        startedInEdge = offset.x < edgeZonePx
                        if (startedInEdge) {
                            isDragging = true
                            dragProgress = 0f
                        }
                    },
                    onDragEnd = {
                        if (startedInEdge && dragProgress >= thresholdPx) {
                            navState.pop()
                        }
                        isDragging = false
                        dragProgress = 0f
                        startedInEdge = false
                    },
                    onDragCancel = {
                        isDragging = false
                        dragProgress = 0f
                        startedInEdge = false
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        if (startedInEdge && dragAmount > 0) {
                            dragProgress = (dragProgress + dragAmount).coerceAtLeast(0f)
                        }
                    },
                )
            }
    ) {
        content()

        // Green edge indicator while dragging
        if (isDragging && startedInEdge && dragProgress > 0f) {
            val alpha = (dragProgress / thresholdPx).coerceIn(0f, 1f)
            val indicatorWidth = (dragProgress * 0.3f).coerceAtMost(with(density) { 6.dp.toPx() })
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(with(density) { indicatorWidth.toDp() })
                    .align(Alignment.CenterStart)
                    .drawBehind {
                        drawRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    DawTheme.AccentGreen.copy(alpha = alpha * 0.8f),
                                    Color.Transparent,
                                ),
                            ),
                        )
                    },
            )
        }

        // Back indicator at top-left
        AnimatedVisibility(
            visible = navState.canGoBack && !isDragging,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = DawTheme.SpaceSm, top = DawTheme.SpaceXs),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable { navState.pop() }
                    .padding(DawTheme.SpaceXs),
            ) {
                Text(
                    text = "\u25C0",
                    color = DawTheme.AccentGreenDim,
                    fontSize = DawTheme.FontLabel,
                    fontFamily = FontFamily.Monospace,
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    text = "BACK",
                    color = DawTheme.AccentGreenDim,
                    fontSize = DawTheme.FontLabel,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

// ── EditableParamRow ──────────────────────────────────────────────────────────

@Composable
fun EditableParamRow(
    label: String,
    value: Int,
    maxValue: Int = 0xFF,
    displayFn: (Int) -> String = { M8Song.hex2(it) },
    onValueChange: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = DawTheme.SpaceXs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = DawTheme.TextNormal,
            fontSize = DawTheme.FontMono,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f),
        )

        Text(
            text = displayFn(value),
            color = DawTheme.AccentGreen,
            fontSize = DawTheme.FontMono,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = DawTheme.SpaceSm),
        )

        RepeatButton(
            text = "-",
            enabled = value > 0,
            onClick = { onValueChange((value - 1).coerceAtLeast(0)) },
        )

        Spacer(modifier = Modifier.width(DawTheme.SpaceXs))

        RepeatButton(
            text = "+",
            enabled = value < maxValue,
            onClick = { onValueChange((value + 1).coerceAtMost(maxValue)) },
        )
    }
}

@Composable
private fun RepeatButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Long-press repeat
    LaunchedEffect(isPressed, enabled) {
        if (isPressed && enabled) {
            delay(400L)
            while (isPressed) {
                onClick()
                delay(80L)
            }
        }
    }

    val borderColor = if (enabled) DawTheme.AccentGreen else DawTheme.BorderDim
    val textColor = if (enabled) DawTheme.AccentGreen else DawTheme.TextDim
    val bgColor = if (isPressed && enabled) DawTheme.AccentGreen.copy(alpha = 0.15f) else Color.Transparent

    Box(
        modifier = Modifier
            .size(width = 32.dp, height = 28.dp)
            .clip(RoundedCornerShape(DawTheme.CornerSm))
            .border(1.dp, borderColor, RoundedCornerShape(DawTheme.CornerSm))
            .background(bgColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = DawTheme.FontBody,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
        )
    }
}

// ── EditableEnumRow ───────────────────────────────────────────────────────────

@Composable
fun <T> EditableEnumRow(
    label: String,
    value: T,
    values: Array<T>,
    labelFn: (T) -> String,
    onValueChange: (T) -> Unit,
) {
    val currentIndex = values.indexOf(value).coerceAtLeast(0)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = DawTheme.SpaceXs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = DawTheme.TextNormal,
            fontSize = DawTheme.FontMono,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f),
        )

        Text(
            text = labelFn(value),
            color = DawTheme.AccentCyan,
            fontSize = DawTheme.FontMono,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = DawTheme.SpaceSm),
        )

        RepeatButton(
            text = "\u25C0",
            enabled = currentIndex > 0,
            onClick = {
                val prev = (currentIndex - 1).coerceAtLeast(0)
                onValueChange(values[prev])
            },
        )

        Spacer(modifier = Modifier.width(DawTheme.SpaceXs))

        RepeatButton(
            text = "\u25B6",
            enabled = currentIndex < values.lastIndex,
            onClick = {
                val next = (currentIndex + 1).coerceAtMost(values.lastIndex)
                onValueChange(values[next])
            },
        )
    }
}
