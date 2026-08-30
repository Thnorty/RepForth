package com.repforth.core.testing

import com.repforth.core.secrets.SecretId
import com.repforth.core.secrets.SecretStore

/**
 * A [SecretStore] that keeps secrets in a map.
 *
 * The real one needs the Android Keystore, so anything above it can only be
 * unit-tested against something like this. That is a deliberate trade and it is
 * worth naming what it gives up: this proves callers use the store correctly,
 * never that the store protects anything. The protection is proved by
 * `KeystoreSecretStoreTest`, on a device.
 *
 * [contains] deliberately does not decrypt, matching the real contract — a
 * caller that uses `get() != null` where `contains()` would do is doing more
 * work than it needs to, and this store will not tell them so.
 */
class InMemorySecretStore : SecretStore {
    private val secrets = mutableMapOf<String, String>()

    /** What a test can inspect afterwards, without going through [get]. */
    val storedIds: Set<String> get() = secrets.keys.toSet()

    override suspend fun put(id: SecretId, secret: String) {
        secrets[id.value] = secret
    }

    override suspend fun get(id: SecretId): String? = secrets[id.value]

    override suspend fun contains(id: SecretId): Boolean = secrets.containsKey(id.value)

    override suspend fun delete(id: SecretId) {
        secrets.remove(id.value)
    }

    override suspend fun clear() {
        secrets.clear()
    }
}
