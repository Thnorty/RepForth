package com.repforth.core.ai.http

import com.repforth.core.ai.ProviderFailure
import com.repforth.core.model.ProviderSettings
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

internal val PROVIDER_JSON_MEDIA_TYPE: MediaType = "application/json; charset=utf-8".toMediaType()

/** Joins an endpoint without imposing an address policy that §8 removed. */
internal fun providerEndpoint(baseUrl: String, relativePath: String): String =
    baseUrl.trimEnd('/') + "/" + relativePath.trimStart('/')

/**
 * What a provider adapter got back: the status, and the body as text.
 *
 * The body is read into a string here rather than handed on as a stream,
 * because both adapters want the whole thing and a `Response` that escapes this
 * file is a `Response` somebody forgets to close.
 */
internal class HttpReply(val code: Int, val body: String)

/**
 * The HTTP the provider adapters share (§8).
 *
 * §8: "Do not depend on a vendor SDK in domain code. Direct HTTP clients make
 * dynamic endpoints, local model servers, and consistent cancellation/error
 * handling easier." This is that client, and it centralises the two things that
 * would otherwise be written twice and differently: the timeout, and the mapping
 * from a failure to a category the user can act on.
 *
 * **The address is not inspected.** §8 was amended deliberately: whatever the
 * user typed is what gets sent, over whatever scheme they typed. There is no
 * allowlist and no scheme check, here or anywhere else — if the server answers,
 * that is the answer. The cost is stated in the guideline: a key sent over
 * `http://` is readable in transit, and nothing in the app will say so.
 */
internal class ProviderHttp(private val client: OkHttpClient) {

    /**
     * Sends [request], or explains why it was not sent.
     *
     * Returns a failure rather than throwing for anything a user can cause.
     * Pressing "Test connection" on a plane is not exceptional.
     */
    suspend fun send(
        request: Request,
        timeoutSeconds: Int,
    ): Result<HttpReply> {
        // A per-call timeout on a shared client. `newBuilder` copies the
        // configuration but keeps the connection pool and dispatcher, so this
        // is not a new client per request — which is the usual way this goes
        // wrong, and shows up as sockets that are never reused.
        val scoped = client.newBuilder()
            .callTimeout(timeoutSeconds.toLong(), TimeUnit.SECONDS)
            .build()

        return runCatching { scoped.newCall(request).await() }
    }

    private companion object {
        /**
         * Bridges OkHttp's callback API to a coroutine, cancelling the call
         * when the coroutine is cancelled.
         *
         * `execute()` on a dispatcher would work and would leak: a cancelled
         * coroutine leaves the thread blocked on a socket until the timeout
         * expires, which for a 60-second generation request is a minute of a
         * dispatcher thread doing nothing for a screen the user has left.
         */
        suspend fun Call.await(): HttpReply = suspendCancellableCoroutine { continuation ->
            enqueue(
                object : Callback {
                    override fun onResponse(call: Call, response: Response) {
                        response.use {
                            continuation.resume(
                                HttpReply(it.code, it.body.string()),
                            )
                        }
                    }

                    override fun onFailure(call: Call, e: IOException) {
                        if (continuation.isActive) continuation.resumeWithException(e)
                    }
                },
            )
            continuation.invokeOnCancellation { cancel() }
        }
    }
}

/**
 * Turns a thrown failure into a category the user can act on.
 *
 * Connection failures stay deliberately coarse, but a deadline is separate:
 * Coach gives a timeout its own retryable message rather than blaming the
 * user's connection.
 */
internal fun Throwable.toProviderFailure(): ProviderFailure = when (this) {
    // InterruptedIOException covers OkHttp's own call timeout, which is not a
    // SocketTimeoutException and would otherwise fall through to NETWORK.
    is SocketTimeoutException, is InterruptedIOException -> ProviderFailure.TIMEOUT
    is UnknownHostException, is SSLException, is IOException -> ProviderFailure.NETWORK
    else -> ProviderFailure.NETWORK
}

/**
 * The deadline for one generation, scaled by how much is being asked for.
 *
 * The configured timeout is a sensible budget for *one* workout, which is what
 * it was chosen for when a request could only ever produce one. A seven-day
 * week is roughly seven times the structured output, and a model that is still
 * writing day five when the clock runs out is not a provider that failed — it
 * is a deadline that never accounted for the request getting bigger.
 *
 * Linear in days for that reason, and clamped to [ProviderSettings.MAX_TIMEOUT_SECONDS]
 * so this can never exceed a limit the user could set by hand. The user still
 * controls the base value, and the generation card can still be cancelled.
 */
internal fun generationTimeoutSeconds(baseSeconds: Int, days: Int): Int =
    (baseSeconds.toLong() * days.coerceAtLeast(1))
        .coerceAtMost(ProviderSettings.MAX_TIMEOUT_SECONDS.toLong())
        .toInt()

/**
 * The HTTP status codes, mapped to what the user should do about them.
 *
 * 429 is separated from the other 4xx on purpose: a rate limit is not a mistake
 * the user made in this app, and telling them to check their key when their
 * account is simply busy sends them to change something that was correct.
 */
internal fun failureForStatus(code: Int): ProviderFailure = when {
    code == 401 || code == 403 -> ProviderFailure.AUTHENTICATION
    code == 429 -> ProviderFailure.QUOTA
    code == 402 -> ProviderFailure.QUOTA
    code in 500..599 -> ProviderFailure.SERVER
    else -> ProviderFailure.FORMAT
}
