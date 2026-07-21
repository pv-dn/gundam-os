package com.uc0079.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Zeta Gundam (A.E.U.G. / MSZ-006) cockpit HUD palette. */
object G {
    val Bg = Color(0xFF04060C)
    val ScrimTop = Color(0xF20A1020)
    val ScrimBottom = Color(0xFA03040A)
    val Panel = Color(0x1A2C6BE6)
    val PanelStrong = Color(0x2A16305E)
    val Border = Color(0x662C6BE6)
    val Blue = Color(0xFF2C6BE6)
    val Cyan = Color(0xFF4FB6E6)
    val Red = Color(0xFFD8232A)
    val Yellow = Color(0xFFF2C230)
    val White = Color(0xFFEAEEF6)
    val Dim = Color(0xFF8393AE)
}

@Composable
fun GundamTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = G.Blue,
            secondary = G.Cyan,
            background = G.Bg,
            surface = G.Bg,
            onPrimary = G.White,
            onBackground = G.White,
            onSurface = G.White,
        ),
        content = content
    )
}

/**
 * Draws a thin HUD frame with cut corners and small corner brackets behind the content.
 */
fun Modifier.hudFrame(
    border: Color = G.Border,
    fill: Color = G.Panel,
    bracket: Color = G.Blue,
    cut: Dp = 8.dp,
): Modifier = this.drawBehind {
    val c = cut.toPx()
    val sw = 1.2.dp.toPx()
    val w = size.width
    val h = size.height

    val shape = androidx.compose.ui.graphics.Path().apply {
        moveTo(c, 0f)
        lineTo(w, 0f)
        lineTo(w, h - c)
        lineTo(w - c, h)
        lineTo(0f, h)
        lineTo(0f, c)
        close()
    }
    drawPath(shape, color = fill)
    drawPath(shape, color = border, style = androidx.compose.ui.graphics.drawscope.Stroke(width = sw))

    val bl = 10.dp.toPx()
    val bsw = 2f.dp.toPx()
    // top-left bracket
    drawLine(bracket, androidx.compose.ui.geometry.Offset(0f, c), androidx.compose.ui.geometry.Offset(0f, c + bl), strokeWidth = bsw)
    drawLine(bracket, androidx.compose.ui.geometry.Offset(c, 0f), androidx.compose.ui.geometry.Offset(c + bl, 0f), strokeWidth = bsw)
    // bottom-right bracket
    drawLine(bracket, androidx.compose.ui.geometry.Offset(w, h - c - bl), androidx.compose.ui.geometry.Offset(w, h - c), strokeWidth = bsw)
    drawLine(bracket, androidx.compose.ui.geometry.Offset(w - c - bl, h), androidx.compose.ui.geometry.Offset(w - c, h), strokeWidth = bsw)
}

/**
 * Cockpit canopy panel: deep space gradient, stars, angled HUD frame, side rails.
 */
fun Modifier.cockpitPanel(
    cut: Dp = 12.dp,
): Modifier = this.drawBehind {
    val c = cut.toPx()
    val w = size.width
    val h = size.height

    val canopy = androidx.compose.ui.graphics.Path().apply {
        moveTo(c * 1.4f, 0f)
        lineTo(w - c * 0.4f, 0f)
        lineTo(w, c * 0.6f)
        lineTo(w, h - c)
        lineTo(w - c, h)
        lineTo(c * 0.5f, h)
        lineTo(0f, h - c * 0.8f)
        lineTo(0f, c)
        close()
    }

    // Deep space fill
    drawPath(
        canopy,
        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
            colors = listOf(
                Color(0xFF0A1528),
                Color(0xFF050A14),
                Color(0xFF02040A),
            )
        )
    )

    // Soft nebula glow (blue, not purple)
    drawPath(
        canopy,
        brush = androidx.compose.ui.graphics.Brush.radialGradient(
            colors = listOf(
                Color(0x332C6BE6),
                Color(0x00000000),
            ),
            center = androidx.compose.ui.geometry.Offset(w * 0.75f, h * 0.2f),
            radius = w * 0.55f
        )
    )

    // Starfield (deterministic from size)
    val seed = (w * 13 + h * 7).toInt()
    var s = seed
    fun next(): Float {
        s = (s * 1103515245 + 12345) and 0x7fffffff
        return (s % 10000) / 10000f
    }
    repeat(42) {
        val sx = next() * w
        val sy = next() * h
        val r = 0.6f + next() * 1.4f
        val a = 0.25f + next() * 0.65f
        drawCircle(
            color = Color.White.copy(alpha = a),
            radius = r,
            center = androidx.compose.ui.geometry.Offset(sx, sy)
        )
    }

    // Horizon scan line
    val hy = h * 0.62f
    drawLine(
        color = Color(0x334FB6E6),
        start = androidx.compose.ui.geometry.Offset(c, hy),
        end = androidx.compose.ui.geometry.Offset(w - c, hy),
        strokeWidth = 1.dp.toPx()
    )

    // Frame stroke
    drawPath(
        canopy,
        color = G.Border,
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.4.dp.toPx())
    )

    // Side rails (cockpit structure)
    val rail = 3.dp.toPx()
    drawRect(
        color = G.Red,
        topLeft = androidx.compose.ui.geometry.Offset(0f, c),
        size = androidx.compose.ui.geometry.Size(rail, h - c * 1.8f)
    )
    drawRect(
        color = G.Blue.copy(alpha = 0.85f),
        topLeft = androidx.compose.ui.geometry.Offset(w - rail, c * 0.6f),
        size = androidx.compose.ui.geometry.Size(rail, h - c * 1.6f)
    )

    // Corner brackets
    val bl = 14.dp.toPx()
    val bsw = 2.2.dp.toPx()
    drawLine(G.Cyan, androidx.compose.ui.geometry.Offset(c * 1.4f, 0f), androidx.compose.ui.geometry.Offset(c * 1.4f + bl, 0f), strokeWidth = bsw)
    drawLine(G.Cyan, androidx.compose.ui.geometry.Offset(0f, c), androidx.compose.ui.geometry.Offset(0f, c + bl), strokeWidth = bsw)
    drawLine(G.Cyan, androidx.compose.ui.geometry.Offset(w - c, h), androidx.compose.ui.geometry.Offset(w - c - bl, h), strokeWidth = bsw)
    drawLine(G.Cyan, androidx.compose.ui.geometry.Offset(w, h - c), androidx.compose.ui.geometry.Offset(w, h - c - bl), strokeWidth = bsw)
}

/** A simple parallelogram accent bar. */
val SkewTag: GenericShape = GenericShape { size, _ ->
    val skew = size.height * 0.5f
    moveTo(skew, 0f)
    lineTo(size.width, 0f)
    lineTo(size.width - skew, size.height)
    lineTo(0f, size.height)
    close()
}

@Composable
fun ScrimBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    listOf(
                        Color(0xF2081224),
                        Color(0xFA03060E),
                        Color(0xFF010208),
                    )
                )
            )
            .drawBehind {
                // Distant stars on the wallpaper scrim
                var s = 42_424
                fun next(): Float {
                    s = (s * 1103515245 + 12345) and 0x7fffffff
                    return (s % 10000) / 10000f
                }
                repeat(60) {
                    val sx = next() * size.width
                    val sy = next() * size.height
                    val r = 0.5f + next() * 1.2f
                    drawCircle(
                        color = Color.White.copy(alpha = 0.12f + next() * 0.35f),
                        radius = r,
                        center = androidx.compose.ui.geometry.Offset(sx, sy)
                    )
                }
                val gap = 5.dp.toPx()
                val line = Color(0x084FB6E6)
                var y = 0f
                while (y < size.height) {
                    drawLine(
                        color = line,
                        start = androidx.compose.ui.geometry.Offset(0f, y),
                        end = androidx.compose.ui.geometry.Offset(size.width, y),
                        strokeWidth = 1f
                    )
                    y += gap
                }
            }
    ) { content() }
}
