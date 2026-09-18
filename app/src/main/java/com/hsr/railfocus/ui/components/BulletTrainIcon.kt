package com.hsr.railfocus.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import com.hsr.railfocus.R

/**
 * App-wide bullet train icon.
 *
 * Uses the custom [R.drawable.ic_bullet_train] vector asset and supports tinting
 * and sizing the same way as Material Icon composable.
 */
@Composable
fun BulletTrainIcon(
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    tint: Color = Color.Unspecified,
    size: Dp = Dp.Unspecified,
) {
    val sizedModifier = if (size != Dp.Unspecified) modifier.size(size) else modifier
    Image(
        painter = painterResource(id = R.drawable.ic_bullet_train),
        contentDescription = contentDescription,
        modifier = sizedModifier,
        colorFilter = if (tint != Color.Unspecified) ColorFilter.tint(tint) else null,
    )
}
