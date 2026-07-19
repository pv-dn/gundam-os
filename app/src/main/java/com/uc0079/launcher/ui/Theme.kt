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

/** Gundam / U.C. cockpit HUD palette. */
object G {
    val Bg = Color(0xFF05070D)
    val ScrimTop = Color(0xF2060810)
    val ScrimBottom = Color(0xFA03040A)
    val Panel = Color(0x14318FE0)
    val PanelStrong = Color(0x22102A46)
    val Border = Color(0x552E7BE0)
    val Blue = Color(0xFF3B86E8)
    val Cyan = Color(0xFF37D0DA)
    val Red = Color(0xFFE0353C)
    val Yellow = Color(0xFFF4C020)
    val White = Color(0xFFE9EEF7)
    val Dim = Color(0xFF8394AE)
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
    bracket: Color = G.Cyan,
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
                    listOf(G.ScrimTop, G.ScrimBottom)
                )
            )
            .padding(0.dp)
    ) { content() }
}
