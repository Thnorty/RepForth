package com.repforth.core.database

import androidx.room.withTransaction
import javax.inject.Inject

/**
 * Runs several writes as one, or none of them.
 *
 * Every DAO in this module is already transactional for the rows it owns, which
 * is enough for every ordinary operation: saving a plan writes its exercises
 * with it, saving a week writes its days. Import is the one thing that is not
 * ordinary. It replaces the profile, the standalone plans, the weekly plans and
 * the whole history, through four repositories that know nothing about each
 * other — and a failure partway through left the earlier replacements applied
 * over data the file was supposed to supersede.
 *
 * There is no server and no second copy, so "half of somebody else's export"
 * is not a state the app may leave a phone in.
 *
 * A class rather than an extension so it can be injected and faked. It lives in
 * this module because this module owns the database; `core:user-data` wraps it
 * in [com.repforth.core.userdata.UserDataTransaction] so that nothing outside
 * that module has to know Room exists.
 */
class DatabaseTransaction @Inject constructor(
    private val database: RepForthDatabase,
) {

    /**
     * Runs [block] inside a single database transaction.
     *
     * Room's suspending `withTransaction` rather than `runInTransaction`: the
     * repositories this wraps are all `suspend`, and the blocking form would
     * confine them to the transaction's own thread and deadlock the moment one
     * of them suspended.
     *
     * A throw rolls everything back and is rethrown, so the caller decides what
     * to tell the user; swallowing it here would report success for a write
     * that did not happen.
     */
    suspend fun <R> run(block: suspend () -> R): R = database.withTransaction(block)
}
