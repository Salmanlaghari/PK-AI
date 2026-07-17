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

        // 1. Google Gemini (Direct API)
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
                    report.append("✓ Google Gemini: Succeeded. Response:\n   \"${text.trim()}\"\n")
                } else {
                    report.append("✗ Google Gemini: Failed. Reason: Empty response structure.\n")
                }
            } catch (e: Exception) {
                report.append("✗ Google Gemini: Failed. Reason: ${e.localizedMessage}\n")
            }
        } else {
            report.append("✗ Google Gemini: Skipped. Reason: GEMINI_API_KEY not configured.\n")
        }

        // 2. OpenRouter (Central API for multiple models)
        val openrouterKey = System.getenv("OPENROUTER_API_KEY") ?: ""
        if (openrouterKey.isNotBlank()) {
            val openRouterService = Retrofit.Builder()
                .baseUrl("https://openrouter.ai/api/v1/")
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(OpenRouterApiService::class.java)

            val openRouterModels = listOf(
                "Claude (anthropic/claude-3-haiku)" to "anthropic/claude-3-haiku",
                "DeepSeek (deepseek/deepseek-chat)" to "deepseek/deepseek-chat",
                "Grok (x-ai/grok-beta)" to "x-ai/grok-beta",
                "Qwen (qwen/qwen-2.5-72b-instruct)" to "qwen/qwen-2.5-72b-instruct",
                "Llama (meta-llama/llama-3.3-70b-instruct)" to "meta-llama/llama-3.3-70b-instruct",
                "Mistral (mistralai/mistral-7b-instruct)" to "mistralai/mistral-7b-instruct",
                "Perplexity (perplexity/sonar)" to "perplexity/sonar"
            )

            openRouterModels.forEach { (displayName, modelId) ->
                try {
                    val request = ChatCompletionRequest(
                        model = modelId,
                        messages = listOf(ChatMessageDto(role = "user", content = prompt))
                    )
                    val response = openRouterService.generateChatResponse("Bearer $openrouterKey", request = request)
                    val text = response.choices.firstOrNull()?.message?.content
                    if (text != null) {
                        report.append("✓ OpenRouter - $displayName: Succeeded. Response:\n   \"${text.trim()}\"\n")
                    } else {
                        report.append("✗ OpenRouter - $displayName: Failed. Reason: Empty response structure.\n")
                    }
                } catch (e: Exception) {
                    report.append("✗ OpenRouter - $displayName: Failed. Reason: ${e.localizedMessage}\n")
                }
            }
        } else {
            report.append("✗ OpenRouter Models: Skipped. Reason: OPENROUTER_API_KEY not configured.\n")
        }

        // 3. Cohere (Direct API)
        val cohereKey = System.getenv("COHERE_API_KEY") ?: ""
        if (cohereKey.isNotBlank()) {
            try {
                val service = Retrofit.Builder()
                    .baseUrl("https://api.cohere.com/")
                    .client(okHttpClient)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                    .create(CohereApiService::class.java)

                val request = CohereChatRequest(
                    message = prompt,
                    model = "command-r-plus"
                )
                val response = service.generateChatResponse("Bearer $cohereKey", request)
                val text = response.text
                if (text != null) {
                    report.append("✓ Cohere (Direct): Succeeded. Response:\n   \"${text.trim()}\"\n")
                } else {
                    report.append("✗ Cohere (Direct): Failed. Reason: Empty response structure.\n")
                }
            } catch (e: Exception) {
                report.append("✗ Cohere (Direct): Failed. Reason: ${e.localizedMessage}\n")
            }
        } else {
            report.append("✗ Cohere (Direct): Skipped. Reason: COHERE_API_KEY not configured.\n")
        }

        report.append("==================================================\n")
        println(report.toString())
    }
}
