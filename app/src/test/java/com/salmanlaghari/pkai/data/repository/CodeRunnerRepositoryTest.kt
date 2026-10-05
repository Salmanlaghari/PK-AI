package com.salmanlaghari.pkai.data.repository

import com.salmanlaghari.pkai.data.local.datastore.PreferencesManager
import com.salmanlaghari.pkai.data.remote.HackerEarthApiService
import com.salmanlaghari.pkai.data.remote.HackerEarthRequestStatus
import com.salmanlaghari.pkai.data.remote.HackerEarthResult
import com.salmanlaghari.pkai.data.remote.HackerEarthRunStatus
import com.salmanlaghari.pkai.data.remote.HackerEarthSubmissionRequest
import com.salmanlaghari.pkai.data.remote.HackerEarthSubmissionResponse
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import retrofit2.Response

class CodeRunnerRepositoryTest {

    private lateinit var mockApiService: HackerEarthApiService
    private lateinit var mockPreferencesManager: PreferencesManager
    private lateinit var repository: CodeRunnerRepository

    @Before
    fun setUp() {
        mockApiService = mock(HackerEarthApiService::class.java)
        mockPreferencesManager = mock(PreferencesManager::class.java)
        repository = CodeRunnerRepository(mockApiService, mockPreferencesManager)
    }

    @Test
    fun testExecuteCodeQuotaExceeded() = runTest {
        `when`(mockPreferencesManager.getCodeRunCount()).thenReturn(1000)

        val result = repository.executeCode("print('test')", "PYTHON3_8")
        assertTrue(result is CodeExecutionResult.QuotaExceeded)
    }

    @Test
    fun testExecuteCodeSuccess() = runTest {
        `when`(mockPreferencesManager.getCodeRunCount()).thenReturn(5)

        val submitResponse = HackerEarthSubmissionResponse(
            he_id = "test-he-id",
            request_status = HackerEarthRequestStatus("REQUEST_COMPLETED", "Done"),
            result = HackerEarthResult(
                compile_status = "OK",
                run_status = HackerEarthRunStatus(
                    status = "AC",
                    output = "https://example.com/output.txt",
                    stderr = null,
                    time_used = 0.015,
                    memory_used = 128
                )
            ),
            status_update_url = "https://example.com/status",
            message = null,
            errors = null
        )

        `when`(mockApiService.submitCode(anyString() ?: "", any(HackerEarthSubmissionRequest::class.java) ?: HackerEarthSubmissionRequest("", "")))
            .thenReturn(Response.success(submitResponse))

        `when`(mockApiService.fetchOutputText(anyString() ?: ""))
            .thenReturn(Response.success("Hello World Output".toResponseBody(null)))

        val result = repository.executeCode("print('Hello')", "PYTHON3_8")
        assertTrue(result is CodeExecutionResult.Success)

        val successResult = result as CodeExecutionResult.Success
        assertEquals("Hello World Output", successResult.stdout)
        assertEquals("AC", successResult.runStatus)
        assertEquals(994, successResult.remainingQuota)
    }

    @Test
    fun testLanguageNormalization() = runTest {
        // Markdown fence labels the UI actually passes -> HackerEarth v4 codes
        assertEquals("PYTHON3", CodeRunnerRepository.resolveLanguageArgument("python"))
        assertEquals("PYTHON3", CodeRunnerRepository.resolveLanguageArgument("py"))
        assertEquals("PYTHON3", CodeRunnerRepository.resolveLanguageArgument("Python3"))
        assertEquals("PYTHON3_8", CodeRunnerRepository.resolveLanguageArgument("python3.8"))
        assertEquals("CPP17", CodeRunnerRepository.resolveLanguageArgument("c++"))
        assertEquals("CPP17", CodeRunnerRepository.resolveLanguageArgument("cpp"))
        assertEquals("JAVASCRIPT_NODE", CodeRunnerRepository.resolveLanguageArgument("js"))
        assertEquals("TYPESCRIPT", CodeRunnerRepository.resolveLanguageArgument("ts"))
        assertEquals("CSHARP", CodeRunnerRepository.resolveLanguageArgument("c#"))
        assertEquals("KOTLIN", CodeRunnerRepository.resolveLanguageArgument("kt"))
        // Already-valid v4 codes pass through untouched
        assertEquals("PYTHON3_8", CodeRunnerRepository.resolveLanguageArgument("PYTHON3_8"))
        assertEquals("CPP17", CodeRunnerRepository.resolveLanguageArgument("CPP17"))
        // python2 must NOT resolve to the removed v4 "PYTHON" code (would 400)
        assertEquals("PYTHON3", CodeRunnerRepository.resolveLanguageArgument("python2"))
        // Versioned codes the v4 set does not list map to the nearest
        // supported one — never silently reinterpreted as Python.
        assertEquals("JAVA14", CodeRunnerRepository.resolveLanguageArgument("JAVA17"))
        assertEquals("JAVA14", CodeRunnerRepository.resolveLanguageArgument("java17"))
        // Unknown fence labels resolve to null (unsupported) — they are never
        // forwarded verbatim (the backend would 400) and never silently
        // reinterpreted as another language.
        assertEquals(null, CodeRunnerRepository.resolveLanguageArgument("json"))
        assertEquals(null, CodeRunnerRepository.resolveLanguageArgument("bash"))
        assertEquals(null, CodeRunnerRepository.resolveLanguageArgument("sql"))
        assertEquals(null, CodeRunnerRepository.resolveLanguageArgument(""))
    }

    @Test
    fun testUnknownLanguageReturnsUnsupportedLanguage() = runTest {
        `when`(mockPreferencesManager.getCodeRunCount()).thenReturn(5)

        val result = repository.executeCode("{\"a\": 1}", "json")
        assertTrue(result is CodeExecutionResult.UnsupportedLanguage)
        assertEquals("json", (result as CodeExecutionResult.UnsupportedLanguage).label)
    }

    @Test
    fun testUnsupportedLanguageMapsToFriendlyMessage() = runTest {
        `when`(mockPreferencesManager.getCodeRunCount()).thenReturn(5)

        val serverError = "{\"errors\": {\"lang\": [\"Unsupported language\"]}}"
        `when`(mockApiService.submitCode(anyString() ?: "", any(HackerEarthSubmissionRequest::class.java) ?: HackerEarthSubmissionRequest("", "")))
            .thenReturn(Response.error(400, serverError.toResponseBody(null)))

        val result = repository.executeCode("print('x')", "python")
        assertTrue(result is CodeExecutionResult.Error)

        val err = result as CodeExecutionResult.Error
        // Short user-friendly message — the raw JSON body must NOT reach the user.
        assertEquals(
            "The selected language is not supported by the execution backend.",
            err.message
        )
    }

    @Test
    fun testAuthFailureMapsToFriendlyMessage() = runTest {
        `when`(mockPreferencesManager.getCodeRunCount()).thenReturn(5)

        `when`(mockApiService.submitCode(anyString() ?: "", any(HackerEarthSubmissionRequest::class.java) ?: HackerEarthSubmissionRequest("", "")))
            .thenReturn(Response.error(401, "{\"detail\": \"Invalid client secret\"}".toResponseBody(null)))

        val result = repository.executeCode("print('x')", "python")
        val err = result as CodeExecutionResult.Error
        assertEquals("Authentication with the execution service failed.", err.message)
        assertFalse("Error was: ${err.message}", err.message.contains("client secret"))
    }

    @Test
    fun testServerErrorMapsToFriendlyMessage() = runTest {
        `when`(mockPreferencesManager.getCodeRunCount()).thenReturn(5)

        `when`(mockApiService.submitCode(anyString() ?: "", any(HackerEarthSubmissionRequest::class.java) ?: HackerEarthSubmissionRequest("", "")))
            .thenReturn(Response.error(503, "Service Temporarily Unavailable".toResponseBody(null)))

        val result = repository.executeCode("print('x')", "python")
        val err = result as CodeExecutionResult.Error
        assertEquals("The execution service is temporarily unavailable. Try again later.", err.message)
    }

    @Test
    fun testHtmlErrorBodyIsStrippedAndCapped() = runTest {
        `when`(mockPreferencesManager.getCodeRunCount()).thenReturn(5)

        val htmlBody = "<html><head><title>418 I'm a teapot</title></head><body>" +
            "x".repeat(2000) + "</body></html>"
        `when`(mockApiService.submitCode(anyString() ?: "", any(HackerEarthSubmissionRequest::class.java) ?: HackerEarthSubmissionRequest("", "")))
            .thenReturn(Response.error(418, htmlBody.toResponseBody(null)))

        val result = repository.executeCode("print('x')", "python")
        val err = result as CodeExecutionResult.Error
        // No HTML tags, no 2000-char dump — a short sanitized summary only.
        assertFalse("Error was: ${err.message}", err.message.contains("<"))
        assertTrue("Error was: ${err.message}", err.message.length <= 160)
        assertTrue("Error was: ${err.message}", err.message.startsWith("Submission failed (HTTP 418)"))
    }

    @Test
    fun testExecuteCodeCompileError() = runTest {
        `when`(mockPreferencesManager.getCodeRunCount()).thenReturn(10)

        val submitResponse = HackerEarthSubmissionResponse(
            he_id = "test-he-id",
            request_status = HackerEarthRequestStatus("CODE_COMPILED", "Compiled"),
            result = HackerEarthResult(
                compile_status = "error: expected ';' before '}' token",
                run_status = HackerEarthRunStatus(
                    status = "NA",
                    output = null,
                    stderr = null,
                    time_used = 0.0,
                    memory_used = 0
                )
            ),
            status_update_url = "https://example.com/status",
            message = null,
            errors = null
        )

        `when`(mockApiService.submitCode(anyString() ?: "", any(HackerEarthSubmissionRequest::class.java) ?: HackerEarthSubmissionRequest("", "")))
            .thenReturn(Response.success(submitResponse))

        val result = repository.executeCode("int main() { syntax error }", "CPP17")
        assertTrue(result is CodeExecutionResult.CompileError)

        val compileErr = result as CodeExecutionResult.CompileError
        assertTrue(compileErr.compileErrorDetails.contains("expected ';'"))
    }
}
