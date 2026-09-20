package com.salmanlaghari.pkai.ui.aihub

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.salmanlaghari.pkai.R
import com.salmanlaghari.pkai.databinding.FragmentAiHubBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AiHubFragment : Fragment() {

    private var _binding: FragmentAiHubBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AiHubViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAiHubBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupWebView()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val webView = binding.webviewUltraAi
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = true
        webView.settings.allowContentAccess = true
        webView.settings.cacheMode = android.webkit.WebSettings.LOAD_DEFAULT

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                val googleClientId = ""
                val redirectUri = ""
                view?.evaluateJavascript(
                    """
                    window.GOOGLE_CLIENT_ID = $googleClientId;
                    window.GOOGLE_REDIRECT_URI = $redirectUri;
                    """.trimIndent(),
                    null
                )
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val url = request?.url?.toString() ?: return false
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    return false
                }
                return true
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
            }
        }

        webView.addJavascriptInterface(
            object {
                @JavascriptInterface
                fun onGoogleAuthSuccess(accessToken: String, email: String, name: String, picture: String) {
                    activity?.runOnUiThread {
                        val js = """
                            window.AndroidOAuth?.onAuthSuccess?.($accessToken, {
                                name: $name,
                                email: $email,
                                picture: $picture
                            });
                        """.trimIndent()
                        webView.evaluateJavascript(js, null)
                    }
                }

                @JavascriptInterface
                fun onGoogleAuthError(error: String) {
                    activity?.runOnUiThread {
                        val js = """
                            window.AndroidOAuth?.onAuthError?.($error);
                        """.trimIndent()
                        webView.evaluateJavascript(js, null)
                    }
                }
            },
            "AndroidOAuth"
        )

        webView.loadUrl("file:///android_asset/ultra-ai-chat-space/index.html")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
