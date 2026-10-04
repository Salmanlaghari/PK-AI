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
        assertEquals("JAVA17", CodeRunnerRepository.resolveLanguageArgument("JAVA17"))
        assertEquals("PYTHON3", CodeRunnerRepository.resolveLanguageArgument(""))
    }

    @Test
    fun testSurfacesRealApiErrorOnFailure() = runTest {
        `when`(mockPreferencesManager.getCodeRunCount()).thenReturn(5)

        val serverError = "{\"errors\": {\"lang\": [\"Unsupported language\"]}}"
        `when`(mockApiService.submitCode(anyString() ?: "", any(HackerEarthSubmissionRequest::class.java) ?: HackerEarthSubmissionRequest("", "")))
            .thenReturn(Response.error(400, serverError.toResponseBody(null)))

        val result = repository.executeCode("print('x')", "python")
        assertTrue(result is CodeExecutionResult.Error)

        val err = result as CodeExecutionResult.Error
        // The real backend message must reach the user — not just "HTTP code 400".
        assertTrue("Error was: ${err.message}", err.message.contains("Unsupported language"))
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
