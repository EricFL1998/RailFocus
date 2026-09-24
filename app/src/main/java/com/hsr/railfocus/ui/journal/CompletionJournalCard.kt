package com.hsr.railfocus.ui.journal

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.hsr.railfocus.R
import com.hsr.railfocus.domain.model.JourneyJournal
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 适合到站完成弹窗展示的旅行手账卡片（紧凑无空白，图片与文字紧密融合展示）
 */
@Composable
fun CompletionJournalCard(
    journal: JourneyJournal,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dateStr = remember(journal.createdAt) {
        SimpleDateFormat("yyyy年MM月dd日", Locale.getDefault()).format(Date(journal.createdAt))
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // 顶部小标与修改按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                        )
                        Text(
                            text = stringResource(R.string.journal_card_label),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.clickable(onClick = onEdit),
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = stringResource(R.string.action_edit),
                        modifier = Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = stringResource(R.string.action_edit),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            // 配图照片（支持 1~3 张并排自适应展示）
            val validImages = journal.imagePaths.filter { it.isNotBlank() }
            if (validImages.isNotEmpty()) {
                if (validImages.size == 1) {
                    val model = resolveImageModel(validImages.first())
                    AsyncImage(
                        model = model,
                        contentDescription = stringResource(R.string.journal_photo_cd),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 100.dp, max = 150.dp)
                            .clip(RoundedCornerShape(12.dp)),
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        validImages.take(3).forEach { pathOrUri ->
                            val model = resolveImageModel(pathOrUri)
                            AsyncImage(
                                model = model,
                                contentDescription = stringResource(R.string.journal_photo_cd),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(84.dp)
                                    .clip(RoundedCornerShape(10.dp)),
                            )
                        }
                    }
                }
            }

            // 语音留念（若有）
            val audio = journal.audioPath
            if (!audio.isNullOrBlank() && File(audio).exists()) {
                VoicePlayPill(
                    audioPath = audio,
                    durationSec = journal.audioDurationSec,
                )
            }

            // 心得文字（紧随图片下方，绝不产生空白）
            if (journal.content.isNotBlank()) {
                Text(
                    text = journal.content,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            // 底部注脚
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                Text(
                    text = stringResource(R.string.journal_written_at, dateStr, journal.stationName),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }
        }
    }
}

fun resolveImageModel(pathOrUri: String): Any {
    return if (pathOrUri.startsWith("content://") || pathOrUri.startsWith("file://") || pathOrUri.startsWith("android.resource://")) {
        Uri.parse(pathOrUri)
    } else {
        File(pathOrUri)
    }
}
