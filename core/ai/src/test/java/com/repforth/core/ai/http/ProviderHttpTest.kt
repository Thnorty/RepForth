package com.repforth.core.ai.http

import com.repforth.core.ai.ProviderFailure
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import org.junit.Assert.assertEquals
import org.junit.Test

class ProviderHttpTest {

    @Test
    fun `socket and call deadlines are reported as timeouts`() {
        assertEquals(ProviderFailure.TIMEOUT, SocketTimeoutException().toProviderFailure())
        assertEquals(ProviderFailure.TIMEOUT, InterruptedIOException("timeout").toProviderFailure())
    }

    @Test
    fun `connection failures remain network failures`() {
        listOf(
            UnknownHostException(),
            SSLException("handshake"),
            IOException("connection refused"),
        ).forEach { cause ->
            assertEquals(ProviderFailure.NETWORK, cause.toProviderFailure())
        }
    }
}
