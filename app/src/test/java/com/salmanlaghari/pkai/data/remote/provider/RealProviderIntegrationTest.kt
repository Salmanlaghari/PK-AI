package com.salmanlaghari.pkai.data.remote.provider

import com.salmanlaghari.pkai.data.model.FreeAiModel
import com.salmanlaghari.pkai.data.model.LlmProvider
import com.salmanlaghari.pkai.data.remote.PublicFreeApiService
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.mockito.Mockito.mock
import java.util.concurrent.TimeUnit

/**
 * Live end-to-end verification of every AI backend PK AI ships with.
 *
 * Run by `.github/workflows/verify-apis.yml`, which injects the real API keys from repository
 * secrets into BuildConfig. For each backend this asserts the request reaches the provider,
 * returns 2xx and yields non-blank text.
 *
 * The test collects **all** results before failing so a single bad key never hides the status
 * of the others — the full report is printed and uploaded as a CI artifact.
 */
class RealProviderIntegrationTest {

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .build()

    private val prompt = "Reply in one short sentence: who are you?"

    private data class ProbeResult(
        val name: String,
        val detail: String,
        val model: String,
        val ok: Boolean,
        /** Key-less shared-IP services are flaky from CI by nature — reported, never fatal. */
        val soft: Boolean = false
    )

    @Test
    fun verifyAllProviders() = runBlocking {
        // This test performs real, billable network calls, so it only runs in the dedicated
        // `verify-apis` workflow. The regular build skips it to stay deterministic and offline.
        assumeTrue(
            "Skipping live API verification (set RUN_LIVE_API_VERIFICATION=true to enable)",
            System.getenv("RUN_LIVE_API_VERIFICATION") == "true"
        )

        val factory = AiProviderFactory(okHttpClient, mock(PublicFreeApiService::class.java))
        val results = mutableListOf<ProbeResult>()

        // ── The 7 BYOK premium providers ─────────────────────────────────────────
        for (provider in LlmProvider.ALL) {
            results += probe(provider.displayName, provider.defaultModel) {
                factory.getProvider(provider.id)
            }
        }

        // ── The key-less Free AI tab models ──────────────────────────────────────
        // These are anonymous, per-IP-rate-limited public endpoints. CI runners share
        // IPs with thousands of other jobs, so occasional 428/overload responses are
        // environmental, not regressions — they are reported but never fail the build.
        for (freeModel in FreeAiModel.ALL) {
            results += probe("${freeModel.displayName} (Free AI tab)", "key-less", soft = true) {
                factory.getFreeProvider(freeModel.id)
            }
        }

        val report = buildString {
            append("\n==================================================\n")
            append("          REAL AI PROVIDER VERIFICATION REPORT     \n")
            append("==================================================\n")
            results.forEach { result ->
                append(if (result.ok) "✓ " else "✗ ")
                append(result.name)
                append("  [model: ${result.model}]\n")
                append("   ${result.detail.replace("\n", " ").take(300)}\n")
            }
            val passed = results.count { it.ok }
            val softFailed = results.count { !it.ok && it.soft }
            append("--------------------------------------------------\n")
            append("PASSED: $passed / ${results.size}")
            if (softFailed > 0) append("  ($softFailed key-less service(s) busy — non-blocking)")
            append("\n==================================================\n")
        }

        println(report)
        runCatching { java.io.File("api_verification_report.txt").writeText(report) }

        val failures = results.filterNot { it.ok || it.soft }
        if (failures.isNotEmpty()) {
            fail(
                "${failures.size} AI backend(s) failed verification:\n" +
                    failures.joinToString("\n") { "  ✗ ${it.name} — ${it.detail.take(200)}" } +
                    "\n$report"
            )
        }
    }

    /** Sends one real request through [providerFactory] and classifies the outcome. */
    private suspend fun probe(
        name: String,
        model: String,
        soft: Boolean = false,
        providerFactory: () -> AiProvider
    ): ProbeResult = try {
        var text: String? = null
        var error: String? = null
        providerFactory().sendMessage(prompt, emptyList()).collect { response ->
            when (response) {
                is AiResponse.Success -> text = response.text
                is AiResponse.Error -> error = response.text
            }
        }
        if (!text.isNullOrBlank()) {
            ProbeResult(name, "Succeeded. Response: \"${text!!.trim().take(160)}\"", model, true, soft)
        } else {
            ProbeResult(name, error ?: "Empty response", model, false, soft)
        }
    } catch (e: Exception) {
        ProbeResult(name, "Threw ${e.javaClass.simpleName}: ${e.localizedMessage}", model, false, soft)
    }
}
