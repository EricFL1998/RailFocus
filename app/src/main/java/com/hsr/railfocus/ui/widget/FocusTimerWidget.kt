package com.hsr.railfocus.ui.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.background
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.hsr.railfocus.MainActivity
import com.hsr.railfocus.R
import com.hsr.railfocus.data.repository.JourneyRepository
import com.hsr.railfocus.domain.model.JourneyRecord
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * 桌面小组件：展示进行中的专注旅程与剩余时间
 */
class FocusTimerWidget : androidx.glance.appwidget.GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val deps = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetDependencies::class.java,
        )
        val active = deps.journeyRepository().getActiveJourney()

        provideContent {
            GlanceTheme {
                if (active != null) {
                    ActiveJourneyContent(active)
                } else {
                    IdleContent()
                }
            }
        }
    }

    @Composable
    private fun ActiveJourneyContent(journey: JourneyRecord) {
        val remainingSec = journey.remainingSec ?: (journey.plannedDurationMin * 60)
        val totalSec = (journey.plannedDurationMin * 60).coerceAtLeast(1)
        val progress = (1f - remainingSec.toFloat() / totalSec).coerceIn(0f, 1f)
        val remainingLabel = formatRemaining(remainingSec)

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ColorProvider(Color(0xE61E2A3A)))
                .cornerRadius(24.dp)
                .clickable(actionStartActivity<MainActivity>())
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(modifier = GlanceModifier.fillMaxWidth()) {
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(
                        provider = ImageProvider(R.drawable.ic_bullet_train),
                        contentDescription = null,
                        modifier = GlanceModifier.padding(end = 8.dp),
                    )
                    Text(
                        text = journey.startStation.name + " → " + journey.endStation.name,
                        style = TextStyle(
                            fontWeight = FontWeight.Medium,
                            color = ColorProvider(Color.White),
                        ),
                        maxLines = 1,
                    )
                }
                Text(
                    text = remainingLabel,
                    style = TextStyle(
                        fontWeight = FontWeight.Bold,
                        color = ColorProvider(Color.White),
                    ),
                    modifier = GlanceModifier.padding(top = 4.dp),
                )
                LinearProgressIndicator(
                    progress = progress,
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
            }
        }
    }

    @Composable
    private fun IdleContent() {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ColorProvider(Color(0xE61E2A3A)))
                .cornerRadius(24.dp)
                .clickable(actionStartActivity<MainActivity>())
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = GlanceModifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "暂无进行中的专注",
                    style = TextStyle(color = ColorProvider(Color.White)),
                )
                Text(
                    text = "点击开始",
                    style = TextStyle(
                        fontWeight = FontWeight.Bold,
                        color = ColorProvider(Color.White),
                    ),
                    modifier = GlanceModifier
                        .padding(top = 8.dp)
                        .background(ColorProvider(Color(0x40FFFFFF)))
                        .cornerRadius(16.dp)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }

    private fun formatRemaining(totalSeconds: Int): String {
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%02d:%02d".format(minutes, seconds)
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetDependencies {
    fun journeyRepository(): JourneyRepository
}
