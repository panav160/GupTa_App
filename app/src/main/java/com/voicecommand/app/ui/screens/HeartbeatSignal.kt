package com.voicecommand.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.sin

@Composable
fun HeartbeatSignal(
    isActive: Boolean,
    glowAlpha: Float,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    val primaryDark = primary.copy(
        red = (primary.red * 0.7f).coerceIn(0f, 1f),
        green = (primary.green * 0.7f).coerceIn(0f, 1f),
        blue = (primary.blue * 0.7f).coerceIn(0f, 1f)
    )

    var tick by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(50)
            tick++
        }
    }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cy = h / 2f
        val n = 80
        val dx = w / n.toFloat()
        val t = tick.toFloat() / 20f

        val path = Path()

        for (i in 0..n) {
            val x = i * dx
            val frac = i.toFloat() / n.toFloat()

            val pulse: Float = if (isActive) {
                val phase = (frac + t * 0.08f) % 1f
                when {
                    phase > 0.7f && phase < 0.78f -> {
                        val p = (phase - 0.7f) / 0.08f
                        -(h * 0.5f) * sin(p * 3.1415927f)
                    }
                    phase > 0.78f && phase < 0.85f -> {
                        val p = (phase - 0.78f) / 0.07f
                        (h * 0.25f) * sin(p * 3.1415927f)
                    }
                    phase > 0.3f && phase < 0.38f -> {
                        val p = (phase - 0.3f) / 0.08f
                        -(h * 0.4f) * sin(p * 3.1415927f)
                    }
                    phase > 0.38f && phase < 0.45f -> {
                        val p = (phase - 0.38f) / 0.07f
                        (h * 0.18f) * sin(p * 3.1415927f)
                    }
                    else -> 0f
                }
            } else {
                sin(frac * 12f + t * 0.3f) * h * 0.02f
            }

            val y = cy + pulse
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        val brush = Brush.horizontalGradient(
            listOf(primaryDark.copy(alpha = glowAlpha), primary.copy(alpha = glowAlpha))
        )

        drawPath(
            path = path,
            color = primaryDark.copy(alpha = glowAlpha * 0.08f),
            style = Stroke(width = 20.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        drawPath(
            path = path,
            color = primaryDark.copy(alpha = glowAlpha * 0.15f),
            style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        drawPath(
            path = path,
            color = primaryDark.copy(alpha = glowAlpha * 0.3f),
            style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        drawPath(
            path = path,
            brush = brush,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        if (isActive) {
            val dotPhase = (t * 0.08f) % 1f
            val dotIndex = (dotPhase * n).toInt().coerceIn(0, n)
            val dotFrac = dotIndex.toFloat() / n.toFloat()
            val dotPulse: Float = if (isActive) {
                val phase = (dotFrac + t * 0.08f) % 1f
                when {
                    phase > 0.7f && phase < 0.78f -> {
                        val p = (phase - 0.7f) / 0.08f
                        -(h * 0.5f) * sin(p * 3.1415927f)
                    }
                    phase > 0.78f && phase < 0.85f -> {
                        val p = (phase - 0.78f) / 0.07f
                        (h * 0.25f) * sin(p * 3.1415927f)
                    }
                    phase > 0.3f && phase < 0.38f -> {
                        val p = (phase - 0.3f) / 0.08f
                        -(h * 0.4f) * sin(p * 3.1415927f)
                    }
                    phase > 0.38f && phase < 0.45f -> {
                        val p = (phase - 0.38f) / 0.07f
                        (h * 0.18f) * sin(p * 3.1415927f)
                    }
                    else -> 0f
                }
            } else 0f
            val dotY = cy + dotPulse
            val dotX = dotIndex * dx
            drawCircle(
                color = primary.copy(alpha = glowAlpha),
                radius = 5.dp.toPx(),
                center = Offset(dotX, dotY)
            )
            drawCircle(
                color = primary.copy(alpha = glowAlpha * 0.3f),
                radius = 10.dp.toPx(),
                center = Offset(dotX, dotY)
            )
        }
    }
}