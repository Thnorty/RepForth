package com.repforth.feature.body

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.repforth.core.designsystem.theme.Radius
import java.io.File

/** Portrait, as progress photos are taken. */
internal const val PHOTO_ASPECT = 3f / 4f

/**
 * A stored progress photo, or the empty frame one would sit in.
 *
 * Read from the app's private folder, never the network. A file that is
 * missing — swept, or a test with no files — shows the frame.
 */
@Composable
internal fun PhotoThumb(file: File?, contentDescription: String?, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .aspectRatio(PHOTO_ASPECT)
            .clip(RoundedCornerShape(Radius.media))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        if (file != null) {
            AsyncImage(
                model = file,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}
