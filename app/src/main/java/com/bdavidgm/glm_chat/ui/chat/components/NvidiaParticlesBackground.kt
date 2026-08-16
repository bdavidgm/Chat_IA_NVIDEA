package com.bdavidgm.glm_chat.ui.chat.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.isActive
import kotlin.random.Random

/**
 * Representa una partícula individual en el sistema.
 * Las propiedades se mutan in-place para evitar GC pressure.
 */
private class Particle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val radius: Float,
    val color: Color,
    val alpha: Float
)

@Composable
fun NvidiaParticlesBackground(
    modifier: Modifier = Modifier,
    particleCount: Int = 100,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val connectionDistancePx = with(density) { 120.dp.toPx() }
    val connectionDistanceSq = connectionDistancePx * connectionDistancePx

    val nvidiaGreen = Color(0xFF76B900)
    val pureBlack = Color(0xFF000000)

    Box(modifier = modifier.fillMaxSize()) {
        // Isolated so IME/frame state cannot recompose the chat tree sitting in [content].
        ParticleLayer(
            particleCount = particleCount,
            connectionDistanceSq = connectionDistanceSq,
            nvidiaGreen = nvidiaGreen,
            pureBlack = pureBlack,
        )
        content()
    }
}

@Composable
private fun ParticleLayer(
    particleCount: Int,
    connectionDistanceSq: Float,
    nvidiaGreen: Color,
    pureBlack: Color,
) {
    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    val particles = remember { mutableStateListOf<Particle>() }
    var frameTime by remember { mutableLongStateOf(0L) }

    LaunchedEffect(imeVisible) {
        if (imeVisible) return@LaunchedEffect
        while (isActive) {
            withFrameNanos { time ->
                frameTime = time
            }
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        if (frameTime == -1L) return@Canvas

        val width = size.width
        val height = size.height

        if (particles.isEmpty() && width > 0) {
            repeat(particleCount) {
                val isGreen = Random.nextFloat() > 0.3f
                particles.add(
                    Particle(
                        x = Random.nextFloat() * width,
                        y = Random.nextFloat() * height,
                        vx = (Random.nextFloat() - 0.5f) * 1.5f,
                        vy = (Random.nextFloat() - 0.5f) * 1.5f,
                        radius = Random.nextFloat() * 2.5f + 1f,
                        color = if (isGreen) nvidiaGreen else Color.White,
                        alpha = Random.nextFloat() * 0.5f + 0.2f
                    )
                )
            }
        }

        drawRect(color = pureBlack)

        for (i in 0 until particles.size) {
            val p1 = particles[i]

            p1.x += p1.vx
            p1.y += p1.vy

            if (p1.x < 0 || p1.x > width) p1.vx *= -1
            if (p1.y < 0 || p1.y > height) p1.vy *= -1

            for (j in i + 1 until particles.size) {
                val p2 = particles[j]
                val dx = p1.x - p2.x
                val dy = p1.y - p2.y
                val distSq = dx * dx + dy * dy

                if (distSq < connectionDistanceSq) {
                    val fraction = 1f - (distSq / connectionDistanceSq)
                    val lineAlpha = fraction * 0.25f

                    drawLine(
                        color = nvidiaGreen.copy(alpha = lineAlpha),
                        start = Offset(p1.x, p1.y),
                        end = Offset(p2.x, p2.y),
                        strokeWidth = 1f
                    )
                }
            }

            drawCircle(
                color = p1.color.copy(alpha = p1.alpha),
                radius = p1.radius,
                center = Offset(p1.x, p1.y)
            )
        }
    }
}
