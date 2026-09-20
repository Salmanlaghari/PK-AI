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
import com.salmanlaghari.pkai.data.repository.AuthRepository
import com.salmanlaghari.pkai.databinding.FragmentAiHubBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject

@AndroidEntryPoint
class AiHubFragment : Fragment() {

    private var _binding: FragmentAiHubBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AiHubViewModel by viewModels()

    @Inject
    lateinit var authRepository: AuthRepository

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

                val googleClientId = try {
                    getString(R.string.default_web_client_id)
                } catch (e: Exception) {
                    ""
                }
                val redirectUri = ""
                val clientIdJs = JSONObject.quote(googleClientId)
                val redirectUriJs = JSONObject.quote(redirectUri)

                val js = """
                    window.GOOGLE_CLIENT_ID = $clientIdJs;
                    window.GOOGLE_REDIRECT_URI = $redirectUriJs;
                    console.log("[AiHub] Injected GOOGLE_CLIENT_ID successfully");
                """.trimIndent()
                view?.evaluateJavascript(js, null)
                Log.d("AiHubFragment", "Injected JS configuration into WebView")
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

        // Bridge exposed to WebView JavaScript as window.AndroidOAuth
        webView.addJavascriptInterface(
            object {
                @JavascriptInterface
                fun startGoogleSignIn() {
                    Log.d("AiHubFragment", "startGoogleSignIn called from JS")
                    activity?.runOnUiThread {
                        triggerGoogleSignIn()
                    }
                }

                @JavascriptInterface
                fun startGoogleSignIn(clientId: String?, redirectUri: String?) {
                    Log.d("AiHubFragment", "startGoogleSignIn(clientId, redirectUri) called from JS")
                    activity?.runOnUiThread {
                        triggerGoogleSignIn()
                    }
                }
            },
            "AndroidOAuth"
        )

        webView.loadUrl("file:///android_asset/ultra-ai-chat-space/index.html")
    }

    private fun triggerGoogleSignIn() {
        try {
            val credentialManager = CredentialManager.create(requireContext())
            val clientId = try {
                getString(R.string.default_web_client_id)
            } catch (e: Exception) {
                ""
            }

            if (clientId.isBlank()) {
                dispatchAuthErrorToJs("Google Client ID is not configured in strings.xml")
                return
            }

            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(clientId)
                .setAutoSelectEnabled(true)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    Log.d("AiHubFragment", "Requesting credentials via CredentialManager with client ID: $clientId")
                    val result = credentialManager.getCredential(
                        request = request,
                        context = requireContext()
                    )
                    val credential = result.credential
                    if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                        val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                        val idToken = googleIdTokenCredential.idToken
                        val displayName = googleIdTokenCredential.displayName ?: ""
                        val email = googleIdTokenCredential.id
                        val photoUrl = googleIdTokenCredential.profilePictureUri?.toString() ?: ""

                        Log.i("AiHubFragment", "Google Sign-In success! Email: $email")

                        // Update app-wide session in repository
                        try {
                            authRepository.loginWithGoogle(
                                idToken = idToken,
                                displayName = displayName,
                                email = email,
                                photoUrl = photoUrl
                            )
                        } catch (e: Exception) {
                            Log.w("AiHubFragment", "Failed to update authRepository session: ${e.message}")
                        }

                        dispatchAuthSuccessToJs(idToken, email, displayName, photoUrl)
                    } else {
                        Log.e("AiHubFragment", "Unsupported credential type: ${credential.type}")
                        dispatchAuthErrorToJs("Unsupported credential type: ${credential.type}")
                    }
                } catch (e: androidx.credentials.exceptions.GetCredentialCancellationException) {
                    Log.d("AiHubFragment", "Google Sign-In was cancelled by user")
                    dispatchAuthErrorToJs("Sign-In cancelled.")
                } catch (e: androidx.credentials.exceptions.NoCredentialException) {
                    Log.e("AiHubFragment", "No credentials available on device", e)
                    dispatchAuthErrorToJs("No Google account found on this device.")
                } catch (e: Exception) {
                    Log.e("AiHubFragment", "CredentialManager getCredential failed", e)
                    dispatchAuthErrorToJs(e.localizedMessage ?: "Google Sign-In failed")
                }
            }
        } catch (e: Exception) {
            Log.e("AiHubFragment", "Failed to initialize Google Sign-In", e)
            dispatchAuthErrorToJs(e.localizedMessage ?: "Failed to start Google Sign-In")
        }
    }

    private fun dispatchAuthSuccessToJs(token: String, email: String, name: String, picture: String) {
        activity?.runOnUiThread {
            val tokenJs = JSONObject.quote(token)
            val emailJs = JSONObject.quote(email)
            val nameJs = JSONObject.quote(name)
            val pictureJs = JSONObject.quote(picture)

            val js = """
                (function() {
                    var user = { name: $nameJs, email: $emailJs, picture: $pictureJs };
                    var token = $tokenJs;
                    if (typeof window.onAndroidGoogleAuthSuccess === 'function') {
                        window.onAndroidGoogleAuthSuccess(token, user);
                    }
                    if (typeof window.__onGoogleAuthSuccess === 'function') {
                        window.__onGoogleAuthSuccess(token, user);
                    }
                    if (window.AndroidOAuth && typeof window.AndroidOAuth.onAuthSuccess === 'function') {
                        try { window.AndroidOAuth.onAuthSuccess(token, user); } catch(e) {}
                    }
                    window.dispatchEvent(new CustomEvent('pkai:auth_success', { detail: { token: token, user: user } }));
                })();
            """.trimIndent()
            _binding?.webviewUltraAi?.evaluateJavascript(js, null)
            Log.d("AiHubFragment", "Dispatched auth success to WebView")
        }
    }

    private fun dispatchAuthErrorToJs(error: String) {
        activity?.runOnUiThread {
            val errorJs = JSONObject.quote(error)
            val js = """
                (function() {
                    var err = $errorJs;
                    if (typeof window.onAndroidGoogleAuthError === 'function') {
                        window.onAndroidGoogleAuthError(err);
                    }
                    if (typeof window.__onGoogleAuthError === 'function') {
                        window.__onGoogleAuthError(err);
                    }
                    if (window.AndroidOAuth && typeof window.AndroidOAuth.onAuthError === 'function') {
                        try { window.AndroidOAuth.onAuthError(err); } catch(e) {}
                    }
                    window.dispatchEvent(new CustomEvent('pkai:auth_error', { detail: { error: err } }));
                })();
            """.trimIndent()
            _binding?.webviewUltraAi?.evaluateJavascript(js, null)
            Log.d("AiHubFragment", "Dispatched auth error to WebView: $error")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
