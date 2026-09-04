package com.repforth.core.ai

import kotlinx.coroutines.flow.Flow

/**
 * Whether Coach has a provider to talk to.
 *
 * One question, as an interface, because it is the only thing outside this
 * module that needs asking and [ProviderRepository] is a concrete class holding
 * a [com.repforth.core.secrets.SecretStore] — a screen's view model should not
 * have to construct one of those to be tested, and a test should not have to
 * own a keystore to say "no provider yet".
 *
 * Bound to [ProviderRepository] in `AiGenerationModule`, the same public seam
 * that exists so the generator itself can be replaced on a test device.
 */
interface ProviderAvailability {

    /**
     * True when a request would reach somewhere.
     *
     * Exactly the condition `ProviderRepository.configFor` answers null to. A
     * local server needs no key, so this is not the same question as "is a key
     * stored".
     */
    val configured: Flow<Boolean>
}
