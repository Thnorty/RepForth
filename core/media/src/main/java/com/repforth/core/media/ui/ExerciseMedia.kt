package com.repforth.core.media.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.repforth.core.designsystem.component.RfIcons
import com.repforth.core.designsystem.theme.Radius
import com.repforth.core.model.MediaRef

enum class ExerciseMediaSize(val dp: Dp, val cornerRadius: Dp) {
    SMALL(48.dp, 8.dp),                 // List item row
    MEDIUM(72.dp, Radius.media),        // Workout card row
    FLUSH(Dp.Unspecified, Radius.media), // Full width / stacked
}

/**
 * Renders 1:1 exercise media strictly without blurring or backdrop distortion (§9).
 *
 * Falls back to placeholder icon [RfIcons.fitnessCenter] when media is unavailable
 * or while loading.
 */
@Composable
fun ExerciseMedia(
    mediaRef: MediaRef,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: ExerciseMediaSize = ExerciseMediaSize.SMALL,
    iconSize: Dp = if (size == ExerciseMediaSize.SMALL) 24.dp else 32.dp,
) {
    val shape = RoundedCornerShape(size.cornerRadius)
    val sizeModifier = if (size == ExerciseMediaSize.FLUSH) {
        modifier
            .fillMaxSize()
            .aspectRatio(1f)
    } else {
        modifier.size(size.dp)
    }

    Box(
        modifier = sizeModifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (mediaRef.isAvailable && mediaRef.url != null) {
            val context = LocalContext.current
            val request = ImageRequest.Builder(context)
                .data(mediaRef.url)
                .crossfade(true)
                .build()

            AsyncImage(
                model = request,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                painter = RfIcons.Exercises,
                contentDescription = contentDescription,
                modifier = Modifier.size(iconSize),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
