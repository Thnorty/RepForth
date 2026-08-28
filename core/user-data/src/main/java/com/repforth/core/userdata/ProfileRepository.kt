package com.repforth.core.userdata

import com.repforth.core.model.UserProfile
import kotlinx.coroutines.flow.Flow

/**
 * The user's training constraints (§3, §7).
 *
 * An interface because the rules engine is pure and must be testable against a
 * profile that never touched a database, and because §8's AI validator needs the
 * same constraints without caring where they came from.
 */
interface ProfileRepository {

    /** The profile, or null until onboarding completes. */
    fun observeProfile(): Flow<UserProfile?>

    suspend fun getProfile(): UserProfile?

    /** Writes the profile and its membership sets atomically. */
    suspend fun save(profile: UserProfile)

    /** "Reset app" (§7). Removes the profile; the bundled catalog is untouched. */
    suspend fun deleteAll()
}
