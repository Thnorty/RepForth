package com.repforth.core.media

import com.repforth.core.media.manifest.MediaManifestRepository
import com.repforth.core.model.ExerciseId
import com.repforth.core.model.MediaRef
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves media references for exercises (§6, §9).
 *
 * In `placeholder` builds, resolves to [MediaRef.Unavailable] so no network requests are issued.
 * In `licensed` builds, resolves against the media manifest.
 */
interface MediaResolver {
    suspend fun resolveThumbnail(exerciseId: ExerciseId): MediaRef
    suspend fun resolveAnimation(exerciseId: ExerciseId): MediaRef
}

/**
 * Default resolver used in placeholder builds: no network requests.
 */
@Singleton
class PlaceholderMediaResolver @Inject constructor() : MediaResolver {
    override suspend fun resolveThumbnail(exerciseId: ExerciseId): MediaRef = MediaRef.Unavailable
    override suspend fun resolveAnimation(exerciseId: ExerciseId): MediaRef = MediaRef.Unavailable
}

/**
 * Manifest-backed resolver used when the licensed manifest is present.
 */
@Singleton
class ManifestMediaResolver @Inject constructor(
    private val manifestRepository: MediaManifestRepository,
) : MediaResolver {
    override suspend fun resolveThumbnail(exerciseId: ExerciseId): MediaRef =
        manifestRepository.findThumbnail(exerciseId)

    override suspend fun resolveAnimation(exerciseId: ExerciseId): MediaRef =
        manifestRepository.findAnimation(exerciseId)
}
