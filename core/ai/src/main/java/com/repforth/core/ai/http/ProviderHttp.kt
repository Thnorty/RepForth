package com.repforth.core.ai.http

import com.repforth.core.ai.ProviderFailure
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
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

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
 * Deliberately coarse. "Could not resolve host" and "TLS handshake failed" are
 * different to a developer and identical to someone standing in a gym with no
 * signal: the app could not reach the provider.
 */
internal fun Throwable.toProviderFailure(): ProviderFailure = when (this) {
    // InterruptedIOException covers OkHttp's own call timeout, which is not a
    // SocketTimeoutException and would otherwise fall through to NETWORK with a
    // message nobody could act on.
    is SocketTimeoutException, is InterruptedIOException -> ProviderFailure.NETWORK
    is UnknownHostException, is SSLException, is IOException -> ProviderFailure.NETWORK
    else -> ProviderFailure.NETWORK
}

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
