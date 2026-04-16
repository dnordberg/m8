package com.m8droid.ui.daw

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Lightweight vector icons drawn directly with [Canvas] so the DAW shell can
 * avoid pulling in `material-icons-extended` and stay pixel-aligned with the
 * neon theme. Each icon composable takes a [tint] and a [size] — everything
 * else is baked in.
 */

private val DefaultIconSize: Dp = 20.dp

// ───────── Module sidebar icons ─────────

/** 4×4 dot grid — PATTERN module. */
@Composable
fun DawPatternIcon(tint: Color, size: Dp = DefaultIconSize) {
    Canvas(modifier = Modifier.size(size)) {
        val cols = 4; val rows = 4
        val stepX = this.size.width / cols
        val stepY = this.size.height / rows
        val r = minOf(stepX, stepY) * 0.18f
        for (c in 0 until cols) for (rI in 0 until rows) {
            drawCircle(
                color = tint,
                radius = r,
                center = androidx.compose.ui.geometry.Offset(
                    stepX * (c + 0.5f),
                    stepY * (rI + 0.5f),
                ),
            )
        }
    }
}

/** Stylised piano keys — INSTRUMENT module. */
@Composable
fun DawInstrumentIcon(tint: Color, size: Dp = DefaultIconSize) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = Stroke(width = h * 0.08f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        // Key bed outline
        drawRoundRect(
            color = tint,
            topLeft = androidx.compose.ui.geometry.Offset(w * 0.05f, h * 0.2f),
            size = androidx.compose.ui.geometry.Size(w * 0.9f, h * 0.65f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(h * 0.08f),
            style = stroke,
        )
        // Two dividers marking white keys
        val topY = h * 0.2f + stroke.width / 2
        val botY = h * 0.85f - stroke.width / 2
        drawLine(
            color = tint,
            start = androidx.compose.ui.geometry.Offset(w * 0.37f, topY),
            end = androidx.compose.ui.geometry.Offset(w * 0.37f, botY),
            strokeWidth = stroke.width,
        )
        drawLine(
            color = tint,
            start = androidx.compose.ui.geometry.Offset(w * 0.63f, topY),
            end = androidx.compose.ui.geometry.Offset(w * 0.63f, botY),
            strokeWidth = stroke.width,
        )
        // Two black keys
        drawRoundRect(
            color = tint,
            topLeft = androidx.compose.ui.geometry.Offset(w * 0.27f, h * 0.22f),
            size = androidx.compose.ui.geometry.Size(w * 0.14f, h * 0.35f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(h * 0.05f),
        )
        drawRoundRect(
            color = tint,
            topLeft = androidx.compose.ui.geometry.Offset(w * 0.56f, h * 0.22f),
            size = androidx.compose.ui.geometry.Size(w * 0.14f, h * 0.35f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(h * 0.05f),
        )
    }
}

/** Three horizontal sliders with knobs — MIXER module. */
@Composable
fun DawMixerIcon(tint: Color, size: Dp = DefaultIconSize) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val sw = h * 0.08f
        val knobR = h * 0.12f
        // Three lanes with knobs at different positions
        val lanes = listOf(0.25f to 0.3f, 0.5f to 0.65f, 0.75f to 0.45f)
        for ((y01, knobX01) in lanes) {
            val y = h * y01
            drawLine(
                color = tint,
                start = androidx.compose.ui.geometry.Offset(w * 0.1f, y),
                end = androidx.compose.ui.geometry.Offset(w * 0.9f, y),
                strokeWidth = sw,
                cap = StrokeCap.Round,
            )
            drawCircle(
                color = tint,
                radius = knobR,
                center = androidx.compose.ui.geometry.Offset(w * knobX01, y),
            )
        }
    }
}

/** Waveform / spectrum bars — SAMPLES module. */
@Composable
fun DawSamplesIcon(tint: Color, size: Dp = DefaultIconSize) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val bars = floatArrayOf(0.3f, 0.7f, 0.45f, 0.9f, 0.5f, 0.25f)
        val barW = w * 0.1f
        val gap = (w - barW * bars.size) / (bars.size + 1)
        bars.forEachIndexed { i, b ->
            val bh = h * b
            val x = gap + i * (barW + gap)
            val y = h * 0.9f - bh
            drawRoundRect(
                color = tint,
                topLeft = androidx.compose.ui.geometry.Offset(x, y),
                size = androidx.compose.ui.geometry.Size(barW, bh),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barW * 0.3f),
            )
        }
    }
}

/** Simple gear — SYSTEM module. */
@Composable
fun DawSystemIcon(tint: Color, size: Dp = DefaultIconSize) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val cx = w / 2
        val cy = h / 2
        val outer = minOf(w, h) * 0.42f
        val inner = minOf(w, h) * 0.18f
        // Eight teeth as small rectangles around the center
        val teethCount = 8
        val toothLen = minOf(w, h) * 0.15f
        val toothWidth = minOf(w, h) * 0.12f
        for (i in 0 until teethCount) {
            val angle = (i * 2 * Math.PI / teethCount).toFloat()
            val tx = cx + outer * kotlin.math.cos(angle)
            val ty = cy + outer * kotlin.math.sin(angle)
            drawRoundRect(
                color = tint,
                topLeft = androidx.compose.ui.geometry.Offset(tx - toothWidth / 2, ty - toothLen / 2),
                size = androidx.compose.ui.geometry.Size(toothWidth, toothLen),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(toothWidth * 0.3f),
            )
        }
        drawCircle(
            color = tint,
            radius = outer - toothLen * 0.2f,
            center = androidx.compose.ui.geometry.Offset(cx, cy),
            style = Stroke(width = minOf(w, h) * 0.1f),
        )
        drawCircle(
            color = tint,
            radius = inner,
            center = androidx.compose.ui.geometry.Offset(cx, cy),
        )
    }
}

// ───────── Transport bar icons ─────────

/** Filled right-pointing triangle. */
@Composable
fun DawPlayIcon(tint: Color, size: Dp = DefaultIconSize) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val path = Path().apply {
            moveTo(w * 0.25f, h * 0.15f)
            lineTo(w * 0.85f, h * 0.5f)
            lineTo(w * 0.25f, h * 0.85f)
            close()
        }
        drawPath(path, color = tint)
    }
}

/** Two vertical bars. */
@Composable
fun DawPauseIcon(tint: Color, size: Dp = DefaultIconSize) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val barW = w * 0.16f
        val y1 = h * 0.18f
        val barH = h * 0.64f
        drawRoundRect(
            color = tint,
            topLeft = androidx.compose.ui.geometry.Offset(w * 0.28f, y1),
            size = androidx.compose.ui.geometry.Size(barW, barH),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(barW * 0.25f),
        )
        drawRoundRect(
            color = tint,
            topLeft = androidx.compose.ui.geometry.Offset(w * 0.56f, y1),
            size = androidx.compose.ui.geometry.Size(barW, barH),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(barW * 0.25f),
        )
    }
}

/** Filled square. */
@Composable
fun DawStopIcon(tint: Color, size: Dp = DefaultIconSize) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        drawRoundRect(
            color = tint,
            topLeft = androidx.compose.ui.geometry.Offset(w * 0.22f, h * 0.22f),
            size = androidx.compose.ui.geometry.Size(w * 0.56f, h * 0.56f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.1f),
        )
    }
}

/** Filled circle. */
@Composable
fun DawRecordIcon(tint: Color, size: Dp = DefaultIconSize) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        drawCircle(
            color = tint,
            radius = minOf(w, h) * 0.32f,
            center = androidx.compose.ui.geometry.Offset(w / 2, h / 2),
        )
    }
}

/** Two right-pointing triangles — fast forward. */
@Composable
fun DawFastForwardIcon(tint: Color, size: Dp = DefaultIconSize) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val first = Path().apply {
            moveTo(w * 0.1f, h * 0.22f)
            lineTo(w * 0.5f, h * 0.5f)
            lineTo(w * 0.1f, h * 0.78f)
            close()
        }
        val second = Path().apply {
            moveTo(w * 0.5f, h * 0.22f)
            lineTo(w * 0.9f, h * 0.5f)
            lineTo(w * 0.5f, h * 0.78f)
            close()
        }
        drawPath(first, color = tint)
        drawPath(second, color = tint)
    }
}

// ───────── Header action icons ─────────

/** Down-arrow into a tray — DOWNLOAD / LOAD action. */
@Composable
fun DawLoadIcon(tint: Color, size: Dp = DefaultIconSize) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val sw = h * 0.1f
        // Tray floor
        drawLine(
            color = tint,
            start = androidx.compose.ui.geometry.Offset(w * 0.18f, h * 0.85f),
            end = androidx.compose.ui.geometry.Offset(w * 0.82f, h * 0.85f),
            strokeWidth = sw,
            cap = StrokeCap.Round,
        )
        // Vertical shaft
        drawLine(
            color = tint,
            start = androidx.compose.ui.geometry.Offset(w * 0.5f, h * 0.12f),
            end = androidx.compose.ui.geometry.Offset(w * 0.5f, h * 0.60f),
            strokeWidth = sw,
            cap = StrokeCap.Round,
        )
        // Arrow head pointing down
        val arrow = Path().apply {
            moveTo(w * 0.25f, h * 0.50f)
            lineTo(w * 0.5f, h * 0.75f)
            lineTo(w * 0.75f, h * 0.50f)
        }
        drawPath(arrow, color = tint, style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** Question-mark badge — HELP action. */
@Composable
fun DawHelpIcon(tint: Color, size: Dp = DefaultIconSize) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val sw = h * 0.1f
        drawCircle(
            color = tint,
            radius = minOf(w, h) * 0.42f,
            center = androidx.compose.ui.geometry.Offset(w / 2, h / 2),
            style = Stroke(width = sw),
        )
        // Question arc
        val q = Path().apply {
            moveTo(w * 0.37f, h * 0.38f)
            quadraticTo(w * 0.5f, h * 0.22f, w * 0.62f, h * 0.38f)
            quadraticTo(w * 0.72f, h * 0.52f, w * 0.5f, h * 0.58f)
            lineTo(w * 0.5f, h * 0.66f)
        }
        drawPath(q, color = tint, style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
        // Dot
        drawCircle(
            color = tint,
            radius = sw * 0.7f,
            center = androidx.compose.ui.geometry.Offset(w * 0.5f, h * 0.78f),
        )
    }
}

/** Two left-pointing triangles — rewind. */
@Composable
fun DawRewindIcon(tint: Color, size: Dp = DefaultIconSize) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val first = Path().apply {
            moveTo(w * 0.9f, h * 0.22f)
            lineTo(w * 0.5f, h * 0.5f)
            lineTo(w * 0.9f, h * 0.78f)
            close()
        }
        val second = Path().apply {
            moveTo(w * 0.5f, h * 0.22f)
            lineTo(w * 0.1f, h * 0.5f)
            lineTo(w * 0.5f, h * 0.78f)
            close()
        }
        drawPath(first, color = tint)
        drawPath(second, color = tint)
    }
}
