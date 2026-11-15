package com.dsann.music.ui.player

import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import kotlin.math.max
import kotlin.math.min

@Composable
fun TopAudioVisualizerPill(
    title: String,
    artist: String,
    artworkUri: Uri?,
    levels: List<Float>,
    isPlaying: Boolean,
    style: String,
    colorPreset: String,
    sizeScale: Float = 1f,
    animateColors: Boolean = true,
    animationSpeed: Float = 1f,
    backgroundTint: Color? = null,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val accent = MaterialTheme.colorScheme.primary
    val safeScale = sizeScale.coerceIn(0.75f, 1.35f)
    val safeSpeed = animationSpeed.coerceIn(0.5f, 2f)
    val capsuleBackground by animateColorAsState(
        targetValue = backgroundTint?.copy(alpha = 0.94f) ?: Color(0xF20B0E18),
        animationSpec = tween(durationMillis = 700),
        label = "TopVisualizerArtworkBackground"
    )
    val colorTransition = rememberInfiniteTransition(label = "TopVisualizerColors")
    val colorPhase by colorTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (animateColors) 360f else 0f,
        animationSpec = infiniteRepeatable(animation = tween(
            durationMillis = (7000f / safeSpeed).toInt().coerceIn(1200, 16000),
            easing = LinearEasing
        )),
        label = "TopVisualizerHueCycle"
    )

    Box(
        modifier = modifier
            .widthIn(min = 184.dp * safeScale, max = 232.dp * safeScale)
            .height(48.dp * safeScale)
            .shadow(18.dp * safeScale, CircleShape)
            .clip(CircleShape)
            .background(
                Brush.horizontalGradient(
                    listOf(
                        capsuleBackground.copy(alpha = 0.92f),
                        capsuleBackground.copy(alpha = 0.78f),
                        capsuleBackground.copy(alpha = 0.92f)
                    )
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
            .clickable { expanded = true }
            .padding(horizontal = 11.dp * safeScale),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp * safeScale)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(accent, Color(0xFF182A3C)))),
                contentAlignment = Alignment.Center
            ) {
                if (artworkUri != null) {
                    AsyncImage(
                        model = artworkUri,
                        contentDescription = null,
                        modifier = Modifier.size(30.dp * safeScale).clip(CircleShape)
                    )
                } else {
                    Text("♪", color = Color.White, fontSize = 18.sp * safeScale, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.width(12.dp * safeScale))
            AudioSpectrumCanvas(
                levels = levels,
                isPlaying = isPlaying,
                style = style,
                colorPreset = colorPreset,
                accent = accent,
                colorPhase = colorPhase,
                animateColors = animateColors,
                modifier = Modifier.weight(1f).height(29.dp * safeScale)
            )
        }
    }

    if (expanded) {
        Dialog(onDismissRequest = { expanded = false }) {
            Surface(
                modifier = Modifier.fillMaxWidth().widthIn(max = 380.dp),
                shape = RoundedCornerShape(30.dp),
                color = Color(0xFF10131D),
                tonalElevation = 8.dp,
                shadowElevation = 20.dp
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "NOW PLAYING",
                            color = accent,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.8.sp
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            "CLOSE",
                            color = Color.White.copy(alpha = 0.70f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { expanded = false }
                                .padding(horizontal = 10.dp, vertical = 7.dp)
                        )
                    }

                    Spacer(Modifier.height(18.dp))
                    Box(
                        modifier = Modifier
                            .size(58.dp * safeScale)
                            .clip(RoundedCornerShape(18.dp * safeScale))
                            .background(Brush.linearGradient(listOf(accent, Color(0xFF202B47)))),
                        contentAlignment = Alignment.Center
                    ) {
                        if (artworkUri != null) {
                            AsyncImage(
                                model = artworkUri,
                                contentDescription = null,
                                modifier = Modifier.size(58.dp * safeScale).clip(RoundedCornerShape(18.dp * safeScale))
                            )
                        } else {
                            Text("♪", color = Color.White, fontSize = 26.sp * safeScale)
                        }
                    }

                    Spacer(Modifier.height(11.dp))
                    Text(
                        title,
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        artist,
                        color = Color.White.copy(alpha = 0.62f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(Modifier.height(20.dp))
                    AudioSpectrumCanvas(
                        levels = levels,
                        isPlaying = isPlaying,
                        style = style,
                        colorPreset = colorPreset,
                        accent = accent,
                        colorPhase = colorPhase,
                        animateColors = animateColors,
                        modifier = Modifier.fillMaxWidth().height(138.dp * safeScale)
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        if (isPlaying) "LIVE AUDIO  •  REAL-TIME SPECTRUM" else "PLAY THE TRACK TO SEE THE SPECTRUM",
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.1.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun AudioSpectrumCanvas(
    levels: List<Float>,
    isPlaying: Boolean,
    style: String,
    colorPreset: String,
    accent: Color,
    colorPhase: Float,
    animateColors: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val count = min(PlaybackBands, levels.size).coerceAtLeast(1)
        val centerY = size.height / 2f

        fun barColor(index: Int): Color {
            val fraction = if (count <= 1) 0f else index / (count - 1f)
            if (colorPreset == "Accent" && !animateColors) return accent
            val baseHue = when (colorPreset) {
                "Amber" -> 34f
                "Ocean" -> 184f
                "Violet" -> 248f
                "Accent" -> 218f
                else -> 185f
            }
            val hueSpan = if (colorPreset == "Spectrum") 155f else 24f
            val phase = if (animateColors) colorPhase else 0f
            val saturation = if (colorPreset == "Spectrum") 0.88f else 0.86f
            return Color.hsv((baseHue + fraction * hueSpan + phase) % 360f, saturation, 1f)
        }

        if (style == "Wave") {
            val path = Path()
            repeat(count) { index ->
                val x = if (count == 1) size.width / 2f else size.width * index / (count - 1f)
                val level = if (isPlaying) levels[index].coerceIn(0f, 1f) else 0.025f
                val y = centerY - (0.04f + level * 0.43f) * size.height
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path = path,
                brush = Brush.horizontalGradient(List(count) { barColor(it).copy(alpha = 0.98f) }),
                style = Stroke(width = max(1.8.dp.toPx(), size.height * 0.055f))
            )
            val lowerPath = Path()
            repeat(count) { index ->
                val x = if (count == 1) size.width / 2f else size.width * index / (count - 1f)
                val level = if (isPlaying) levels[index].coerceIn(0f, 1f) else 0.025f
                val y = centerY + (0.04f + level * 0.38f) * size.height
                if (index == 0) lowerPath.moveTo(x, y) else lowerPath.lineTo(x, y)
            }
            drawPath(
                lowerPath,
                brush = Brush.horizontalGradient(List(count) { barColor(it).copy(alpha = 0.48f) }),
                style = Stroke(width = max(1.2.dp.toPx(), size.height * 0.035f))
            )
            return@Canvas
        }

        val gap = size.width * 0.025f
        val barWidth = ((size.width - gap * (count - 1)) / count).coerceAtLeast(1f)
        repeat(count) { index ->
            val level = if (isPlaying) levels[index].coerceIn(0f, 1f) else 0.035f
            val barHeight = (size.height * (0.10f + level * 0.86f)).coerceIn(2.dp.toPx(), size.height)
            val x = index * (barWidth + gap)
            val y = centerY - barHeight / 2f
            val color = barColor(index)

            if (style == "Dots") {
                val dotSize = min(barWidth, size.height * 0.16f).coerceAtLeast(2.dp.toPx())
                val step = dotSize * 1.55f
                val dotCount = max(1, (barHeight / step).toInt())
                repeat(dotCount) { dot ->
                    val dotY = centerY + (dotCount - 1) * step / 2f - dot * step - dotSize / 2f
                    drawRoundRect(
                        color.copy(alpha = 0.35f + level * 0.65f),
                        topLeft = Offset(x + (barWidth - dotSize) / 2f, dotY),
                        size = Size(dotSize, dotSize),
                        cornerRadius = CornerRadius(dotSize / 2f)
                    )
                }
            } else {
                drawRoundRect(
                    color.copy(alpha = 0.18f),
                    topLeft = Offset(x - barWidth * 0.18f, y - 2.dp.toPx()),
                    size = Size(barWidth * 1.36f, barHeight + 4.dp.toPx()),
                    cornerRadius = CornerRadius(barWidth)
                )
                drawRoundRect(
                    color.copy(alpha = 0.98f),
                    topLeft = Offset(x, y),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(barWidth)
                )
            }
        }
    }
}

private const val PlaybackBands = 24
