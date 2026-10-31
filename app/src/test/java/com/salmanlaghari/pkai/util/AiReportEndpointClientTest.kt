package com.salmanlaghari.pkai.util

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiReportEndpointClientTest {
    @Test
    fun `only HTTPS endpoints with hosts are accepted`() {
        assertTrue(AiReportEndpointClient.isHttpsEndpoint("https://script.google.com/macros/s/id/exec"))
        assertFalse(AiReportEndpointClient.isHttpsEndpoint("http://script.google.com/macros/s/id/exec"))
        assertFalse(AiReportEndpointClient.isHttpsEndpoint("https://example.com/report"))
        assertFalse(AiReportEndpointClient.isHttpsEndpoint("not a URL"))
        assertFalse(AiReportEndpointClient.isHttpsEndpoint("https:///missing-host"))
    }

    @Test
    fun `successful JSON response counts as sent and request is JSON POST`() = runBlocking {
        var observedMethod = ""
        var observedContentType = ""
        var observedBody = ""
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            observedMethod = request.method
            observedContentType = request.body?.contentType()?.toString().orEmpty()
            val buffer = Buffer()
            request.body?.writeTo(buffer)
            observedBody = buffer.readUtf8()
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("""{"ok":true}""".toResponseBody("application/json".toMediaType()))
                .build()
        }.build()

        val result = AiReportEndpointClient(
            "https://script.google.com/macros/s/test/exec",
            http
        ).submit("""{"version":1,"reason":"Other"}""")

        assertEquals(ReportSubmissionResult.Sent, result)
        assertEquals("POST", observedMethod)
        assertTrue(observedContentType.startsWith("application/json"))
        assertEquals("""{"version":1,"reason":"Other"}""", observedBody)
    }

    @Test
    fun `server rejection does not claim report was sent`() = runBlocking {
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("""{"ok":false,"code":"RATE_LIMITED"}""".toResponseBody("application/json".toMediaType()))
                .build()
        }.build()

        val result = AiReportEndpointClient(
            "https://script.google.com/macros/s/test/exec",
            http
        ).submit("{}")

        assertEquals(ReportSubmissionResult.Failed("RATE_LIMITED"), result)
    }

    @Test
    fun `unconfigured or invalid endpoint fails without a network request`() = runBlocking {
        assertEquals(
            ReportSubmissionResult.Failed("NOT_CONFIGURED"),
            AiReportEndpointClient("").submit("{}")
        )
        assertEquals(
            ReportSubmissionResult.Failed("NOT_CONFIGURED"),
            AiReportEndpointClient("http://example.com/report").submit("{}")
        )
        assertEquals(
            ReportSubmissionResult.Failed("NOT_CONFIGURED"),
            AiReportEndpointClient("https://example.com/report").submit("{}")
        )
    }

    @Test
    fun `oversized payload is rejected locally`() = runBlocking {
        assertEquals(
            ReportSubmissionResult.Failed("INVALID_PAYLOAD"),
            AiReportEndpointClient("https://script.google.com/macros/s/test/exec")
                .submit("x".repeat(8_193))
        )
    }
}
