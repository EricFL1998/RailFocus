package com.hsr.railfocus.ui.journal

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.hsr.railfocus.domain.model.MemoryRecall
import com.hsr.railfocus.domain.model.RecallType
import java.io.File

/**
 * 【那年今日 · 车站旧忆】时光卡片
 * 1. 刚好当天：直接展示当年手账图文与语音，顶部只留徽章与年份标注；
 * 2. 未来 1~3 天内：以简短文字表达提前抵站与未来相遇；
 * 3. 过去/非当天：以简短文字表达错过与旧日停留回忆；
 * 4. 无论何时再次遇到，若当年留下了语音，均可直接点击播放！
 */
@Composable
fun MemoryRecallCard(
    recall: MemoryRecall,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val audio = recall.journal.audioPath
    val hasAudio = !audio.isNullOrBlank()

    when (recall.type) {
        RecallType.SAME_DAY_YEARS_AGO -> {
            // 场景 1：刚好当天 -> 完整展示当年手账
            Card(
                modifier = modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                ),
                border = BorderStroke(1.dp, Color(0xFFD97706).copy(alpha = 0.35f)),
                onClick = onClick ?: {},
                enabled = onClick != null,
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // 顶部：徽章 + 年份标注（纯展示）
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFFEF3C7),
                            contentColor = Color(0xFFD97706),
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
                                    text = "那年今日",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }

                        Text(
                            text = if (recall.yearsAgo > 0) "${recall.yearsAgo}年前的今天" else "曾经的今天",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    // 当年手账照片（1~3 张并排）
                    val imagePaths = recall.journal.imagePaths.filter { it.isNotBlank() }
                    if (imagePaths.isNotEmpty()) {
                        if (imagePaths.size == 1) {
                            val model = resolveImageModel(imagePaths.first())
                            AsyncImage(
                                model = model,
                                contentDescription = "当年照片",
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
                                imagePaths.take(3).forEach { path ->
                                    val model = resolveImageModel(path)
                                    AsyncImage(
                                        model = model,
                                        contentDescription = "当年照片",
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

                    // 当年语音留念（若有，可直接播放）
                    if (hasAudio) {
                        VoicePlayPill(
                            audioPath = audio,
                            durationSec = recall.journal.audioDurationSec,
                        )
                    }

                    // 当年手账文字
                    if (recall.journal.content.isNotBlank()) {
                        Text(
                            text = recall.journal.content,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 14.sp,
                                lineHeight = 22.sp,
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    // 底部：加入手账时间与车站
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Text(
                            text = "写于 ${recall.formattedDateTime} · ${recall.journal.stationName}站",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                    }
                }
            }
        }
        RecallType.UPCOMING_DAYS -> {
            // 场景 2：未来 1~3 天内 -> 表达提前抵达与对未来的相遇期许
            Card(
                modifier = modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                ),
                border = BorderStroke(1.dp, Color(0xFF059669).copy(alpha = 0.35f)),
                onClick = onClick ?: {},
                enabled = onClick != null,
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // 顶部小标
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFD1FAE5),
                        contentColor = Color(0xFF059669),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                            )
                            Text(
                                text = "即将重逢",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }

                    // 表达未来与期许的简短文字
                    Text(
                        text = recall.message,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 14.sp,
                            lineHeight = 22.sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                    )

                    // 底部注脚
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Text(
                            text = "当年写于 ${recall.formattedDate} · ${recall.journal.stationName}站",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                    }
                }
            }
        }
        RecallType.REVISIT -> {
            // 场景 3：过去/非当天 -> 表达对过去停留的错过与回忆
            Card(
                modifier = modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                ),
                border = BorderStroke(1.dp, Color(0xFF0284C7).copy(alpha = 0.3f)),
                onClick = onClick ?: {},
                enabled = onClick != null,
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // 顶部小标
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFE0F2FE),
                        contentColor = Color(0xFF0284C7),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                            )
                            Text(
                                text = "车站旧忆",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }

                    // 表达错过与回忆的简短文字
                    Text(
                        text = recall.message,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 14.sp,
                            lineHeight = 22.sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                    )

                    // 底部注脚
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Text(
                            text = "上次停留于 ${recall.formattedDate} · ${recall.journal.stationName}站",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                    }
                }
            }
        }
    }
}
