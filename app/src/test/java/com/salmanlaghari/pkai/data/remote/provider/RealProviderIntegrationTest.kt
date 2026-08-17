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

        // 1. Google Gemini
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

        // 2. OpenRouter (multiple models: ChatGPT, Claude, DeepSeek, Qwen, Mistral, Grok)
        val openrouterKey = System.getenv("OPENROUTER_API_KEY") ?: ""
        if (openrouterKey.isNotBlank()) {
            val openRouterModels = listOf(
                "openai/gpt-4o-mini" to "ChatGPT",
                "anthropic/claude-3-haiku" to "Claude",
                "deepseek/deepseek-chat" to "DeepSeek",
                "qwen/qwen-2.5-72b-instruct" to "Qwen",
                "mistralai/mistral-7b-instruct" to "Mistral",
                "x-ai/grok-2-latest" to "Grok (grok-2-latest)",
                "x-ai/grok-beta" to "Grok (grok-beta)",
                "x-ai/grok-3" to "Grok (grok-3)"
            )
            try {
                val service = Retrofit.Builder()
                    .baseUrl("https://openrouter.ai/api/v1/")
                    .client(okHttpClient)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                    .create(OpenRouterApiService::class.java)

                for ((modelId, label) in openRouterModels) {
                    try {
                        val request = ChatCompletionRequest(
                            model = modelId,
                            messages = listOf(ChatMessageDto(role = "user", content = prompt))
                        )
                        val response = service.generateChatResponse("Bearer $openrouterKey", request = request)
                        val text = response.choices.firstOrNull()?.message?.content
                        if (text != null) {
                            report.append("✓ OpenRouter ($label): Succeeded. Response:\n   \"${text.trim()}\"\n")
                        } else {
                            report.append("✗ OpenRouter ($label): Failed. Reason: Empty response structure.\n")
                        }
                    } catch (e: Exception) {
                        report.append("✗ OpenRouter ($label): Failed. Reason: ${e.localizedMessage}\n")
                    }
                }
            } catch (e: Exception) {
                report.append("✗ OpenRouter: Failed to initialise. Reason: ${e.localizedMessage}\n")
            }
        } else {
            report.append("✗ OpenRouter: Skipped. Reason: OPENROUTER_API_KEY not configured.\n")
        }

        // 3. Groq
        val groqKey = System.getenv("GROQ_API_KEY") ?: ""
        if (groqKey.isNotBlank()) {
            try {
                val service = Retrofit.Builder()
                    .baseUrl("https://api.groq.com/openai/v1/")
                    .client(okHttpClient)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                    .create(GroqApiService::class.java)

                val request = ChatCompletionRequest(
                    model = "llama3-8b-8192",
                    messages = listOf(ChatMessageDto(role = "user", content = prompt))
                )
                val response = service.generateChatResponse("Bearer $groqKey", request)
                val text = response.choices.firstOrNull()?.message?.content
                if (text != null) {
                    report.append("✓ Groq: Succeeded. Response:\n   \"${text.trim()}\"\n")
                } else {
                    report.append("✗ Groq: Failed. Reason: Empty response structure.\n")
                }
            } catch (e: Exception) {
                report.append("✗ Groq: Failed. Reason: ${e.localizedMessage}\n")
            }
        } else {
            report.append("✗ Groq: Skipped. Reason: GROQ_API_KEY not configured.\n")
        }

        // 4. Together AI
        val togetherKey = System.getenv("TOGETHER_API_KEY") ?: ""
        if (togetherKey.isNotBlank()) {
            try {
                val service = Retrofit.Builder()
                    .baseUrl("https://api.together.xyz/v1/")
                    .client(okHttpClient)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                    .create(TogetherApiService::class.java)

                val request = ChatCompletionRequest(
                    model = "meta-llama/Meta-Llama-3-8B-Instruct-Lite",
                    messages = listOf(ChatMessageDto(role = "user", content = prompt))
                )
                val response = service.generateChatResponse("Bearer $togetherKey", request)
                val text = response.choices.firstOrNull()?.message?.content
                if (text != null) {
                    report.append("✓ Together AI: Succeeded. Response:\n   \"${text.trim()}\"\n")
                } else {
                    report.append("✗ Together AI: Failed. Reason: Empty response structure.\n")
                }
            } catch (e: Exception) {
                report.append("✗ Together AI: Failed. Reason: ${e.localizedMessage}\n")
            }
        } else {
            report.append("✗ Together AI: Skipped. Reason: TOGETHER_API_KEY not configured.\n")
        }

        // 5. Cohere
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
                    report.append("✓ Cohere: Succeeded. Response:\n   \"${text.trim()}\"\n")
                } else {
                    report.append("✗ Cohere: Failed. Reason: Empty response structure.\n")
                }
            } catch (e: Exception) {
                report.append("✗ Cohere: Failed. Reason: ${e.localizedMessage}\n")
            }
        } else {
            report.append("✗ Cohere: Skipped. Reason: COHERE_API_KEY not configured.\n")
        }

        // 6. Cerebras
        val cerebrasKey = System.getenv("CEREBRAS_API_KEY") ?: ""
        if (cerebrasKey.isNotBlank()) {
            try {
                val service = Retrofit.Builder()
                    .baseUrl("https://api.cerebras.ai/v1/")
                    .client(okHttpClient)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                    .create(CerebrasApiService::class.java)

                val request = ChatCompletionRequest(
                    model = "llama3.1-8b",
                    messages = listOf(ChatMessageDto(role = "user", content = prompt))
                )
                val response = service.generateChatResponse("Bearer $cerebrasKey", request)
                val text = response.choices.firstOrNull()?.message?.content
                if (text != null) {
                    report.append("✓ Cerebras: Succeeded. Response:\n   \"${text.trim()}\"\n")
                } else {
                    report.append("✗ Cerebras: Failed. Reason: Empty response structure.\n")
                }
            } catch (e: Exception) {
                report.append("✗ Cerebras: Failed. Reason: ${e.localizedMessage}\n")
            }
        } else {
            report.append("✗ Cerebras: Skipped. Reason: CEREBRAS_API_KEY not configured.\n")
        }

        // 7. SambaNova
        val sambanovaKey = System.getenv("SAMBANOVA_API_KEY") ?: ""
        if (sambanovaKey.isNotBlank()) {
            try {
                val service = Retrofit.Builder()
                    .baseUrl("https://api.sambanova.ai/v1/")
                    .client(okHttpClient)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                    .create(SambaNovaApiService::class.java)

                val request = ChatCompletionRequest(
                    model = "Meta-Llama-3.1-8B-Instruct",
                    messages = listOf(ChatMessageDto(role = "user", content = prompt))
                )
                val response = service.generateChatResponse("Bearer $sambanovaKey", request)
                val text = response.choices.firstOrNull()?.message?.content
                if (text != null) {
                    report.append("✓ SambaNova: Succeeded. Response:\n   \"${text.trim()}\"\n")
                } else {
                    report.append("✗ SambaNova: Failed. Reason: Empty response structure.\n")
                }
            } catch (e: Exception) {
                report.append("✗ SambaNova: Failed. Reason: ${e.localizedMessage}\n")
            }
        } else {
            report.append("✗ SambaNova: Skipped. Reason: SAMBANOVA_API_KEY not configured.\n")
        }

        // 8. xAI (Grok) — native xAI API, falls back to Groq if no key
        val xAiKey = System.getenv("XAI_API_KEY") ?: ""
        val groqKeyForGrok = System.getenv("GROQ_API_KEY") ?: ""
        if (xAiKey.isNotBlank()) {
            try {
                val service = Retrofit.Builder()
                    .baseUrl("https://api.x.ai/v1/")
                    .client(okHttpClient)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                    .create(XAiApiService::class.java)

                val request = ChatCompletionRequest(
                    model = "grok-2-latest",
                    messages = listOf(ChatMessageDto(role = "user", content = prompt))
                )
                val response = service.generateChatResponse("Bearer $xAiKey", request)
                val text = response.choices.firstOrNull()?.message?.content
                if (text != null) {
                    report.append("✓ xAI Grok (native): Succeeded. Response:\n   \"${text.trim()}\"\n")
                } else {
                    report.append("✗ xAI Grok (native): Failed. Reason: Empty response structure.\n")
                }
            } catch (e: Exception) {
                report.append("✗ xAI Grok (native): Failed. Reason: ${e.localizedMessage}\n")
            }
        } else if (groqKeyForGrok.isNotBlank()) {
            try {
                val service = Retrofit.Builder()
                    .baseUrl("https://api.groq.com/openai/v1/")
                    .client(okHttpClient)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                    .create(GroqApiService::class.java)

                val request = ChatCompletionRequest(
                    model = "llama3-8b-8192",
                    messages = listOf(ChatMessageDto(role = "user", content = prompt))
                )
                val response = service.generateChatResponse("Bearer $groqKeyForGrok", request)
                val text = response.choices.firstOrNull()?.message?.content
                if (text != null) {
                    report.append("✓ Grok (Groq fallback): Succeeded. Response:\n   \"${text.trim()}\"\n")
                } else {
                    report.append("✗ Grok (Groq fallback): Failed. Reason: Empty response structure.\n")
                }
            } catch (e: Exception) {
                report.append("✗ Grok (Groq fallback): Failed. Reason: ${e.localizedMessage}\n")
            }
        } else {
            report.append("✗ xAI Grok: Skipped. Reason: XAI_API_KEY (and GROQ_API_KEY) not configured.\n")
        }

        // 9. Anthropic (Claude) — native Anthropic API, falls back to OpenRouter if no key
        val anthropicKey = System.getenv("ANTHROPIC_API_KEY") ?: ""
        if (anthropicKey.isNotBlank()) {
            try {
                val service = Retrofit.Builder()
                    .baseUrl("https://api.anthropic.com/")
                    .client(okHttpClient)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                    .create(AnthropicApiService::class.java)

                val request = AnthropicRequest(
                    messages = listOf(AnthropicMessage(role = "user", content = prompt))
                )
                val response = service.createMessage(anthropicKey, request = request)
                val text = response.content?.firstOrNull { it.type == "text" }?.text
                if (text != null) {
                    report.append("✓ Anthropic Claude (native): Succeeded. Response:\n   \"${text.trim()}\"\n")
                } else {
                    report.append("✗ Anthropic Claude (native): Failed. Reason: Empty response structure.\n")
                }
            } catch (e: Exception) {
                report.append("✗ Anthropic Claude (native): Failed. Reason: ${e.localizedMessage}\n")
            }
        } else if (openrouterKey.isNotBlank()) {
            report.append("ℹ Anthropic Claude: Using OpenRouter fallback (no ANTHROPIC_API_KEY set).\n")
        } else {
            report.append("✗ Anthropic Claude: Skipped. Reason: ANTHROPIC_API_KEY (and OPENROUTER_API_KEY) not configured.\n")
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
