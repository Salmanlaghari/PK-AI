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
