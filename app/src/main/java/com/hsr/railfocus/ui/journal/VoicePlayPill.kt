package com.hsr.railfocus.ui.journal

import com.hsr.railfocus.R
import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.io.File

/**
 * 极简手账语音播放胶囊组件
 * 兼容本地文件路径与 Uri，点击直接播放/暂停
 */
@Composable
fun VoicePlayPill(
    audioPath: String,
    durationSec: Int,
    modifier: Modifier = Modifier,
    labelPrefix: String? = null,
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(audioPath) {
        onDispose {
            try {
                player?.stop()
            } catch (_: Exception) {}
            try {
                player?.release()
            } catch (_: Exception) {}
            player = null
            isPlaying = false
        }
    }

    fun togglePlay() {
        if (isPlaying) {
            try {
                player?.pause()
            } catch (_: Exception) {}
            isPlaying = false
        } else {
            try {
                if (player == null) {
                    player = MediaPlayer().apply {
                        if (audioPath.startsWith("content://") || audioPath.startsWith("android.resource://") || audioPath.startsWith("file://")) {
                            setDataSource(context, Uri.parse(audioPath))
                        } else {
                            val file = File(audioPath)
                            if (!file.exists()) return
                            setDataSource(file.absolutePath)
                        }
                        setOnCompletionListener {
                            isPlaying = false
                            try {
                                seekTo(0)
                            } catch (_: Exception) {}
                        }
                        prepare()
                    }
                } else {
                    try {
                        if (!player!!.isPlaying && player!!.currentPosition >= player!!.duration - 50) {
                            player?.seekTo(0)
                        }
                    } catch (_: Exception) {}
                }
                player?.start()
                isPlaying = true
            } catch (_: Exception) {
                isPlaying = false
            }
        }
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f),
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = modifier.clickable { togglePlay() },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = stringResource(if (isPlaying) R.string.cd_pause else R.string.cd_play),
                modifier = Modifier.size(16.dp),
            )

            AudioWaveVisualizer(
                isPlaying = isPlaying,
                tint = MaterialTheme.colorScheme.primary,
            )

            val formattedDuration = "%02d:%02d".format(durationSec / 60, durationSec % 60)
            val displayText = if (labelPrefix != null) "$labelPrefix $formattedDuration" else formattedDuration
            Text(
                text = displayText,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun AudioWaveVisualizer(
    isPlaying: Boolean,
    tint: Color,
    modifier: Modifier = Modifier
) {
    if (!isPlaying) {
        Icon(
            imageVector = Icons.Default.GraphicEq,
            contentDescription = null,
            modifier = modifier.size(15.dp),
            tint = tint,
        )
    } else {
        val infiniteTransition = rememberInfiniteTransition(label = "audio_wave")
        val b1 by infiniteTransition.animateFloat(
            initialValue = 0.35f, targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(420, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "b1"
        )
        val b2 by infiniteTransition.animateFloat(
            initialValue = 0.85f, targetValue = 0.25f,
            animationSpec = infiniteRepeatable(
                animation = tween(320, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "b2"
        )
        val b3 by infiniteTransition.animateFloat(
            initialValue = 0.4f, targetValue = 0.95f,
            animationSpec = infiniteRepeatable(
                animation = tween(520, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "b3"
        )
        val b4 by infiniteTransition.animateFloat(
            initialValue = 0.9f, targetValue = 0.3f,
            animationSpec = infiniteRepeatable(
                animation = tween(380, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "b4"
        )

        Row(
            modifier = modifier.size(width = 16.dp, height = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(1.5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(b1)
                    .clip(RoundedCornerShape(1.dp))
                    .background(tint)
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(b2)
                    .clip(RoundedCornerShape(1.dp))
                    .background(tint)
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(b3)
                    .clip(RoundedCornerShape(1.dp))
                    .background(tint)
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(b4)
                    .clip(RoundedCornerShape(1.dp))
                    .background(tint)
            )
        }
    }
}
