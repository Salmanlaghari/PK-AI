package com.salmanlaghari.pkai.data.remote.provider

import com.salmanlaghari.pkai.data.remote.*
import kotlinx.coroutines.runBlocking
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
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
        val report = StringBuilder()
        report.append("\n==================================================\n")
        report.append("          REAL AI PROVIDER VERIFICATION REPORT     \n")
        report.append("==================================================\n")

        // 1. Google Gemini (Official API)
        val geminiKey = System.getenv("GEMINI_API_KEY") ?: ""
        if (geminiKey.isNotBlank()) {
            try {
                val service = Retrofit.Builder()
                    .baseUrl("https://generativelanguage.googleapis.com/")
                    .client(okHttpClient)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                    .create(GeminiApiService::class.java)

                val request = GeminiRequest(
                    contents = listOf(GeminiContent(parts = listOf(GeminiPart(prompt))))
                )
                val response = service.generateContent(geminiKey, request)
                val text = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                if (text != null) {
                    report.append("✓ Google Gemini (Official): Succeeded. Response:\n   \"${text.trim()}\"\n")
                } else {
                    report.append("✗ Google Gemini (Official): Failed. Reason: Empty response structure.\n")
                }
            } catch (e: Exception) {
                report.append("✗ Google Gemini (Official): Failed. Reason: ${e.localizedMessage}\n")
            }
        } else {
            report.append("✗ Google Gemini (Official): Skipped. Reason: GEMINI_API_KEY not configured.\n")
        }

        // 2. OpenRouter Mapped Models
        val openrouterKey = System.getenv("OPENROUTER_API_KEY") ?: ""
        if (openrouterKey.isNotBlank()) {
            val openRouterService = Retrofit.Builder()
                .baseUrl("https://openrouter.ai/api/v1/")
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(OpenRouterApiService::class.java)

            val modelsToTest = mapOf(
                "Grok" to "x-ai/grok-2",
                "Llama" to "meta-llama/llama-3.3-70b-instruct",
                "Mistral" to "mistralai/mistral-nemo",
                "Perplexity" to "perplexity/sonar",
                "Claude" to "anthropic/claude-3.5-sonnet",
                "DeepSeek" to "deepseek/deepseek-chat"
            )

            modelsToTest.forEach { (displayName, modelId) ->
                try {
                    val request = ChatCompletionRequest(
                        model = modelId,
                        messages = listOf(ChatMessageDto(role = "user", content = prompt))
                    )
                    val response = openRouterService.generateChatResponse("Bearer $openrouterKey", request = request)
                    val text = response.choices.firstOrNull()?.message?.content
                    if (text != null) {
                        report.append("✓ $displayName via OpenRouter ($modelId): Succeeded. Response:\n   \"${text.trim()}\"\n")
                    } else {
                        report.append("✗ $displayName via OpenRouter ($modelId): Failed. Reason: Empty response structure.\n")
                    }
                } catch (e: Exception) {
                    report.append("✗ $displayName via OpenRouter ($modelId): Failed. Reason: ${e.localizedMessage}\n")
                }
            }
        } else {
            report.append("✗ OpenRouter Models: Skipped. Reason: OPENROUTER_API_KEY not configured.\n")
        }

        report.append("==================================================\n")
        println(report.toString())
    }
}
