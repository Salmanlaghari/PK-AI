package com.salmanlaghari.pkai.data.repository

import com.salmanlaghari.pkai.BuildConfig
import com.salmanlaghari.pkai.data.local.datastore.PreferencesManager
import com.salmanlaghari.pkai.data.remote.HackerEarthApiService
import com.salmanlaghari.pkai.data.remote.HackerEarthSubmissionRequest
import com.salmanlaghari.pkai.data.remote.HackerEarthSubmissionResponse
import kotlinx.coroutines.delay
import retrofit2.Response
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

        /**
         * HackerEarth API v4 `lang` argument values
         * (https://www.hackerearth.com/docs/wiki/developers/v4/).
         */
        private val HACKEREARTH_LANGS = setOf(
            "C", "CPP14", "CPP17", "CLOJURE", "CSHARP", "GO", "HASKELL",
            "JAVA8", "JAVA14", "JAVASCRIPT_NODE", "KOTLIN", "OBJECTIVEC",
            "PASCAL", "PERL", "PHP", "PYTHON3", "PYTHON3_8",
            "R", "RUBY", "RUST", "SCALA", "SWIFT", "TYPESCRIPT"
        )

        /**
         * Markdown fence labels / common aliases → v4 lang argument.
         * Keys are normalized (uppercase, punctuation folded).
         */
        private val LANGUAGE_ALIASES = mapOf(
            // Python family
            "PY" to "PYTHON3",
            "PYTHON" to "PYTHON3", // "python" almost always means Python 3 (Python 2 is EOL)
            "PYTHON2" to "PYTHON",
            "PY3" to "PYTHON3",
            "PYTHON38" to "PYTHON3_8",
            "PY38" to "PYTHON3_8",
            // C++ family
            "CPP" to "CPP17",
            "CXX" to "CPP17",
            "GPP" to "CPP17",
            "CPP11" to "CPP14",
            // Java family
            "JAVA" to "JAVA8",
            // JavaScript / TypeScript
            "JS" to "JAVASCRIPT_NODE",
            "NODE" to "JAVASCRIPT_NODE",
            "NODEJS" to "JAVASCRIPT_NODE",
            "TS" to "TYPESCRIPT",
            // Others
            "CS" to "CSHARP",
            "KT" to "KOTLIN",
            "RB" to "RUBY",
            "RS" to "RUST",
            "CLJ" to "CLOJURE",
            "HS" to "HASKELL",
            "PL" to "PERL",
            "PAS" to "PASCAL",
            "OBJC" to "OBJECTIVEC",
            "OBJECTIVE_C" to "OBJECTIVEC"
        )

        /**
         * Resolves whatever language label the UI passes (markdown fence labels
         * like `python`, `c++`, `js`, or already-valid v4 codes like
         * `PYTHON3_8`, `CPP17`, `JAVA17`) to a HackerEarth API v4 `lang`
         * argument.
         *
         * This is the root cause of "python nahin chal raha": code blocks carry
         * lowercase fence labels (`python`) which the v4 API rejects with HTTP
         * 400 "Unsupported language".
         */
        fun resolveLanguageArgument(lang: String): String {
            val raw = lang.trim()
            if (raw.isEmpty()) return "PYTHON3"
            // Already a valid v4 code — pass through untouched.
            if (raw in HACKEREARTH_LANGS) return raw
            val normalized = raw.uppercase()
                .replace("+", "P") // C++ -> CPP
                .replace("#", "SHARP") // C# -> CSHARP
                .replace(".", "_")
                .replace("-", "_")
                .replace(" ", "_")
            if (normalized in HACKEREARTH_LANGS) return normalized
            LANGUAGE_ALIASES[normalized]?.let { return it }
            // Looks like a versioned v4 code (e.g. JAVA17) — trust it and let
            // the server return the real error if it isn't supported.
            if (normalized.matches(Regex("[A-Z][A-Z0-9_]*"))) return normalized
            return "PYTHON3"
        }
    }

    suspend fun executeCode(source: String, lang: String): CodeExecutionResult {
        // 1. Check current quota usage
        val currentUsage = preferencesManager.getCodeRunCount()
        if (currentUsage >= MAX_QUOTA) {
            return CodeExecutionResult.QuotaExceeded
        }

        val secretKey = BuildConfig.HACKEREARTH_CLIENT_SECRET.ifBlank { "fallback_test_secret" }
        val proxyUrl = BuildConfig.CODE_RUNNER_PROXY_URL
        // The UI passes markdown fence labels ("python", "c++"); the v4 API only
        // accepts its own uppercase lang arguments ("PYTHON3", "CPP17", ...).
        val resolvedLang = resolveLanguageArgument(lang)

        return try {
            if (proxyUrl.isNotBlank()) {
                executeViaProxy(proxyUrl, source, resolvedLang, currentUsage)
            } else {
                executeDirectly(secretKey, source, resolvedLang, currentUsage)
            }
        } catch (e: IOException) {
            CodeExecutionResult.NoNetwork
        } catch (e: Exception) {
            CodeExecutionResult.Error("Execution error: ${e.localizedMessage ?: "Unknown error"}")
        }
    }

    /** Reads the raw error body so the REAL API error message can be surfaced. Never throws. */
    private fun <T> readErrorBody(response: Response<T>): String =
        runCatching { response.errorBody()?.string().orEmpty().trim() }
            .getOrDefault("")
            .take(500)

    /** Builds a readable message from a REQUEST_FAILED submission response. */
    private fun requestFailedMessage(body: HackerEarthSubmissionResponse): String {
        val detail = body.message
            ?: body.request_status?.message
            ?: body.errors?.values?.joinToString("; ") { it.toString() }
        return if (!detail.isNullOrBlank()) "Execution request failed: $detail"
        else "Execution request failed (no details returned by the backend)."
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
            if (errCode == 429) {
                return CodeExecutionResult.QuotaExceeded
            }
            val serverMessage = readErrorBody(response)
            return CodeExecutionResult.Error(
                buildString {
                    append("Proxy server returned error ($errCode)")
                    if (serverMessage.isNotBlank()) append(": $serverMessage")
                }
            )
        }

        val body = response.body()!!
        // Surface the real backend error instead of silently failing.
        val proxyError = body.error?.toString()?.takeIf { it.isNotBlank() && it != "null" }
        if (proxyError != null) {
            return CodeExecutionResult.Error("Execution failed: $proxyError")
        }

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
            if (errCode == 429) {
                return CodeExecutionResult.QuotaExceeded
            }
            // Surface the REAL API error message instead of a bare HTTP code.
            val serverMessage = readErrorBody(response)
            if (errCode == 400 && serverMessage.contains("Unsupported", ignoreCase = true)) {
                val detail = serverMessage.ifBlank { "no details" }
                return CodeExecutionResult.Error(
                    "Language '$lang' is not supported by the execution backend ($detail)."
                )
            }
            return CodeExecutionResult.Error(
                buildString {
                    append("Submission failed (HTTP $errCode)")
                    if (serverMessage.isNotBlank()) append(": $serverMessage")
                }
            )
        }

        val submissionBody = response.body()!!

        // HTTP 200 with a failed request (e.g. bad client-secret) — surface it.
        if (submissionBody.request_status?.code == "REQUEST_FAILED") {
            return CodeExecutionResult.Error(requestFailedMessage(submissionBody))
        }

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

        if (statusResponse.request_status?.code == "REQUEST_FAILED") {
            return CodeExecutionResult.Error(requestFailedMessage(statusResponse))
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
