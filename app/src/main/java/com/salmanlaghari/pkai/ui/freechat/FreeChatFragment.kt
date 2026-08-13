package com.salmanlaghari.pkai.ui.freechat

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.salmanlaghari.pkai.BuildConfig
import com.salmanlaghari.pkai.data.remote.GeminiContent
import com.salmanlaghari.pkai.data.remote.GeminiPart
import com.salmanlaghari.pkai.data.remote.GeminiRequest
import com.salmanlaghari.pkai.data.remote.GeminiApiService
import com.salmanlaghari.pkai.databinding.FragmentFreeChatBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject

/**
 * Free Chat screen.
 *
 * Loads a self-contained, premium dark chat page from assets and bridges it to
 * Gemini Flash (Gemini API via Firebase AI) so users can chat for free without
 * logging in. The Gemini API key is auto-injected at build time from
 * local.properties / environment variables — it is never hard-coded.
 */
@AndroidEntryPoint
class FreeChatFragment : Fragment() {

    private var _binding: FragmentFreeChatBinding? = null
    private val binding get() = _binding!!

    @Inject
    lateinit var geminiApiService: GeminiApiService

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFreeChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.webView.apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            settings.allowFileAccess = true
            addJavascriptInterface(JsBridge(), "PkBridge")
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest
                ): Boolean {
                    val url = request.url.toString()
                    // Keep asset-internal navigation inside the WebView
                    if (url.startsWith("file://")) return false
                    // Open any external links in the system browser
                    runCatching {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }
                    return true
                }
            }
            loadUrl("file:///android_asset/free_chat.html")
        }

        // Handle back: history first, then normal navigation back
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (_binding != null && binding.webView.canGoBack()) {
                        binding.webView.goBack()
                    } else {
                        findNavController().navigateUp()
                    }
                }
            }
        )
    }

    /**
     * JavaScript bridge exposed to the assets page as `window.PkBridge`.
     */
    private inner class JsBridge {

        @JavascriptInterface
        fun sendMessageAsync(message: String, callbackId: String) {
            val webView = binding.webView
            viewLifecycleOwner.lifecycleScope.launch {
                val text = withContext(Dispatchers.IO) {
                    runCatching {
                        // Gemini Flash — Gemini API key auto-injected at build time.
                        val key = BuildConfig.GEMINI_API_KEY
                        if (key.isBlank()) {
                            "API key not configured. Add GEMINI_API_KEY to your local.properties (or CI env). It is auto-injected and never hard-coded."
                        } else {
                            val request = GeminiRequest(
                                contents = listOf(
                                    GeminiContent(parts = listOf(GeminiPart(message)))
                                )
                            )
                            geminiApiService.generateContent(key, request)
                                .candidates?.firstOrNull()
                                ?.content?.parts?.firstOrNull()?.text
                                ?: "Empty response from Gemini Flash."
                        }
                    }.getOrElse { error ->
                        error.localizedMessage ?: "Something went wrong while reaching Gemini Flash."
                    }
                }
                val payload = JSONObject()
                    .put("id", callbackId)
                    .put("text", text)
                    .toString()
                // Must dispatch back onto the main thread for evaluateJavascript
                webView.post {
                    webView.evaluateJavascript(
                        "window.onNativeResponse($payload);",
                        null
                    )
                }
            }
        }
    }

    override fun onDestroyView() {
        binding.webView.run {
            loadUrl("about:blank")
            stopLoading()
            removeAllViews()
            destroy()
        }
        _binding = null
        super.onDestroyView()
    }
}