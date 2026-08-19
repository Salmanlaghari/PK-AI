package com.salmanlaghari.pkai.data.remote.provider

import com.salmanlaghari.pkai.data.model.LlmProvider
import com.salmanlaghari.pkai.data.remote.PublicFreeApiService
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.junit.Test
import org.mockito.Mockito.mock
import java.util.concurrent.TimeUnit

class RealProviderIntegrationTest {

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .build()

    private val prompt = "Hello, who are you?"

    @Test
    fun verifyAllProviders() = runBlocking {
        val factory = AiProviderFactory(okHttpClient, mock(PublicFreeApiService::class.java))

        val report = StringBuilder()
        report.append("\n==================================================\n")
        report.append("          REAL AI PROVIDER VERIFICATION REPORT     \n")
        report.append("==================================================\n")

        for (provider in LlmProvider.ALL) {
            try {
                var text: String? = null
                var error: String? = null
                factory.getProvider(provider.id).sendMessage(prompt, emptyList()).collect { response ->
                    when (response) {
                        is AiResponse.Success -> text = response.text
                        is AiResponse.Error -> error = response.text
                    }
                }
                if (!text.isNullOrBlank()) {
                    report.append("✓ ${provider.displayName}: Succeeded. Response:\n   \"${text!!.trim()}\"\n")
                } else {
                    report.append("✗ ${provider.displayName}: ${error ?: "Empty response"}\n")
                }
            } catch (e: Exception) {
                report.append("✗ ${provider.displayName}: Failed. Reason: ${e.localizedMessage}\n")
            }
        }

        report.append("==================================================\n")
        println(report.toString())
        try {
            val outFile = java.io.File("api_verification_report.txt")
            outFile.writeText(report.toString())
            println("API verification report written to: ${outFile.absolutePath}")
        } catch (e: Exception) {
            println("Could not write report file: ${e.localizedMessage}")
        }
    }
}
