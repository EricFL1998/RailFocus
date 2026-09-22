package com.hsr.railfocus.ui.journal

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
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
                        }
                        prepare()
                    }
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
                contentDescription = if (isPlaying) "暂停" else "播放",
                modifier = Modifier.size(16.dp),
            )

            Icon(
                imageVector = Icons.Default.GraphicEq,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
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

