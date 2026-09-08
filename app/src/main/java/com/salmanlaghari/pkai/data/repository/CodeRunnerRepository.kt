package com.salmanlaghari.pkai.data.repository

import com.salmanlaghari.pkai.BuildConfig
import com.salmanlaghari.pkai.data.local.datastore.PreferencesManager
import com.salmanlaghari.pkai.data.remote.HackerEarthApiService
import com.salmanlaghari.pkai.data.remote.HackerEarthSubmissionRequest
import kotlinx.coroutines.delay
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

sealed class CodeExecutionResult {
    data class Success(
        val stdout: String,
        val stderr: String?,
        val compileStatus: String?,
        val runStatus: String,
        val timeUsed: Double,
        val memoryUsed: Long,
        val remainingQuota: Int
    ) : CodeExecutionResult()

    data class CompileError(
        val compileErrorDetails: String
    ) : CodeExecutionResult()

    data class Error(
        val message: String
    ) : CodeExecutionResult()

    object QuotaExceeded : CodeExecutionResult()
    object NoNetwork : CodeExecutionResult()
}

@Singleton
class CodeRunnerRepository @Inject constructor(
    private val apiService: HackerEarthApiService,
    private val preferencesManager: PreferencesManager
) {
    companion object {
        const val MAX_QUOTA = 1000
    }

    suspend fun executeCode(source: String, lang: String): CodeExecutionResult {
        // 1. Check current quota usage
        val currentUsage = preferencesManager.getCodeRunCount()
        if (currentUsage >= MAX_QUOTA) {
            return CodeExecutionResult.QuotaExceeded
        }

        val secretKey = BuildConfig.HACKEREARTH_CLIENT_SECRET.ifBlank {
            "5ef5fed19a69253a5a2592b2dfac397b4494904f"
        }
        val proxyUrl = BuildConfig.CODE_RUNNER_PROXY_URL

        return try {
            if (proxyUrl.isNotBlank()) {
                executeViaProxy(proxyUrl, source, lang, currentUsage)
            } else {
                executeDirectly(secretKey, source, lang, currentUsage)
            }
        } catch (e: IOException) {
            CodeExecutionResult.NoNetwork
        } catch (e: Exception) {
            CodeExecutionResult.Error("Execution error: ${e.localizedMessage ?: "Unknown error"}")
        }
    }

    private suspend fun executeViaProxy(
        proxyUrl: String,
        source: String,
        lang: String,
        currentUsage: Int
    ): CodeExecutionResult {
        val request = HackerEarthSubmissionRequest(source = source, lang = lang)
        val response = apiService.runViaProxy(proxyUrl, request)

        if (!response.isSuccessful || response.body() == null) {
            val errCode = response.code()
            return when (errCode) {
                429 -> CodeExecutionResult.QuotaExceeded
                else -> CodeExecutionResult.Error("Proxy server returned error ($errCode).")
            }
        }

        val body = response.body()!!
        val compileStatus = body.compile_status
        if (!compileStatus.isNullOrBlank() && compileStatus != "OK" && compileStatus != "Compiling...") {
            return CodeExecutionResult.CompileError(compileStatus)
        }

        preferencesManager.incrementCodeRunCount()
        val remaining = MAX_QUOTA - (currentUsage + 1)

        return CodeExecutionResult.Success(
            stdout = body.stdout ?: "No output produced.",
            stderr = body.stderr,
            compileStatus = compileStatus,
            runStatus = body.run_status ?: "AC",
            timeUsed = body.time_used ?: 0.0,
            memoryUsed = body.memory_used ?: 0L,
            remainingQuota = remaining
        )
    }

    private suspend fun executeDirectly(
        secretKey: String,
        source: String,
        lang: String,
        currentUsage: Int
    ): CodeExecutionResult {
        val request = HackerEarthSubmissionRequest(source = source, lang = lang)
        val response = apiService.submitCode(secretKey, request)

        if (!response.isSuccessful || response.body() == null) {
            val errCode = response.code()
            if (errCode == 400) {
                val errBody = response.errorBody()?.string().orEmpty()
                if (errBody.contains("Unsupported") || errBody.contains("invalid")) {
                    return CodeExecutionResult.Error("Language '$lang' is not supported by the execution backend.")
                }
            } else if (errCode == 429) {
                return CodeExecutionResult.QuotaExceeded
            }
            return CodeExecutionResult.Error("Submission failed with HTTP code $errCode.")
        }

        val submissionBody = response.body()!!
        val heId = submissionBody.he_id
        val statusUrl = submissionBody.status_update_url

        if (heId.isNullOrBlank() || statusUrl.isNullOrBlank()) {
            return CodeExecutionResult.Error("Invalid response from compilation service.")
        }

        // Poll for completion (max 15 attempts, 1 second delay)
        var attempts = 0
        var statusResponse = submissionBody

        while (attempts < 15) {
            val statusCode = statusResponse.request_status?.code.orEmpty()
            if (statusCode == "REQUEST_COMPLETED" || statusCode == "CODE_COMPILED" || statusCode == "REQUEST_FAILED") {
                break
            }

            delay(1000)
            attempts++

            val pollRes = apiService.getSubmissionStatus(statusUrl, secretKey)
            if (pollRes.isSuccessful && pollRes.body() != null) {
                statusResponse = pollRes.body()!!
            }
        }

        val compileStatus = statusResponse.result?.compile_status
        val runStatus = statusResponse.result?.run_status

        if (!compileStatus.isNullOrBlank() && compileStatus != "OK" && compileStatus != "Compiling...") {
            return CodeExecutionResult.CompileError(compileStatus)
        }

        val outputUrl = runStatus?.output
        var stdout = ""

        if (!outputUrl.isNullOrBlank()) {
            runCatching {
                val outRes = apiService.fetchOutputText(outputUrl)
                if (outRes.isSuccessful && outRes.body() != null) {
                    stdout = outRes.body()!!.string()
                }
            }
        }

        if (stdout.isBlank() && !compileStatus.equals("OK", ignoreCase = true)) {
            if (!compileStatus.isNullOrBlank()) {
                return CodeExecutionResult.CompileError(compileStatus)
            }
        }

        preferencesManager.incrementCodeRunCount()
        val remaining = MAX_QUOTA - (currentUsage + 1)

        return CodeExecutionResult.Success(
            stdout = stdout.ifBlank { "(No stdout output produced)" },
            stderr = runStatus?.stderr,
            compileStatus = compileStatus,
            runStatus = runStatus?.status ?: "AC",
            timeUsed = runStatus?.time_used ?: 0.0,
            memoryUsed = runStatus?.memory_used ?: 0L,
            remainingQuota = remaining
        )
    }
}
