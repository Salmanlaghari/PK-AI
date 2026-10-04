package com.salmanlaghari.pkai.data.repository

import com.salmanlaghari.pkai.BuildConfig
import com.salmanlaghari.pkai.data.local.datastore.PreferencesManager
import com.salmanlaghari.pkai.data.remote.HackerEarthApiService
import com.salmanlaghari.pkai.data.remote.HackerEarthSubmissionRequest
import com.salmanlaghari.pkai.data.remote.HackerEarthSubmissionResponse
import android.util.Log
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
        private const val TAG = "CodeRunnerRepository"

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
            "PYTHON2" to "PYTHON3", // Python 2 is EOL; v4 has no PYTHON code at all
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
         * `PYTHON3_8` or `CPP17`) to a HackerEarth API v4 `lang`
         * argument.
         *
         * Only labels in the known v4 set ([HACKEREARTH_LANGS]) or the defined
         * [LANGUAGE_ALIASES] are ever forwarded. Anything else (unknown fence
         * labels like `json`, `bash`, `sql`...) falls back to PYTHON3 — never
         * forwarded verbatim, because the v4 API answers unsupported
         * languages with HTTP 400.
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
            // Unknown label (json, yaml, bash, sql, ...) — fall back to the
            // default instead of sending a value the backend will 400 on.
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

    /** Reads the raw error body for local diagnostics only. Never user-visible as-is. Never throws. */
    private fun <T> readErrorBody(response: Response<T>): String =
        runCatching { response.errorBody()?.string().orEmpty().trim() }
            .getOrDefault("")

    /**
     * Strips HTML tags, collapses whitespace and caps length before any backend
     * text reaches the UI — a raw HTML/JSON error body must never be shown to
     * the user.
     */
    private fun sanitizedUserDetail(rawBody: String): String =
        rawBody
            .replace(Regex("<[^>]*>"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(120)

    /**
     * Maps an HTTP failure to a short user-friendly message. The full raw body
     * is logged locally for diagnostics; the user only ever sees the sanitized
     * summary. Never throws (android.util.Log is unavailable on the plain JVM
     * used by unit tests, hence the guard).
     */
    private fun httpErrorMessage(errCode: Int, rawBody: String): String {
        try {
            Log.w(TAG, "Execution backend failed (HTTP $errCode): ${rawBody.take(1000)}")
        } catch (t: Throwable) {
            println("CodeRunnerRepository: execution backend failed (HTTP $errCode)")
        }
        return when (errCode) {
            400 -> if (sanitizedUserDetail(rawBody).contains("unsupported", ignoreCase = true)) {
                "The selected language is not supported by the execution backend."
            } else {
                "The code could not be submitted. Please check the language and try again."
            }
            401, 403 -> "Authentication with the execution service failed."
            in 500..599 -> "The execution service is temporarily unavailable. Try again later."
            else -> buildString {
                append("Submission failed (HTTP $errCode)")
                val detail = sanitizedUserDetail(rawBody)
                if (detail.isNotBlank()) append(": $detail")
            }
        }
    }

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
            return CodeExecutionResult.Error(httpErrorMessage(errCode, serverMessage))
        }

        val body = response.body()!!
        // Surface a sanitized summary of the backend error instead of silently failing.
        val proxyError = body.error?.toString()?.takeIf { it.isNotBlank() && it != "null" }
        if (proxyError != null) {
            return CodeExecutionResult.Error("Execution failed: ${sanitizedUserDetail(proxyError)}")
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
            return CodeExecutionResult.Error(httpErrorMessage(errCode, readErrorBody(response)))
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
