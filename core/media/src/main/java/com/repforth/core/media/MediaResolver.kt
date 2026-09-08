package com.repforth.core.media

import com.repforth.core.media.manifest.MediaManifestRepository
import com.repforth.core.model.ExerciseId
import com.repforth.core.model.MediaRef
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves media references for exercises (§6, §9).
 *
 * **One implementation, in every flavour.** This interface used to have two, and
 * the second — `PlaceholderMediaResolver`, which answered [MediaRef.Unavailable]
 * to everything — was bound nowhere: `MediaModule` binds [ManifestMediaResolver]
 * unconditionally and no source reads the `media` flavour at run time. It has
 * been deleted rather than wired up, because the owner has decided that every
 * build fetches the upstream media (§6). A class whose only job was to embody
 * the opposite rule is the artefact that made the opposite rule believable.
 *
 * [MediaRef.Unavailable] is still reachable: it is what the manifest answers for
 * an exercise it has no entry for.
 */
interface MediaResolver {
    suspend fun resolveThumbnail(exerciseId: ExerciseId): MediaRef
    suspend fun resolveAnimation(exerciseId: ExerciseId): MediaRef
}

/**
 * The resolver, backed by the bundled manifest.
 *
 * Bundled in `main`, so it is present in all four variants — see §18 on why the
 * flavour names do not mean what they look like.
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
