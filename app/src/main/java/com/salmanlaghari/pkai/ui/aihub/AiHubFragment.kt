package com.salmanlaghari.pkai.ui.aihub

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.salmanlaghari.pkai.R
import com.salmanlaghari.pkai.databinding.FragmentAiHubBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.json.JSONObject

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
                Log.d("AiHubFragment", "Page started loading: $url")
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                Log.d("AiHubFragment", "Page finished loading: $url")

                val googleClientId = ""
                val redirectUri = ""

                val clientIdJs = JSONObject.quote(googleClientId)
                val redirectUriJs = JSONObject.quote(redirectUri)

                val js = """
                    window.GOOGLE_CLIENT_ID = $clientIdJs;
                    window.GOOGLE_REDIRECT_URI = $redirectUriJs;
                    console.log("Injected GOOGLE_CLIENT_ID:", window.GOOGLE_CLIENT_ID);
                    console.log("Injected GOOGLE_REDIRECT_URI:", window.GOOGLE_REDIRECT_URI);
                """.trimIndent()

                view?.evaluateJavascript(js, null)
                Log.d("AiHubFragment", "Injected JS: $js")
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
                if (newProgress == 100) {
                    Log.d("AiHubFragment", "WebView load complete")
                }
            }
        }

        webView.addJavascriptInterface(
            object {
                @JavascriptInterface
                fun startGoogleSignIn(
                    clientId: String,
                    redirectUri: String,
                    onSuccess: (token: String, user: UserInfo) -> Unit,
                    onError: (error: String) -> Unit
                ) {
                    Log.d("AiHubFragment", "startGoogleSignIn called from JS")
                    activity?.runOnUiThread {
                        try {
                            val credentialManager = CredentialManager.create(requireContext())
                            val googleIdOption = GetGoogleIdOption.Builder()
                                .setFilterByAuthorizedAccounts(false)
                                .setServerClientId(clientId)
                                .setAutoSelectEnabled(true)
                                .build()

                            val request = GetCredentialRequest.Builder()
                                .addCredentialOption(googleIdOption)
                                .build()

                            lifecycleScope.launch {
                                try {
                                    val result = credentialManager.getCredential(
                                        request = request,
                                        context = requireContext()
                                    )
                                    val credential = result.credential
                                    if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                                        val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                                        val token = googleIdTokenCredential.idToken
                                        val profile = googleIdTokenCredential.profile
                                        val user = UserInfo(
                                            name = profile?.name ?: "",
                                            email = profile?.email ?: "",
                                            picture = profile?.profilePictureUri?.toString() ?: ""
                                        )
                                        onSuccess(token, user)
                                    } else {
                                        onError("Unsupported credential type: ${credential.type}")
                                    }
                                } catch (e: Exception) {
                                    onError("Google Sign-In failed: ${e.message}")
                                }
                            }
                        } catch (e: Exception) {
                            onError("Failed to start Google Sign-In: ${e.message}")
                        }
                    }
                }

                @JavascriptInterface
                fun onGoogleAuthSuccess(accessToken: String, email: String, name: String, picture: String) {
                    Log.d("AiHubFragment", "onGoogleAuthSuccess: $email")
                    activity?.runOnUiThread {
                        val clientIdJs = JSONObject.quote("")
                        val redirectUriJs = JSONObject.quote("")
                        val accessTokenJs = JSONObject.quote(accessToken)
                        val emailJs = JSONObject.quote(email)
                        val nameJs = JSONObject.quote(name)
                        val pictureJs = JSONObject.quote(picture)
                        val js = """
                            window.AndroidOAuth?.onAuthSuccess?.($accessTokenJs, {
                                name: $nameJs,
                                email: $emailJs,
                                picture: $pictureJs
                            });
                        """.trimIndent()
                        webView.evaluateJavascript(js, null)
                    }
                }

                @JavascriptInterface
                fun onGoogleAuthError(error: String) {
                    Log.d("AiHubFragment", "onGoogleAuthError: $error")
                    activity?.runOnUiThread {
                        val errorJs = JSONObject.quote(error)
                        val js = """
                            window.AndroidOAuth?.onAuthError?.($errorJs);
                        """.trimIndent()
                        webView.evaluateJavascript(js, null)
                    }
                }
            },
            "AndroidOAuth"
        )

        webView.loadUrl("file:///android_asset/ultra-ai-chat-space/index.html")
    }

    data class UserInfo(
        val name: String,
        val email: String,
        val picture: String
    )

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
