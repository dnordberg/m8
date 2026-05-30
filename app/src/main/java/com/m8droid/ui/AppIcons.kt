package com.m8droid.ui

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
 * Lightweight vector icons drawn directly with [Canvas] so the app can
 * avoid pulling in `material-icons-extended` and stay pixel-aligned with the
 * neon theme. Each icon composable takes a [tint] and a [size].
 */

private val DefaultIconSize: Dp = 20.dp

/** Simple gear — System/settings icon. */
@Composable
fun AppSystemIcon(tint: Color, size: Dp = DefaultIconSize) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val cx = w / 2
        val cy = h / 2
        val outer = minOf(w, h) * 0.42f
        val inner = minOf(w, h) * 0.18f
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

/** Open folder/file — FILE hub action. */
@Composable
fun AppLoadIcon(tint: Color, size: Dp = DefaultIconSize) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val sw = h * 0.08f

        // Back folder body with tab.
        val folder = Path().apply {
            moveTo(w * 0.12f, h * 0.32f)
            lineTo(w * 0.36f, h * 0.32f)
            lineTo(w * 0.46f, h * 0.42f)
            lineTo(w * 0.88f, h * 0.42f)
            lineTo(w * 0.88f, h * 0.78f)
            lineTo(w * 0.12f, h * 0.78f)
            close()
        }
        drawPath(
            folder,
            color = tint,
            style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )

        // Front open lip, angled upward like an opened file folder.
        val lip = Path().apply {
            moveTo(w * 0.16f, h * 0.78f)
            lineTo(w * 0.30f, h * 0.52f)
            lineTo(w * 0.92f, h * 0.52f)
            lineTo(w * 0.78f, h * 0.78f)
            close()
        }
        drawPath(
            lip,
            color = tint,
            style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

/** Question-mark badge — HELP action. */
@Composable
fun AppHelpIcon(tint: Color, size: Dp = DefaultIconSize) {
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
        val q = Path().apply {
            moveTo(w * 0.37f, h * 0.38f)
            quadraticTo(w * 0.5f, h * 0.22f, w * 0.62f, h * 0.38f)
            quadraticTo(w * 0.72f, h * 0.52f, w * 0.5f, h * 0.58f)
            lineTo(w * 0.5f, h * 0.66f)
        }
        drawPath(q, color = tint, style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(
            color = tint,
            radius = sw * 0.7f,
            center = androidx.compose.ui.geometry.Offset(w * 0.5f, h * 0.78f),
        )
    }
}
