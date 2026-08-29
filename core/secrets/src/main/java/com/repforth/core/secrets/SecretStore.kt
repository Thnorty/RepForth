package com.repforth.core.secrets

/**
 * Names one stored secret.
 *
 * A value class rather than a `String` because every method here takes one, and
 * a bare string parameter next to a bare string secret is an argument order
 * waiting to be got wrong — in the direction that writes an API key into a slot
 * name.
 */
@JvmInline
value class SecretId(val value: String)

/**
 * Where user-supplied provider secrets live (§8, §20).
 *
 * An interface for one reason that matters: the real implementation needs the
 * Android Keystore, which does not exist on the JVM. Everything above this can
 * be unit-tested against a fake, and the implementation itself is proved by
 * instrumentation tests on a device.
 *
 * The contract deliberately has no "list everything" and no way to read a
 * secret back into a UI. §8 requires a key to be masked and never shown again
 * in full, so the only legitimate reader is the code that builds an outbound
 * request.
 */
interface SecretStore {

    /** Stores [secret] under [id], replacing anything already there. */
    suspend fun put(id: SecretId, secret: String)

    /** The secret, or null when none is stored or it could not be decrypted. */
    suspend fun get(id: SecretId): String?

    /** True when something is stored, without decrypting it. */
    suspend fun contains(id: SecretId): Boolean

    /** Removes one secret. Absent is not an error. */
    suspend fun delete(id: SecretId)

    /** Removes every secret this store holds (§8: "delete all provider settings"). */
    suspend fun clear()
}
