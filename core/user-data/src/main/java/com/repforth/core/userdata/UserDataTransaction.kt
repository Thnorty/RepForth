package com.repforth.core.userdata

import com.repforth.core.database.DatabaseTransaction
import javax.inject.Inject

/**
 * Several repository writes as one, or none of them.
 *
 * The repositories here are each transactional on their own, which is the right
 * grain for everything the app does *except* import: that replaces the profile,
 * the plans, the weeks and the whole history through four of them, and a failure
 * partway through used to leave the earlier replacements standing over data the
 * file was meant to supersede. There is no server and no second copy, so half of
 * somebody else's export is not a state a phone may be left in.
 *
 * Part of `core:user-data`'s public surface for the same reason the repositories
 * are: this module is the one door to user data, and a caller that needs two
 * writes to be one write should not have to reach past it to Room to say so.
 */
interface UserDataTransaction {

    /**
     * Runs [block], committing only if it returns normally.
     *
     * A throw rolls back and is rethrown. The caller decides what to say about
     * it — swallowing it here would report success for a write that did not
     * happen, which is the failure this exists to prevent.
     */
    suspend fun <R> run(block: suspend () -> R): R
}

internal class RoomUserDataTransaction @Inject constructor(
    private val database: DatabaseTransaction,
) : UserDataTransaction {

    override suspend fun <R> run(block: suspend () -> R): R = database.run(block)
}
