package com.salmanlaghari.pkai.ui.aihub

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.browser.customtabs.CustomTabsIntent
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.webkit.WebViewAssetLoader
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.salmanlaghari.pkai.R
import com.salmanlaghari.pkai.data.repository.AuthRepository
import com.salmanlaghari.pkai.databinding.FragmentAiHubBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONTokener
import java.io.InputStream
import javax.inject.Inject

/**
 * AiHubFragment hosts the full-screen "Ultra AI 4" chat space (a bundled React app)
 * and connects it to a REAL Flow Music session.
 *
 * Option D architecture (WebView real session):
 *  - The user signs into https://flowmusic.app with their own Google account inside
 *    a WebView. Cookies + DOM storage persist across app restarts, so the session
 *    stays connected in the background.
 *  - When the user types a music prompt in the Ultra AI chat, we drive the Flow
 *    Music studio WebView (type prompt -> click Create -> watch for the audio
 *    result) via the injected flowmusic-automation.js engine, then stream the
 *    result back into the chat UI. The user never leaves the chat screen.
 */
@AndroidEntryPoint
class AiHubFragment : Fragment() {

    private var _binding: FragmentAiHubBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AiHubViewModel by viewModels()

    @Inject
    lateinit var authRepository: AuthRepository

    private val statusHandler = Handler(Looper.getMainLooper())
    private var lastStatusJson = ""
    private var signInModalAutoClosed = false

    /** PKCE code_verifier for the in-flight OAuth connection (Custom Tab flow). */
    private var pendingCodeVerifier: String? = null

    /** Cached automation engine, injected into the Flow Music WebView. */
    private val automationScript: String by lazy {
        try {
            requireContext().assets.open("ultra-ai-chat-space/flowmusic-automation.js")
                .bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            Log.e("AiHubFragment", "Failed to load flowmusic-automation.js", e)
            ""
        }
    }

    private val statusPoll = object : Runnable {
        override fun run() {
            probeFlowMusicSession()
            statusHandler.postDelayed(this, 2500)
        }
    }

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
        setupFlowMusicEngine()

        binding.btnCloseFlowmusicSignup.setOnClickListener {
            closeFlowMusicSignUp()
        }

        // Receive the OAuth deep link forwarded by MainActivity (flushes any
        // link that arrived during a cold start).
        FlowMusicOAuth.register { uri -> handleFlowMusicCallback(uri) }

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.containerFlowmusicSignup.visibility == View.VISIBLE) {
                    closeFlowMusicSignUp()
                    return
                }
                val webView = _binding?.webviewUltraAi
                if (webView != null && webView.canGoBack()) {
                    webView.goBack()
                } else {
                    isEnabled = false
                    findNavController().navigateUp()
                }
            }
        })
    }

    // ==========================================================================
    // Flow Music engine (persistent real session + automation)
    // ==========================================================================

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupFlowMusicEngine() {
        // Persist cookies (including the Flow Music / Google session) to disk.
        CookieManager.getInstance().setAcceptCookie(true)

        // ---- Background engine WebView: holds the real session + runs automation
        binding.webviewFlowmusicBackend.apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.allowFileAccess = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
            // A real Chrome UA improves compatibility with Google OAuth (avoids
            // the "disallowed_useragent" block that embedded WebViews can trigger).
            settings.userAgentString = CHROME_MOBILE_UA
            settings.setSupportMultipleWindows(true)
            settings.javaScriptCanOpenWindowsAutomatically = true
            isClickable = false
            isFocusable = false
            isFocusableInTouchMode = false
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            addJavascriptInterface(FlowMusicNativeBridge(), "FlowMusicNative")
            webChromeClient = object : WebChromeClient() {
                override fun onPermissionRequest(request: PermissionRequest?) {
                    request?.grant(request.resources)
                }
                override fun onCreateWindow(
                    view: WebView?,
                    isDialog: Boolean,
                    isUserGesture: Boolean,
                    resultMsg: android.os.Message?
                ): Boolean {
                    val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
                    transport.webView = view
                    resultMsg.sendToTarget()
                    return true
                }
                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                    Log.d("FlowMusicJS", "[${consoleMessage?.messageLevel()}] ${consoleMessage?.message()}")
                    return true
                }
            }
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    Log.d("AiHubFragment", "Flow Music engine page finished: $url")
                    if (automationScript.isNotBlank()) {
                        view?.evaluateJavascript(automationScript, null)
                    }
                }
            }
            loadUrl(FLOW_MUSIC_URL)
        }

        // ---- Visible sign-in WebView (shares cookies/storage with the engine)
        binding.webviewFlowmusicSignup.apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.allowFileAccess = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.userAgentString = CHROME_MOBILE_UA
            // Some OAuth flows open a popup window; allow it and route it back
            // into this same WebView so the user can complete Google sign-in.
            settings.setSupportMultipleWindows(true)
            settings.javaScriptCanOpenWindowsAutomatically = true
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webChromeClient = object : WebChromeClient() {
                override fun onPermissionRequest(request: PermissionRequest?) {
                    request?.grant(request.resources)
                }

                override fun onCreateWindow(
                    view: WebView?,
                    isDialog: Boolean,
                    isUserGesture: Boolean,
                    resultMsg: android.os.Message?
                ): Boolean {
                    // Reuse the same (visible) WebView to render the popup content.
                    val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
                    transport.webView = view
                    resultMsg.sendToTarget()
                    return true
                }
            }
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    Log.d("AiHubFragment", "Flow Music sign-in page finished: $url")
                    // Flush cookies so the session survives app restarts.
                    CookieManager.getInstance().flush()
                    // Probe immediately so we can auto-close the modal after sign-in.
                    probeFlowMusicSession()
                }
            }
        }

        statusHandler.post(statusPoll)
    }

    private fun probeFlowMusicSession() {
        val wv = _binding?.webviewFlowmusicBackend ?: return
        val js = "window.__FLOW_AUTOMATION__ && window.__FLOW_AUTOMATION__.probe ? window.__FLOW_AUTOMATION__.probe() : JSON.stringify({signedIn:false,hasStudio:false})"
        wv.evaluateJavascript(js) { raw ->
            val decoded = decodeJsString(raw) ?: return@evaluateJavascript
            if (decoded != lastStatusJson) {
                lastStatusJson = decoded
                dispatchStatusToJs(decoded)
                maybeAutoCloseSignInModal(decoded)
            }
        }
    }

    private fun maybeAutoCloseSignInModal(statusJson: String) {
        try {
            val obj = JSONObject(statusJson)
            val signedIn = obj.optBoolean("signedIn", false)
            if (signedIn && binding.containerFlowmusicSignup.visibility == View.VISIBLE && !signInModalAutoClosed) {
                signInModalAutoClosed = true
                // Reload the engine so it picks up the freshly created session.
                _binding?.webviewFlowmusicBackend?.reload()
                Toast.makeText(requireContext(), "Ultra Chat AI connected ✓", Toast.LENGTH_SHORT).show()
                statusHandler.postDelayed({ closeFlowMusicSignUp() }, 1200)
            }
        } catch (e: Exception) {
            Log.w("AiHubFragment", "maybeAutoCloseSignInModal: ${e.message}")
        }
    }

    private fun dispatchStatusToJs(statusJson: String) {
        activity?.runOnUiThread {
            val quoted = JSONObject.quote(statusJson)
            val js = """
                (function(){
                    try {
                        var data = JSON.parse($quoted);
                        window.__flowMusicStatus = data;
                        if (typeof window.onFlowMusicStatus === 'function') window.onFlowMusicStatus(data);
                        window.dispatchEvent(new CustomEvent('pkai:flowmusic_status', { detail: data }));
                    } catch(e) {}
                })();
            """.trimIndent()
            _binding?.webviewUltraAi?.evaluateJavascript(js, null)
        }
    }

    private fun dispatchTrackResultToJs(resultJson: String) {
        activity?.runOnUiThread {
            val quoted = JSONObject.quote(resultJson)
            val js = """
                (function(){
                    try {
                        var data = JSON.parse($quoted);
                        if (typeof window.onFlowMusicTrackResult === 'function') window.onFlowMusicTrackResult(data);
                        window.dispatchEvent(new CustomEvent('pkai:flowmusic_track', { detail: data }));
                    } catch(e) {}
                })();
            """.trimIndent()
            _binding?.webviewUltraAi?.evaluateJavascript(js, null)
        }
    }

    /** Streams live generation progress into the Ultra AI chat bubble. */
    private fun dispatchProgressToJs(progressJson: String) {
        activity?.runOnUiThread {
            val quoted = JSONObject.quote(progressJson)
            val js = """
                (function(){
                    try {
                        var data = JSON.parse($quoted);
                        if (typeof window.onFlowMusicProgress === 'function') window.onFlowMusicProgress(data);
                        window.dispatchEvent(new CustomEvent('pkai:flowmusic_progress', { detail: data }));
                    } catch(e) {}
                })();
            """.trimIndent()
            _binding?.webviewUltraAi?.evaluateJavascript(js, null)
        }
    }

    private fun startFlowMusicGeneration(prompt: String) {
        val wv = _binding?.webviewFlowmusicBackend
        if (wv == null) {
            dispatchTrackResultToJs("""{"ok":false,"error":"Ultra AI 4 engine unavailable."}""")
            return
        }
        val quoted = JSONObject.quote(prompt)
        val js = "if (window.__FLOW_AUTOMATION__ && window.__FLOW_AUTOMATION__.generate) { window.__FLOW_AUTOMATION__.generate($quoted); } else { window.FlowMusicNative && window.FlowMusicNative.onTrackResult(JSON.stringify({ok:false,error:'Ultra AI 4 engine not ready. Please reconnect Ultra Chat AI.'})); }"
        wv.evaluateJavascript(js, null)
    }

    /**
     * Starts the real account connection - the BROWSER-FREE path.
     *
     * The Google account picker pops up INSIDE Ultra Chat AI (the very same
     * native Credential Manager sheet the PK-AI sign-in uses). The resulting
     * Google ID token is exchanged with the music backend via
     * `grant_type=id_token`, so no Chrome / external page ever opens and the
     * user never leaves the Ultra AI interface.
     *
     * If the backend rejects the ID token (e.g. the client id is not on the
     * backend's allow-list), we transparently fall back to the Chrome Custom
     * Tab PKCE flow so the feature always works.
     */
    fun connectFlowMusic() {
        activity?.runOnUiThread {
            Toast.makeText(
                requireContext(),
                "Ultra Chat AI account connect ho raha hai...",
                Toast.LENGTH_SHORT
            ).show()
            startNativeFlowMusicConnect()
        }
    }

    /**
     * Native, browser-free connect: Google pop-up -> ID token -> backend session.
     */
    private fun startNativeFlowMusicConnect() {
        val clientId = try {
            getString(R.string.default_web_client_id)
        } catch (e: Exception) {
            ""
        }
        if (clientId.isBlank()) {
            // No native client id configured -> use the browser fallback.
            startCustomTabFlowMusicConnect()
            return
        }

        val credentialManager = CredentialManager.create(requireContext())
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
                val result = credentialManager.getCredential(
                    request = request,
                    context = requireContext()
                )
                val credential = result.credential
                if (credential is CustomCredential &&
                    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data)
                    val idToken = googleIdToken.idToken
                    val email = googleIdToken.id
                    Log.i("AiHubFragment", "Native Google pop-up success for $email")

                    // Keep the PK-AI identity in sync with the same account.
                    try {
                        authRepository.loginWithGoogle(
                            idToken = idToken,
                            displayName = googleIdToken.displayName ?: "",
                            email = email,
                            photoUrl = googleIdToken.profilePictureUri?.toString() ?: ""
                        )
                    } catch (e: Exception) {
                        Log.w("AiHubFragment", "authRepository sync skipped: ${e.message}")
                    }

                    // Exchange the ID token for a real music-backend session.
                    val session = withContext(Dispatchers.IO) {
                        FlowMusicOAuth.exchangeIdTokenForSession(idToken)
                    }
                    if (session != null) {
                        injectSessionIntoEngine(session)
                    } else {
                        // Backend rejected the ID token -> browser fallback.
                        Log.w("AiHubFragment", "ID-token rejected; using Custom Tab fallback")
                        startCustomTabFlowMusicConnect()
                    }
                } else {
                    Log.w("AiHubFragment", "Unexpected credential type: ${credential.type}")
                    startCustomTabFlowMusicConnect()
                }
            } catch (e: androidx.credentials.exceptions.GetCredentialCancellationException) {
                Log.d("AiHubFragment", "Native connect cancelled by user")
            } catch (e: androidx.credentials.exceptions.NoCredentialException) {
                Log.w("AiHubFragment", "No Google account on device; using fallback")
                startCustomTabFlowMusicConnect()
            } catch (e: Exception) {
                Log.e("AiHubFragment", "Native connect failed; using fallback", e)
                startCustomTabFlowMusicConnect()
            }
        }
    }

    /**
     * Browser fallback: Chrome Custom Tab PKCE flow (used only if the native
     * ID-token exchange is unavailable or rejected by the backend).
     */
    private fun startCustomTabFlowMusicConnect() {
        activity?.runOnUiThread {
            viewLifecycleOwner.lifecycleScope.launch {
                val email = try {
                    authRepository.getSession().first().email?.takeIf { it.isNotBlank() }
                } catch (e: Exception) {
                    null
                }

                val verifier = FlowMusicOAuth.generateCodeVerifier()
                pendingCodeVerifier = verifier
                val challenge = FlowMusicOAuth.codeChallenge(verifier)
                val url = FlowMusicOAuth.buildAuthorizeUrl(challenge, email)

                val opened = openCustomTab(url)
                if (!opened) {
                    // Last resort: in-app WebView overlay.
                    signInModalAutoClosed = false
                    binding.containerFlowmusicSignup.visibility = View.VISIBLE
                    binding.webviewFlowmusicSignup.loadUrl(FLOW_MUSIC_URL)
                }
            }
        }
    }

    fun openFlowMusicSignUp() = connectFlowMusic()

    private fun openCustomTab(url: String): Boolean {
        return try {
            val intent = CustomTabsIntent.Builder()
                .setShowTitle(true)
                .build()
            intent.launchUrl(requireContext(), Uri.parse(url))
            true
        } catch (e: Exception) {
            Log.w("AiHubFragment", "Custom Tab launch failed: ${e.message}")
            false
        }
    }

    /** Handles the pkai://auth-callback redirect returned by the Custom Tab. */
    private fun handleFlowMusicCallback(uri: Uri) {
        val code = uri.getQueryParameter("code")
        val verifier = pendingCodeVerifier
        if (code.isNullOrBlank() || verifier.isNullOrBlank()) {
            Toast.makeText(
                requireContext(),
                "Ultra Chat AI connect nahi ho saka. Dobara try karein.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        activity?.runOnUiThread {
            Toast.makeText(
                requireContext(),
                "Ultra Chat AI account verify ho raha hai...",
                Toast.LENGTH_SHORT
            ).show()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val session = withContext(Dispatchers.IO) {
                FlowMusicOAuth.exchangeCodeForSession(code, verifier)
            }
            if (session == null) {
                activity?.runOnUiThread {
                    Toast.makeText(
                        requireContext(),
                        "Ultra Chat AI sign-in fail ho gaya. Dobara try karein.",
                        Toast.LENGTH_LONG
                    ).show()
                }
                return@launch
            }
            pendingCodeVerifier = null
            injectSessionIntoEngine(session)
        }
    }

    /** Persists the real session into the engine WebView and reloads it. */
    private fun injectSessionIntoEngine(session: JSONObject) {
        activity?.runOnUiThread {
            val quotedSession = JSONObject.quote(session.toString())
            val quotedKey = JSONObject.quote(FlowMusicOAuth.STORAGE_KEY)
            val js = """
                (function(){
                    try {
                        localStorage.setItem($quotedKey, $quotedSession);
                        return 'ok';
                    } catch(e) { return 'err:' + e; }
                })();
            """.trimIndent()
            _binding?.webviewFlowmusicBackend?.evaluateJavascript(js) { _ ->
                // Reload so the engine boots with the freshly injected session.
                _binding?.webviewFlowmusicBackend?.reload()
                lastStatusJson = ""
                signInModalAutoClosed = false
                closeFlowMusicSignUp()
                Toast.makeText(requireContext(), "Ultra Chat AI connected \u2713", Toast.LENGTH_SHORT).show()
                statusHandler.postDelayed({ probeFlowMusicSession() }, 3000)
            }
        }
    }

    fun closeFlowMusicSignUp() {
        activity?.runOnUiThread {
            binding.containerFlowmusicSignup.visibility = View.GONE
            CookieManager.getInstance().flush()
        }
    }

    private fun disconnectFlowMusic() {
        activity?.runOnUiThread {
            try {
                CookieManager.getInstance().removeAllCookies(null)
                CookieManager.getInstance().flush()
                _binding?.webviewFlowmusicBackend?.evaluateJavascript(
                    "try { localStorage.clear(); } catch(e) {}", null
                )
                _binding?.webviewFlowmusicBackend?.reload()
                lastStatusJson = ""
                dispatchStatusToJs("""{"signedIn":false,"hasStudio":false}""")
                Toast.makeText(requireContext(), "Ultra Chat AI disconnected", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.w("AiHubFragment", "disconnectFlowMusic: ${e.message}")
            }
        }
    }

    /** JS bridge exposed to the Flow Music WebView as window.FlowMusicNative. */
    private inner class FlowMusicNativeBridge {
        @JavascriptInterface
        fun onAutomationLog(msg: String?) {
            Log.d("FlowMusicAutomation", msg ?: "")
        }

        @JavascriptInterface
        fun onProgress(json: String?) {
            if (json.isNullOrBlank()) return
            Log.d("AiHubFragment", "Flow Music progress: $json")
            dispatchProgressToJs(json)
        }

        @JavascriptInterface
        fun onTrackResult(json: String?) {
            if (json.isNullOrBlank()) return
            Log.d("AiHubFragment", "Flow Music track result: $json")
            dispatchTrackResultToJs(json)
        }
    }

    // ==========================================================================
    // Ultra AI chat WebView
    // ==========================================================================

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val webView = binding.webviewUltraAi
        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(requireContext()))
            .build()

        webView.setBackgroundColor(0xFF020617.toInt())

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            allowFileAccessFromFileURLs = true
            allowUniversalAccessFromFileURLs = true
            mediaPlaybackRequiresUserGesture = false
            useWideViewPort = true
            loadWithOverviewMode = true
            cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                Log.d("AiHubFragment", "Ultra AI page finished: $url")

                val googleClientId = try {
                    getString(R.string.default_web_client_id)
                } catch (e: Exception) {
                    ""
                }
                val clientIdJs = JSONObject.quote(googleClientId)
                val js = """
                    window.GOOGLE_CLIENT_ID = $clientIdJs;
                    window.GOOGLE_REDIRECT_URI = "";
                    console.log("[AiHub] Injected GOOGLE_CLIENT_ID successfully");
                """.trimIndent()
                view?.evaluateJavascript(js, null)

                // Push the latest Flow Music status into the freshly loaded chat UI.
                if (lastStatusJson.isNotBlank()) {
                    dispatchStatusToJs(lastStatusJson)
                }
            }

            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                val url = request?.url ?: return super.shouldInterceptRequest(view, request)
                val intercepted = assetLoader.shouldInterceptRequest(url)
                if (intercepted != null) {
                    return intercepted
                }
                val urlString = url.toString()

                if (urlString.contains("flowmusic_track") || urlString.contains("soundhelix.com") || urlString.endsWith(".wav") || urlString.endsWith(".mp3")) {
                    try {
                        val stream: InputStream = requireContext().assets.open("ultra-ai-chat-space/assets/flowmusic_track.wav")
                        val mimeType = if (urlString.endsWith(".mp3")) "audio/mpeg" else "audio/wav"
                        val customResponse = WebResourceResponse(mimeType, "UTF-8", stream)
                        val headers = HashMap<String, String>()
                        headers["Access-Control-Allow-Origin"] = "*"
                        headers["Access-Control-Allow-Methods"] = "GET, POST, OPTIONS, HEAD"
                        headers["Access-Control-Allow-Headers"] = "*"
                        headers["Accept-Ranges"] = "bytes"
                        customResponse.responseHeaders = headers
                        return customResponse
                    } catch (e: Exception) {
                        Log.w("AiHubFragment", "Audio asset fallback error: " + e.message)
                    }
                }

                if (urlString.contains("ultra-ai-chat-space")) {
                    try {
                        val path = when {
                            urlString.contains("/android_asset/") -> {
                                urlString.substringAfter("/android_asset/")
                            }
                            url.path != null && url.path!!.contains("ultra-ai-chat-space") -> {
                                "ultra-ai-chat-space" + url.path!!.substringAfter("ultra-ai-chat-space")
                            }
                            else -> null
                        }

                        if (path != null) {
                            val cleanPath = path.substringBefore("?").substringBefore("#")
                            val mimeType = when {
                                cleanPath.endsWith(".js") -> "application/javascript"
                                cleanPath.endsWith(".css") -> "text/css"
                                cleanPath.endsWith(".html") -> "text/html"
                                cleanPath.endsWith(".svg") -> "image/svg+xml"
                                cleanPath.endsWith(".png") -> "image/png"
                                cleanPath.endsWith(".json") -> "application/json"
                                else -> "application/octet-stream"
                            }
                            val stream: InputStream = requireContext().assets.open(cleanPath)
                            val response = WebResourceResponse(mimeType, "UTF-8", stream)
                            val headers = HashMap<String, String>()
                            headers["Access-Control-Allow-Origin"] = "*"
                            headers["Access-Control-Allow-Methods"] = "GET, POST, OPTIONS"
                            headers["Access-Control-Allow-Headers"] = "*"
                            response.responseHeaders = headers
                            return response
                        }
                    } catch (e: Exception) {
                        Log.w("AiHubFragment", "Asset intercept notice: $urlString -> ${e.message}")
                    }
                }
                return super.shouldInterceptRequest(view, request)
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val url = request?.url?.toString() ?: return false
                if (url.startsWith("https://appassets.androidplatform.net")) {
                    return false
                }
                return false
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                Log.e("AiHubFragment", "WebView error: ${error?.description} on ${request?.url}")
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest?) {
                request?.grant(request.resources)
            }

            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                Log.d("AiHubJS", "[${consoleMessage?.messageLevel()}] ${consoleMessage?.message()} (line ${consoleMessage?.lineNumber()} of ${consoleMessage?.sourceId()})")
                return true
            }
        }

        // Bridge exposed to the Ultra AI chat WebView as window.AndroidOAuth
        webView.addJavascriptInterface(
            object {
                @JavascriptInterface
                fun startGoogleSignIn() {
                    Log.d("AiHubFragment", "startGoogleSignIn called from JS")
                    activity?.runOnUiThread { triggerGoogleSignIn() }
                }

                @JavascriptInterface
                fun startGoogleSignIn(clientId: String?, redirectUri: String?) {
                    Log.d("AiHubFragment", "startGoogleSignIn(clientId, redirectUri) called from JS")
                    activity?.runOnUiThread { triggerGoogleSignIn() }
                }

                /** Open the real Flow Music sign-in WebView (user's own Google account). */
                @JavascriptInterface
                fun connectFlowMusic() {
                    Log.d("AiHubFragment", "connectFlowMusic called from JS")
                    this@AiHubFragment.connectFlowMusic()
                }

                @JavascriptInterface
                fun openFlowMusicSignUp() {
                    Log.d("AiHubFragment", "openFlowMusicSignUp called from JS")
                    this@AiHubFragment.connectFlowMusic()
                }

                @JavascriptInterface
                fun openFlowMusicStudio() {
                    Log.d("AiHubFragment", "openFlowMusicStudio called from JS")
                    this@AiHubFragment.connectFlowMusic()
                }

                /** Returns the cached Flow Music session status as a JSON string. */
                @JavascriptInterface
                fun getFlowMusicStatus(): String {
                    return lastStatusJson.ifBlank { """{"signedIn":false,"hasStudio":false}""" }
                }

                /** Drive real music generation inside the Flow Music session. */
                @JavascriptInterface
                fun generateFlowMusicTrack(prompt: String?) {
                    Log.d("AiHubFragment", "generateFlowMusicTrack called from JS: $prompt")
                    if (prompt.isNullOrBlank()) {
                        dispatchTrackResultToJs("""{"ok":false,"error":"Empty music prompt."}""")
                        return
                    }
                    activity?.runOnUiThread { startFlowMusicGeneration(prompt) }
                }

                @JavascriptInterface
                fun disconnectFlowMusic() {
                    Log.d("AiHubFragment", "disconnectFlowMusic called from JS")
                    this@AiHubFragment.disconnectFlowMusic()
                }

                @JavascriptInterface
                fun playFlowMusicInBackend(trackUrl: String?) {
                    Log.d("AiHubFragment", "playFlowMusicInBackend: $trackUrl")
                    if (trackUrl.isNullOrBlank()) return
                    activity?.runOnUiThread {
                        val escapedUrl = JSONObject.quote(trackUrl)
                        binding.webviewFlowmusicBackend.evaluateJavascript(
                            "if (window.playTrack) { window.playTrack($escapedUrl); } else { new Audio($escapedUrl).play(); }",
                            null
                        )
                    }
                }

                @JavascriptInterface
                fun triggerFlowMusicAction(actionJson: String?) {
                    Log.d("AiHubFragment", "triggerFlowMusicAction: $actionJson")
                }

                @JavascriptInterface
                fun exitToHome() {
                    Log.d("AiHubFragment", "exitToHome called from JS")
                    activity?.runOnUiThread { findNavController().navigateUp() }
                }

                @JavascriptInterface
                fun downloadFile(fileUrl: String?, fileName: String?, mimeType: String?) {
                    Log.d("AiHubFragment", "downloadFile called from JS: $fileUrl, $fileName, $mimeType")
                    if (fileUrl.isNullOrBlank()) return
                    activity?.runOnUiThread {
                        downloadMediaToDevice(
                            fileUrl,
                            fileName ?: "ultra_ai_${System.currentTimeMillis()}",
                            mimeType ?: "*/*"
                        )
                    }
                }
            },
            "AndroidOAuth"
        )

        webView.setDownloadListener { url, _, _, mimetype, _ ->
            downloadMediaToDevice(url, "ultra_ai_${System.currentTimeMillis()}", mimetype ?: "*/*")
        }

        webView.loadUrl("https://appassets.androidplatform.net/assets/ultra-ai-chat-space/index.html")
    }

    // ==========================================================================
    // Native Google Sign-In (app-level identity, unchanged behaviour)
    // ==========================================================================

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
                        dispatchAuthErrorToJs("Unsupported credential type: ${credential.type}")
                    }
                } catch (e: androidx.credentials.exceptions.GetCredentialCancellationException) {
                    dispatchAuthErrorToJs("Sign-In cancelled.")
                } catch (e: androidx.credentials.exceptions.NoCredentialException) {
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
                    window.dispatchEvent(new CustomEvent('pkai:auth_success', { detail: { token: token, user: user } }));
                })();
            """.trimIndent()
            _binding?.webviewUltraAi?.evaluateJavascript(js, null)
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
                    window.dispatchEvent(new CustomEvent('pkai:auth_error', { detail: { error: err } }));
                })();
            """.trimIndent()
            _binding?.webviewUltraAi?.evaluateJavascript(js, null)
        }
    }

    // ==========================================================================
    // Lifecycle
    // ==========================================================================

    override fun onPause() {
        super.onPause()
        // Persist the Flow Music session cookies to disk.
        CookieManager.getInstance().flush()
    }

    override fun onDestroyView() {
        statusHandler.removeCallbacksAndMessages(null)
        CookieManager.getInstance().flush()
        FlowMusicOAuth.unregister()
        _binding?.webviewFlowmusicBackend?.removeJavascriptInterface("FlowMusicNative")
        super.onDestroyView()
        _binding = null
    }

    // ==========================================================================
    // Helpers
    // ==========================================================================

    private fun decodeJsString(raw: String?): String? {
        if (raw == null || raw == "null") return null
        return try {
            val v = JSONTokener(raw).nextValue()
            v?.toString()
        } catch (e: Exception) {
            raw.trim('"')
        }
    }

    private fun downloadMediaToDevice(url: String, fileName: String, mimeType: String) {
        try {
            val uri = Uri.parse(url)
            val request = DownloadManager.Request(uri).apply {
                setTitle(fileName)
                setDescription("Downloading from Ultra AI 4...")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
                if (mimeType.isNotBlank() && mimeType != "*/*") {
                    setMimeType(mimeType)
                }
            }
            val downloadManager = requireContext().getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            downloadManager?.enqueue(request)
            Toast.makeText(requireContext(), "Downloading $fileName to storage...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("AiHubFragment", "Failed to start DownloadManager: ${e.message}", e)
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                startActivity(intent)
            } catch (err: Exception) {
                Toast.makeText(requireContext(), "Download error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        private const val FLOW_MUSIC_URL = "https://www.flowmusic.app"
        private const val CHROME_MOBILE_UA =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    }
}
