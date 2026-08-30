package com.repforth.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.tooling.preview.Preview
import com.repforth.core.designsystem.theme.RepForthTheme
import com.repforth.core.model.BodyRegion
import com.repforth.core.model.BodyView

/**
 * A tappable body diagram for choosing muscle regions.
 *
 * **An enhancement, never the only way to choose.** §12 forbids relying on
 * colour alone, and a silhouette cannot express `cardiovascular system` at all.
 * Pair it with labelled chips, which are what a screen-reader user and a
 * colour-blind user actually operate; the map is for people who think in bodies
 * rather than in words.
 *
 * Geometry comes from the generated [BodyMapRegions], so the paths that are
 * drawn are the same ones that are hit-tested and the two cannot disagree.
 */
@Composable
fun BodyMap(
    view: BodyView,
    selected: Set<BodyRegion>,
    onRegionClick: (BodyRegion) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    silhouetteColor: Color = RepForthTheme.colors.track,
    regionColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    selectedColor: Color = MaterialTheme.colorScheme.primary,
) {
    // Parsed once per view rather than per frame: PathParser walks the whole
    // string, and these paths are identical on every recomposition.
    val silhouette = remember(view) { silhouetteFor(view).toPath() }
    val regions = remember(view) {
        regionsFor(view).mapNotNull { (id, d) ->
            BodyRegion.entries.firstOrNull { it.svgId == id }?.let { it to d.toPath() }
        }
    }

    Canvas(
        modifier = modifier
            .aspectRatio(BodyMapViewport.WIDTH / BodyMapViewport.HEIGHT)
            .alpha(if (enabled) 1f else 0.65f)
            .then(
                if (enabled) {
                    Modifier.pointerInput(view, regions) {
                        detectTapGestures { tap ->
                            val scale = size.width / BodyMapViewport.WIDTH
                            hitTest(regions, Offset(tap.x / scale, tap.y / scale))?.let(onRegionClick)
                        }
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        val matrix = Matrix().apply {
            val scale = size.width / BodyMapViewport.WIDTH
            scale(scale, scale)
        }

        drawPath(silhouette.scaledBy(matrix), silhouetteColor)
        regions.forEach { (region, path) ->
            drawPath(
                path = path.scaledBy(matrix),
                color = if (region in selected) selectedColor else regionColor,
            )
        }
    }
}

/**
 * The region a tap landed in, or null for a tap on bare silhouette.
 *
 * Smallest first, so a small region drawn over a larger one — the obliques
 * against the torso — wins instead of being unreachable.
 */
private fun hitTest(regions: List<Pair<BodyRegion, Path>>, point: Offset): BodyRegion? =
    regions
        .filter { (_, path) -> path.getBounds().contains(point) }
        .minByOrNull { (_, path) -> path.getBounds().let { it.width * it.height } }
        ?.first

private fun silhouetteFor(view: BodyView): String = when (view) {
    BodyView.FRONT -> BodyMapSilhouette.FRONT
    BodyView.BACK -> BodyMapSilhouette.BACK
}

private fun regionsFor(view: BodyView): Map<String, String> = when (view) {
    BodyView.FRONT -> BodyMapRegions.FRONT
    BodyView.BACK -> BodyMapRegions.BACK
}

private fun String.toPath(): Path = PathParser().parsePathString(this).toPath()

private fun Path.scaledBy(matrix: Matrix): Path =
    Path().also { copy ->
        copy.addPath(this)
        copy.transform(matrix)
    }

@Preview(widthDp = 220, heightDp = 520)
@Composable
private fun BodyMapFrontPreview() {
    RepForthTheme {
        BodyMap(
            view = BodyView.FRONT,
            selected = setOf(BodyRegion.CHEST, BodyRegion.ABS),
            onRegionClick = {},
        )
    }
}

@Preview(widthDp = 220, heightDp = 520)
@Composable
private fun BodyMapBackPreview() {
    RepForthTheme {
        BodyMap(
            view = BodyView.BACK,
            selected = setOf(BodyRegion.LATS),
            onRegionClick = {},
        )
    }
}
