package com.hsr.railfocus.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hsr.railfocus.R
import com.hsr.railfocus.data.repository.AppUpdateInfo

/**
 * 发现新版本时的更新提示对话框：显示版本号与更新说明，可前往下载或稍后处理。
 * 调用方负责记录用户已忽略的版本，避免重复弹窗。
 */
@Composable
fun UpdateAvailableDialog(
    info: AppUpdateInfo,
    onDismissed: () -> Unit,
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismissed,
        title = {
            Text(stringResource(R.string.settings_update_available_title, info.version))
        },
        text = {
            if (info.releaseNotes.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = stringResource(R.string.settings_update_release_notes),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = info.releaseNotes,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val url = info.downloadUrl ?: info.releaseUrl
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    onDismissed()
                },
            ) {
                Text(stringResource(R.string.settings_update_download))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissed) {
                Text(stringResource(R.string.settings_update_later))
            }
        },
    )
}
